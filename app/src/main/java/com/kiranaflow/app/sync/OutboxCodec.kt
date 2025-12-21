package com.kiranaflow.app.sync

import com.kiranaflow.app.data.local.OutboxEntity
import org.json.JSONObject

/**
 * Encodes/decodes outbox entries to typed ops + wire envelopes.
 *
 * This keeps the rest of the app from having to parse JSON strings manually.
 */
object OutboxCodec {
    fun decode(entry: OutboxEntity): PendingSyncOp {
        val entityType = SyncEntityType.valueOf(entry.entityType)
        val op = SyncOpType.valueOf(entry.op)
        val body = entry.payloadJson?.trim()?.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
        return PendingSyncOp(
            entityType = entityType,
            entityId = entry.entityId,
            op = op,
            payload = body
        )
    }

    fun toEnvelope(entry: OutboxEntity, deviceId: String, sentAtMillis: Long = System.currentTimeMillis()): SyncEnvelope {
        val body = entry.payloadJson?.trim()?.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
        return SyncEnvelope(
            apiVersion = 1,
            deviceId = deviceId,
            opId = entry.opId,
            sentAtMillis = sentAtMillis,
            entityType = entry.entityType,
            entityId = entry.entityId,
            op = entry.op,
            body = body
        )
    }
}



