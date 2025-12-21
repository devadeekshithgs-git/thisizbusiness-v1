package com.kiranaflow.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.kiranaflow.app.BuildConfig
import com.kiranaflow.app.data.local.AppPrefsStore
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.data.local.OutboxDao
import com.kiranaflow.app.data.local.OutboxEntity
import com.kiranaflow.app.sync.HttpRemoteApi
import com.kiranaflow.app.sync.OutboxCodec
import com.kiranaflow.app.sync.OutboxDispatcher
import com.kiranaflow.app.sync.PendingSyncOp
import com.kiranaflow.app.sync.RemoteApi
import com.kiranaflow.app.sync.RemoteRequestPreview
import com.kiranaflow.app.sync.SyncEnvelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface SyncEngine {
    suspend fun syncOnce(): SyncResult
    suspend fun syncAllPending(): SyncResult
    fun triggerImmediateSync()
}

data class SyncResult(
    val attempted: Int,
    val succeeded: Int,
    val failed: Int,
    val message: String
) {
    val isFullySuccess: Boolean get() = failed == 0 && attempted > 0
    val hasPending: Boolean get() = failed > 0 || (attempted == 0 && message != "Nothing to sync")
}

/**
 * Sync engine that ensures data is always synced to Supabase immediately.
 * - Triggers sync immediately after data changes
 * - Auto-retries on connectivity restoration
 * - Supports manual reconnect via tap on sync indicator
 */
class StubSyncEngine(
    private val db: KiranaDatabase,
    private val appPrefsStore: AppPrefsStore,
    private val context: Context? = null
) : SyncEngine {

    private val outboxDao: OutboxDao = db.outboxDao()
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    
    // Retry configuration for immediate sync
    private val maxRetries = 5
    private val initialRetryDelayMs = 200L  // Faster initial retry
    private val maxRetryDelayMs = 4000L     // Lower max delay

    override suspend fun syncOnce(): SyncResult {
        return syncPendingOnly()
    }

    /**
     * Sync ALL pending items (both PENDING and FAILED), with retry logic.
     * This is the aggressive sync mode - ensures nothing remains pending.
     */
    override suspend fun syncAllPending(): SyncResult = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            var totalAttempted = 0
            var totalSucceeded = 0
            var totalFailed = 0
            var lastMessage = "Nothing to sync"
            
            // First sync PENDING items
            val pendingResult = syncPendingOnlyInternal()
            totalAttempted += pendingResult.attempted
            totalSucceeded += pendingResult.succeeded
            totalFailed += pendingResult.failed
            if (pendingResult.attempted > 0) lastMessage = pendingResult.message
            
            // Then retry FAILED items
            val failedResult = syncFailedOnlyInternal()
            totalAttempted += failedResult.attempted
            totalSucceeded += failedResult.succeeded
            totalFailed += failedResult.failed
            if (failedResult.attempted > 0) lastMessage = failedResult.message
            
            SyncResult(
                attempted = totalAttempted,
                succeeded = totalSucceeded,
                failed = totalFailed,
                message = lastMessage
            )
        }
    }

    /**
     * Trigger immediate sync in a fire-and-forget manner.
     * Called after every data operation to ensure data is synced ASAP.
     */
    override fun triggerImmediateSync() {
        syncScope.launch {
            syncWithRetry()
        }
    }

    /**
     * Sync with exponential backoff retry logic.
     * Keeps retrying until all items are synced or max retries exhausted.
     */
    private suspend fun syncWithRetry(): SyncResult {
        var delayMs = initialRetryDelayMs
        var lastResult: SyncResult? = null
        
        repeat(maxRetries) { attempt ->
            // Check connectivity before attempting
            if (!isNetworkAvailable()) {
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(maxRetryDelayMs)
                return@repeat
            }
            
            val result = syncAllPending()
            lastResult = result
            
            // If no pending items remain, we're done
            if (result.failed == 0) {
                return result
            }
            
            // Wait before retry
            delay(delayMs)
            delayMs = (delayMs * 2).coerceAtMost(maxRetryDelayMs)
        }
        
        return lastResult ?: SyncResult(0, 0, 0, "Sync not attempted")
    }

    private fun isNetworkAvailable(): Boolean {
        val ctx = context ?: return true // Assume online if no context
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            true // Assume online on error
        }
    }

    suspend fun syncFailedOnly(): SyncResult = withContext(Dispatchers.IO) {
        val entries = outboxDao.getFailed(limit = 100)  // Increased from 50
        syncEntries(entries)
    }

    private suspend fun syncFailedOnlyInternal(): SyncResult = withContext(Dispatchers.IO) {
        val entries = outboxDao.getFailed(limit = 200)  // Increased from 100
        syncEntries(entries)
    }

    suspend fun syncPendingOnly(): SyncResult = withContext(Dispatchers.IO) {
        val entries = outboxDao.getPendingOnly(limit = 100)  // Increased from 50
        syncEntries(entries)
    }

    private suspend fun syncPendingOnlyInternal(): SyncResult = withContext(Dispatchers.IO) {
        val entries = outboxDao.getPendingOnly(limit = 200)  // Increased from 100
        syncEntries(entries)
    }

    suspend fun resetFailedToPending(): Unit = withContext(Dispatchers.IO) {
        outboxDao.resetAllFailedToPending()
    }

    suspend fun retryEntry(entryId: Int): SyncResult = withContext(Dispatchers.IO) {
        val entry = outboxDao.getById(entryId) ?: return@withContext SyncResult(
            attempted = 0,
            succeeded = 0,
            failed = 0,
            message = "Entry not found"
        )
        syncEntries(listOf(entry))
    }

    suspend fun resetEntryFailedToPending(entryId: Int): Unit = withContext(Dispatchers.IO) {
        outboxDao.resetFailedToPending(entryId)
    }

    private suspend fun syncEntries(entries: List<OutboxEntity>): SyncResult {
        if (entries.isEmpty()) {
            return SyncResult(0, 0, 0, "Nothing to sync")
        }

        val backendConfigured = BuildConfig.BACKEND_BASE_URL.isNotBlank()

        // Use singleton HttpRemoteApi for connection pooling
        val httpRemote: HttpRemoteApi? = if (backendConfigured) {
            getOrCreateHttpRemote()
        } else null

        val remote: RemoteApi? = when {
            httpRemote != null -> httpRemote
            else -> null
        }

        val deviceId = appPrefsStore.getOrCreateDeviceId()

        // If no remote is configured, mark attempts and return
        if (remote == null) {
            val now = System.currentTimeMillis()
            entries.forEach { entry ->
                outboxDao.markAttempt(entry.id, now, null)
            }
            return SyncResult(entries.size, 0, 0, "Supabase backend not configured")
        }

        // Prepare all entries for sync
        val now = System.currentTimeMillis()
        val validEntries = mutableListOf<Triple<OutboxEntity, SyncEnvelope, RemoteRequestPreview>>()
        var invalidCount = 0

        for (entry in entries) {
            outboxDao.markAttempt(entry.id, now, null)
            
            val op: PendingSyncOp = try {
                OutboxCodec.decode(entry)
            } catch (e: Exception) {
                outboxDao.markFailed(entry.id, now, "Invalid outbox payload")
                invalidCount++
                continue
            }
            
            val preview = OutboxDispatcher.preview(op)
            val envelope = OutboxCodec.toEnvelope(entry, deviceId, sentAtMillis = now)
            validEntries.add(Triple(entry, envelope, preview))
        }

        // Use batch sync for HttpRemoteApi (much faster)
        val (ok, failed) = if (httpRemote != null && validEntries.isNotEmpty()) {
            syncEntriesBatch(httpRemote, validEntries, now)
        } else {
            // Fallback to sequential sync (should rarely happen; kept for safety)
            syncEntriesSequential(remote, validEntries, now)
        }

        val totalFailed = failed + invalidCount
        val msg = when {
            totalFailed == 0 -> "Synced $ok"
            else -> "Synced $ok, failed $totalFailed"
        }

        return SyncResult(
            attempted = entries.size,
            succeeded = ok,
            failed = totalFailed,
            message = msg
        )
    }
    
    /**
     * Batch sync using HttpRemoteApi - sends multiple ops in parallel/batch.
     * Returns (successCount, failCount)
     */
    private suspend fun syncEntriesBatch(
        httpRemote: HttpRemoteApi,
        validEntries: List<Triple<OutboxEntity, SyncEnvelope, RemoteRequestPreview>>,
        timestamp: Long
    ): Pair<Int, Int> {
        val envelopesWithPreviews = validEntries.map { (_, envelope, preview) ->
            envelope to preview
        }
        
        // Use batch apply - handles batching and parallelization internally
        val results = httpRemote.applyBatch(envelopesWithPreviews)
        
        var ok = 0
        var failed = 0
        
        results.forEachIndexed { index, result ->
            val entry = validEntries[index].first
            if (result.ok) {
                outboxDao.markDone(entry.id, timestamp)
                ok++
            } else {
                outboxDao.markFailed(entry.id, timestamp, result.message.ifBlank { "Sync failed" })
                failed++
            }
        }
        
        return ok to failed
    }
    
    /**
     * Sequential sync fallback.
     * Returns (successCount, failCount)
     */
    private suspend fun syncEntriesSequential(
        remote: RemoteApi,
        validEntries: List<Triple<OutboxEntity, SyncEnvelope, RemoteRequestPreview>>,
        timestamp: Long
    ): Pair<Int, Int> {
        var ok = 0
        var failed = 0
        
        for ((entry, envelope, preview) in validEntries) {
            val result = remote.apply(envelope, preview)
            if (result.ok) {
                outboxDao.markDone(entry.id, timestamp)
                ok++
            } else {
                outboxDao.markFailed(entry.id, timestamp, result.message.ifBlank { "Sync failed" })
                failed++
            }
        }
        
        return ok to failed
    }
    
    // Singleton HttpRemoteApi for connection reuse
    private var cachedHttpRemote: HttpRemoteApi? = null
    
    private fun getOrCreateHttpRemote(): HttpRemoteApi {
        return cachedHttpRemote ?: HttpRemoteApi(
            baseUrl = BuildConfig.BACKEND_BASE_URL,
            apiKey = BuildConfig.BACKEND_API_KEY.trim().ifBlank { null }
        ).also { cachedHttpRemote = it }
    }
}

