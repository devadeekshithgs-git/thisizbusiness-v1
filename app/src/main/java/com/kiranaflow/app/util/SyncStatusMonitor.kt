package com.kiranaflow.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Milestone D groundwork (stub): a local-only sync status tracker.
 *
 * For now:
 * - pendingCount is always 0 unless a future sync-queue writes to it
 * - lastSyncAtMillis is updated when [markSynced] is called
 *
 * Later this will be driven by:
 * - a local "outbox" table for pending operations (offline-first)
 * - a real sync engine (e.g. Supabase) with conflict resolution
 */
class SyncStatusMonitor {
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private val _lastSyncAtMillis = MutableStateFlow<Long?>(null)
    val lastSyncAtMillis: StateFlow<Long?> = _lastSyncAtMillis.asStateFlow()

    fun setPendingCount(count: Int) {
        _pendingCount.value = count.coerceAtLeast(0)
    }

    fun markSynced() {
        _pendingCount.value = 0
        _lastSyncAtMillis.value = System.currentTimeMillis()
    }
}









