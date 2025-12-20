package com.kiranaflow.app.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * Canonicalizes typed outbox ops into stable JSON DTOs.
 *
 * Goals:
 * - Deterministic keys & shapes (future backend contract)
 * - Coerce basic types (Int/Double/String/Boolean)
 * - Strip nulls to keep payload clean
 * - Add local IDs where appropriate (so backend can de-dupe / map)
 */
object OutboxCanonicalizer {
    fun canonicalBody(op: PendingSyncOp): JSONObject? {
        val p = op.payload
        return when (op.entityType) {
            SyncEntityType.ITEM -> when (op.op) {
                SyncOpType.UPSERT -> json(
                    "id" to (op.entityId?.toIntOrNull() ?: p?.optIntOrNull("id")),
                    "name" to p?.optString("name"),
                    "category" to p?.optString("category"),
                    "price" to p?.optDoubleOrNull("price"),
                    "costPrice" to p?.optDoubleOrNull("costPrice"),
                    "stock" to p?.optIntOrNull("stock"),
                    "gstPercentage" to p?.optDoubleOrNull("gstPercentage"),
                    "reorderPoint" to (p?.optIntOrNull("reorderPoint") ?: 10),
                    "vendorId" to p?.optIntOrNull("vendorId"),
                    "rackLocation" to p?.optStringOrNull("rackLocation"),
                    "barcode" to p?.optStringOrNull("barcode"),
                    "imageUri" to p?.optStringOrNull("imageUri"),
                    "expiryDateMillis" to p?.optLongOrNull("expiryDateMillis")
                )
                else -> p?.stripNulls()
            }

            SyncEntityType.PARTY -> when (op.op) {
                SyncOpType.UPSERT -> json(
                    "id" to (op.entityId?.toIntOrNull() ?: p?.optIntOrNull("id")),
                    "type" to p?.optString("type"),
                    "name" to p?.optString("name"),
                    "phone" to p?.optString("phone"),
                    "gstNumber" to p?.optStringOrNull("gstNumber"),
                    "balance" to p?.optDoubleOrNull("balance")
                )
                SyncOpType.UPSERT_CUSTOMER -> json(
                    "id" to (op.entityId?.toIntOrNull() ?: p?.optIntOrNull("id")),
                    "type" to "CUSTOMER",
                    "name" to p?.optString("name"),
                    "phone" to p?.optString("phone"),
                    "gstNumber" to JSONObject.NULL,
                    "balance" to 0.0
                )
                SyncOpType.UPSERT_VENDOR -> json(
                    "id" to (op.entityId?.toIntOrNull() ?: p?.optIntOrNull("id")),
                    "type" to "VENDOR",
                    "name" to p?.optString("name"),
                    "phone" to p?.optString("phone"),
                    "gstNumber" to p?.optStringOrNull("gstNumber"),
                    "balance" to 0.0
                )
                else -> p?.stripNulls()
            }

            SyncEntityType.TRANSACTION -> when (op.op) {
                SyncOpType.CREATE_SALE -> {
                    val items = (p?.optJSONArray("items") ?: JSONArray())
                        .canonicalizeLineItems()
                    json(
                        "localId" to op.entityId,
                        "type" to "SALE",
                        "paymentMode" to p?.optString("paymentMode"),
                        "customerId" to p?.optIntOrNull("customerId"),
                        "amount" to p?.optDoubleOrNull("amount"),
                        "items" to items
                    )
                }
                SyncOpType.CREATE_PAYMENT -> json(
                    "localId" to op.entityId,
                    "partyId" to p?.optIntOrNull("partyId"),
                    "partyType" to p?.optString("partyType"),
                    "amount" to p?.optDoubleOrNull("amount"),
                    "mode" to p?.optString("mode")
                )
                SyncOpType.CREATE_VENDOR_PURCHASE -> json(
                    "localId" to op.entityId,
                    "vendorId" to p?.optIntOrNull("vendorId"),
                    "amount" to p?.optDoubleOrNull("amount"),
                    "mode" to p?.optString("mode"),
                    "note" to p?.optStringOrNull("note")
                )
                SyncOpType.CREATE_EXPENSE -> json(
                    "localId" to op.entityId,
                    "amount" to p?.optDoubleOrNull("amount"),
                    "mode" to p?.optString("mode"),
                    "vendorId" to p?.optIntOrNull("vendorId"),
                    "category" to p?.optStringOrNull("category"),
                    "description" to p?.optStringOrNull("description")
                )
                SyncOpType.UPSERT -> p?.stripNulls()
                else -> p?.stripNulls()
            }

            SyncEntityType.TRANSACTION_ITEM -> when (op.op) {
                SyncOpType.UPSERT_MANY -> {
                    val items = (p?.optJSONArray("items") ?: JSONArray()).canonicalizeLineItems()
                    json(
                        "transactionLocalId" to op.entityId,
                        "items" to items
                    )
                }
                else -> p?.stripNulls()
            }

            SyncEntityType.REMINDER -> when (op.op) {
                SyncOpType.UPSERT -> json(
                    "localId" to (op.entityId ?: p?.optStringOrNull("id")),
                    "type" to p?.optString("type"),
                    "refId" to p?.optIntOrNull("refId"),
                    "title" to p?.optString("title"),
                    "dueAt" to p?.optLongOrNull("dueAt"),
                    "note" to p?.optStringOrNull("note")
                )
                SyncOpType.MARK_DONE -> json(
                    "id" to (op.entityId ?: p?.optStringOrNull("id"))
                )
                else -> p?.stripNulls()
            }
        }?.stripNulls()
    }

    private fun json(vararg kv: Pair<String, Any?>): JSONObject {
        val o = JSONObject()
        kv.forEach { (k, v) ->
            if (v == null) {
                o.put(k, JSONObject.NULL)
            } else {
                o.put(k, v)
            }
        }
        return o
    }

    private fun JSONArray.canonicalizeLineItems(): JSONArray {
        val arr = JSONArray()
        for (i in 0 until length()) {
            val o = optJSONObject(i) ?: continue
            arr.put(
                JSONObject().apply {
                    put("itemId", o.optIntOrNull("itemId"))
                    put("name", o.optStringOrNull("name") ?: o.optStringOrNull("itemNameSnapshot"))
                    put("qty", o.optIntOrNull("qty"))
                    put("price", o.optDoubleOrNull("price"))
                }.stripNulls()
            )
        }
        return arr
    }

    private fun JSONObject.stripNulls(): JSONObject {
        val keys = keys().asSequence().toList()
        for (k in keys) {
            val v = opt(k)
            when (v) {
                null, JSONObject.NULL -> remove(k)
                is JSONObject -> v.stripNulls()
                is JSONArray -> v.stripNullsDeep()
            }
        }
        return this
    }

    private fun JSONArray.stripNullsDeep(): JSONArray {
        for (i in 0 until length()) {
            val v = opt(i)
            when (v) {
                is JSONObject -> v.stripNulls()
                is JSONArray -> v.stripNullsDeep()
            }
        }
        return this
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key)) optString(key).trim().ifBlank { null } else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && opt(key) != JSONObject.NULL) runCatching { optInt(key) }.getOrNull() else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && opt(key) != JSONObject.NULL) runCatching { optLong(key) }.getOrNull() else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && opt(key) != JSONObject.NULL) runCatching { optDouble(key) }.getOrNull() else null
}


