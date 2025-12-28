package com.kiranaflow.app.sync

import org.json.JSONObject

data class RemoteRequestPreview(
    val method: String,
    val path: String,
    val body: JSONObject?
) {
    fun toOneLine(maxChars: Int = 900): String {
        val bodyStr = body?.toString() ?: "null"
        val raw = "Would $method $path body=$bodyStr"
        return if (raw.length <= maxChars) raw else raw.take(maxChars) + "…"
    }
}

/**
 * Turns typed outbox ops into a deterministic "remote request preview".
 * No network is performed here.
 */
object OutboxDispatcher {
    fun preview(op: PendingSyncOp): RemoteRequestPreview {
        val base = "/v1"
        val body = OutboxCanonicalizer.canonicalBody(op)
        return when (op.entityType) {
            SyncEntityType.ITEM -> when (op.op) {
                SyncOpType.UPSERT ->
                    RemoteRequestPreview("PUT", "$base/items/${op.entityId}", body)
                SyncOpType.DELETE ->
                    RemoteRequestPreview("DELETE", "$base/items/${op.entityId ?: op.payload?.opt("id")}", null)
                else ->
                    RemoteRequestPreview("POST", "$base/items/_unsupported_${op.op.name.lowercase()}", body)
            }

            SyncEntityType.PARTY -> when (op.op) {
                SyncOpType.UPSERT ->
                    RemoteRequestPreview("PUT", "$base/parties/${op.entityId}", body)
                SyncOpType.DELETE ->
                    RemoteRequestPreview("DELETE", "$base/parties/${op.entityId ?: op.payload?.opt("id")}", null)
                SyncOpType.UPSERT_CUSTOMER ->
                    RemoteRequestPreview("POST", "$base/customers", body)
                SyncOpType.UPSERT_VENDOR ->
                    RemoteRequestPreview("POST", "$base/vendors", body)
                else ->
                    RemoteRequestPreview("POST", "$base/parties/_unsupported_${op.op.name.lowercase()}", body)
            }

            SyncEntityType.TRANSACTION -> when (op.op) {
                SyncOpType.DELETE ->
                    RemoteRequestPreview("DELETE", "$base/transactions/${op.entityId ?: op.payload?.opt("id")}", null)
                SyncOpType.CREATE_SALE ->
                    RemoteRequestPreview("POST", "$base/transactions/sale", body)
                SyncOpType.CREATE_PAYMENT ->
                    RemoteRequestPreview("POST", "$base/transactions/payment", body)
                SyncOpType.CREATE_VENDOR_PURCHASE ->
                    RemoteRequestPreview("POST", "$base/transactions/vendor_purchase", body)
                SyncOpType.CREATE_EXPENSE ->
                    RemoteRequestPreview("POST", "$base/transactions/expense", body)
                SyncOpType.UPSERT ->
                    RemoteRequestPreview("PUT", "$base/transactions/${op.entityId}", body)
                SyncOpType.EDIT_TRANSACTION ->
                    RemoteRequestPreview("PUT", "$base/transactions/${op.entityId}", body)
                SyncOpType.FINALIZE_TRANSACTION ->
                    RemoteRequestPreview("POST", "$base/transactions/${op.entityId}/finalize", body)
                SyncOpType.VOID_TRANSACTION ->
                    RemoteRequestPreview("POST", "$base/transactions/${op.entityId}/void", body)
                SyncOpType.CREATE_ADJUSTMENT ->
                    RemoteRequestPreview("POST", "$base/transactions/${op.entityId}/adjustments", body)
                else ->
                    RemoteRequestPreview("POST", "$base/transactions/_unsupported_${op.op.name.lowercase()}", body)
            }

            SyncEntityType.TRANSACTION_ITEM -> when (op.op) {
                SyncOpType.UPSERT_MANY ->
                    RemoteRequestPreview("POST", "$base/transactions/${op.entityId}/items", body)
                else ->
                    RemoteRequestPreview("POST", "$base/transaction_items/_unsupported_${op.op.name.lowercase()}", body)
            }

            SyncEntityType.REMINDER -> when (op.op) {
                SyncOpType.UPSERT ->
                    RemoteRequestPreview("POST", "$base/reminders", body)
                SyncOpType.DELETE ->
                    RemoteRequestPreview("DELETE", "$base/reminders/${op.entityId ?: op.payload?.opt("id")}", null)
                SyncOpType.MARK_DONE ->
                    RemoteRequestPreview("POST", "$base/reminders/${op.entityId ?: op.payload?.opt("id")}/done", null)
                else ->
                    RemoteRequestPreview("POST", "$base/reminders/_unsupported_${op.op.name.lowercase()}", body)
            }
        }
    }

    fun envelope(
        op: PendingSyncOp,
        opId: String,
        deviceId: String,
        sentAtMillis: Long = System.currentTimeMillis()
    ): SyncEnvelope {
        return SyncEnvelope(
            apiVersion = 1,
            deviceId = deviceId,
            opId = opId,
            sentAtMillis = sentAtMillis,
            entityType = op.entityType.name,
            entityId = op.entityId,
            op = op.op.name,
            body = OutboxCanonicalizer.canonicalBody(op)
        )
    }
}


