package com.kiranaflow.app.sync

data class RemoteResult(
    val ok: Boolean,
    val message: String
)

/**
 * A tiny abstraction for "where sync goes".
 * For now we only have a fake in-memory implementation (no creds required).
 */
interface RemoteApi {
    suspend fun apply(envelope: SyncEnvelope, request: RemoteRequestPreview): RemoteResult
}







