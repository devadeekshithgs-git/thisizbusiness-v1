package com.kiranaflow.app.util

import com.kiranaflow.app.BuildConfig
import com.kiranaflow.app.data.local.AppPrefsStore
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.data.local.OutboxDao
import com.kiranaflow.app.data.local.OutboxEntity
import com.kiranaflow.app.sync.FakeRemoteApi
import com.kiranaflow.app.sync.HttpRemoteApi
import com.kiranaflow.app.sync.OutboxCodec
import com.kiranaflow.app.sync.OutboxDispatcher
import com.kiranaflow.app.sync.PendingSyncOp
import com.kiranaflow.app.sync.RemoteApi
import com.kiranaflow.app.sync.SyncEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

interface SyncEngine {
    suspend fun syncOnce(): SyncResult
}

data class SyncResult(
    val attempted: Int,
    val succeeded: Int,
    val failed: Int,
    val message: String
)

/**
 * Milestone D groundwork: a "stub" sync engine that can either:
 * - simulate success via [FakeRemoteApi]
 * - call real backend via [HttpRemoteApi] when configured
 * - or do nothing (but record attempts) when backend isn't configured
 */
class StubSyncEngine(
    private val db: KiranaDatabase,
    private val appPrefsStore: AppPrefsStore
) : SyncEngine {

    private val outboxDao: OutboxDao = db.outboxDao()

    companion object {
        private val sharedRemote = FakeRemoteApi()
    }

    override suspend fun syncOnce(): SyncResult {
        val prefs = appPrefsStore.prefs.first()
        return syncPendingOnly(simulateSuccess = prefs.devSimulateSyncSuccess)
    }

    suspend fun syncOnce(simulateSuccess: Boolean): SyncResult =
        syncPendingOnly(simulateSuccess = simulateSuccess)

    suspend fun syncFailedOnly(simulateSuccess: Boolean): SyncResult = withContext(Dispatchers.IO) {
        val entries = outboxDao.getFailed(limit = 50)
        syncEntries(entries, simulateSuccess)
    }

    suspend fun syncPendingOnly(simulateSuccess: Boolean): SyncResult = withContext(Dispatchers.IO) {
        val entries = outboxDao.getPendingOnly(limit = 50)
        syncEntries(entries, simulateSuccess)
    }

    suspend fun resetFailedToPending(): Unit = withContext(Dispatchers.IO) {
        outboxDao.resetAllFailedToPending()
    }

    suspend fun retryEntry(entryId: Int, simulateSuccess: Boolean): SyncResult = withContext(Dispatchers.IO) {
        val entry = outboxDao.getById(entryId) ?: return@withContext SyncResult(
            attempted = 0,
            succeeded = 0,
            failed = 0,
            message = "Entry not found"
        )
        syncEntries(listOf(entry), simulateSuccess)
    }

    suspend fun resetEntryFailedToPending(entryId: Int): Unit = withContext(Dispatchers.IO) {
        outboxDao.resetFailedToPending(entryId)
    }

    private suspend fun syncEntries(entries: List<OutboxEntity>, simulateSuccess: Boolean): SyncResult {
        if (entries.isEmpty()) {
            return SyncResult(0, 0, 0, "Nothing to sync")
        }

        val prefs = appPrefsStore.prefs.first()
        val useRealBackend = prefs.useRealBackend
        val backendConfigured = BuildConfig.BACKEND_BASE_URL.isNotBlank()

        val remote: RemoteApi? = when {
            useRealBackend && backendConfigured ->
                HttpRemoteApi(
                    baseUrl = BuildConfig.BACKEND_BASE_URL,
                    apiKey = BuildConfig.BACKEND_API_KEY.trim().ifBlank { null }
                )
            simulateSuccess -> sharedRemote
            else -> null
        }

        val deviceId = appPrefsStore.getOrCreateDeviceId()

        var ok = 0
        var failed = 0

        for (entry in entries) {
            // Always record that we tried to sync this entry.
            val now = System.currentTimeMillis()
            outboxDao.markAttempt(entry.id, now, null)

            val op: PendingSyncOp = try {
                OutboxCodec.decode(entry)
            } catch (e: Exception) {
                outboxDao.markFailed(entry.id, now, "Invalid outbox payload")
                failed++
                continue
            }

            // If no remote is configured, leave as PENDING/FAILED (whichever it already is).
            if (remote == null) continue

            val preview = OutboxDispatcher.preview(op)
            val envelope: SyncEnvelope = OutboxCodec.toEnvelope(entry, deviceId, sentAtMillis = now)
            val result = remote.apply(envelope, preview)

            if (result.ok) {
                outboxDao.markDone(entry.id, now)
                ok++
            } else {
                outboxDao.markFailed(entry.id, now, result.message.ifBlank { "Sync failed" })
                failed++
            }
        }

        val msg = when {
            remote == null && useRealBackend && !backendConfigured ->
                "Backend URL not configured"
            remote == null && !useRealBackend ->
                "Cloud Sync is off"
            remote == null ->
                "Sync skipped"
            failed == 0 ->
                "Synced $ok"
            else ->
                "Synced $ok, failed $failed"
        }

        return SyncResult(
            attempted = entries.size,
            succeeded = ok,
            failed = failed,
            message = msg
        )
    }
}

