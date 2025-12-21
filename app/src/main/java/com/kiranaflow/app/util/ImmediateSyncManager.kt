package com.kiranaflow.app.util

import android.content.Context
import com.kiranaflow.app.data.local.AppPrefsStore
import com.kiranaflow.app.data.local.KiranaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Singleton manager that ensures data is immediately synced after every operation.
 * This guarantees data is never left in a "pending" state when there's connectivity.
 * 
 * OPTIMIZATIONS:
 * - Debouncing: Multiple rapid triggers are coalesced into one sync
 * - Batching: Waits briefly to collect multiple ops before syncing
 */
object ImmediateSyncManager {
    private var syncEngine: SyncEngine? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Debounce configuration
    private const val DEBOUNCE_MS = 100L  // Wait 100ms to batch multiple ops
    private var pendingJob: Job? = null
    private val pendingCount = AtomicInteger(0)
    
    /**
     * Initialize with context. Should be called once when app starts.
     */
    fun init(context: Context) {
        if (syncEngine == null) {
            val db = KiranaDatabase.getDatabase(context)
            val prefs = AppPrefsStore(context)
            syncEngine = StubSyncEngine(db, prefs, context)
        }
    }
    
    /**
     * Trigger immediate sync. Called after every data operation.
     * Fire-and-forget - does not block the caller.
     * 
     * Uses debouncing to batch multiple rapid operations:
     * - If multiple triggerSync() calls happen within 100ms, only one sync runs
     * - This is much faster when creating sales with multiple items
     */
    fun triggerSync() {
        pendingCount.incrementAndGet()
        
        // Cancel any pending debounced sync
        pendingJob?.cancel()
        
        // Start new debounced sync
        pendingJob = scope.launch {
            delay(DEBOUNCE_MS)
            
            // Reset counter and sync
            val count = pendingCount.getAndSet(0)
            if (count > 0) {
                syncEngine?.triggerImmediateSync()
            }
        }
    }
    
    /**
     * Trigger sync immediately without debouncing.
     * Use when you need guaranteed immediate sync (e.g., app going to background).
     */
    fun triggerSyncNow() {
        pendingJob?.cancel()
        pendingCount.set(0)
        scope.launch {
            syncEngine?.triggerImmediateSync()
        }
    }
    
    /**
     * Force sync all pending items. Returns when complete.
     */
    suspend fun syncAllNow(): SyncResult {
        // Cancel debounced sync since we're doing a full sync
        pendingJob?.cancel()
        pendingCount.set(0)
        
        return syncEngine?.syncAllPending() 
            ?: SyncResult(0, 0, 0, "Sync engine not initialized")
    }
}

