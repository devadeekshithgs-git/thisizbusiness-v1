package com.kiranaflow.app.sync

import com.kiranaflow.app.data.local.OutboxDao
import com.kiranaflow.app.data.local.OutboxEntity
import org.json.JSONObject

/**
 * Minimal typed sync op model + queue.
 *
 * This is used by the local outbox + the stub sync engine to build SyncEnvelopes.
 */
enum class SyncEntityType {
    ITEM,
    PARTY,
    TRANSACTION,
    TRANSACTION_ITEM,
    REMINDER
}

enum class SyncOpType {
    UPSERT,
    DELETE,
    UPSERT_MANY,
    MARK_DONE,
    CREATE_SALE,
    CREATE_PAYMENT,
    CREATE_VENDOR_PURCHASE,
    CREATE_EXPENSE,
    UPSERT_CUSTOMER,
    UPSERT_VENDOR
}

data class PendingSyncOp(
    val entityType: SyncEntityType,
    val entityId: String? = null,
    val op: SyncOpType,
    val payload: JSONObject? = null
)

class PendingSyncQueue(
    private val outboxDao: OutboxDao
) {
    suspend fun enqueue(op: PendingSyncOp) {
        outboxDao.insert(
            OutboxEntity(
                entityType = op.entityType.name,
                entityId = op.entityId,
                op = op.op.name,
                payloadJson = op.payload?.toString()
            )
        )
    }
}



