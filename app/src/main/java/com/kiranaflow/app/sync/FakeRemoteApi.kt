package com.kiranaflow.app.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * A deterministic in-memory "remote" for dev/testing.
 *
 * - Uses opId as idempotency key.
 * - Stores applied envelopes so retries are safe.
 * - Maintains simple remote state (items/parties/transactions) so we can catch consistency bugs early.
 */
class FakeRemoteApi : RemoteApi {
    private val mutex = Mutex()
    private val seenOpIds = linkedSetOf<String>()

    // "Remote DB" (in-memory, process lifetime only).
    private val itemsById = linkedMapOf<Int, JSONObject>()
    private val partiesById = linkedMapOf<Int, JSONObject>()
    private val transactionsById = linkedMapOf<String, JSONObject>() // key: localId string

    override suspend fun apply(envelope: SyncEnvelope, request: RemoteRequestPreview): RemoteResult {
        return mutex.withLock {
            if (seenOpIds.contains(envelope.opId)) {
                return@withLock RemoteResult(ok = true, message = "Idempotent replay: already applied opId=${envelope.opId}")
            }

            // Validation (still not exhaustive, but now stateful).
            val body = envelope.body
            val err = when {
                envelope.apiVersion != 1 -> "Unsupported apiVersion=${envelope.apiVersion}"
                envelope.deviceId.isBlank() -> "Missing deviceId"
                envelope.opId.isBlank() -> "Missing opId"
                else -> null
            }

            if (err != null) {
                return@withLock RemoteResult(ok = false, message = "FakeRemote rejected: $err")
            }

            // Apply.
            val applyErr = applyToState(request, body)
            if (applyErr != null) {
                return@withLock RemoteResult(ok = false, message = "FakeRemote rejected: $applyErr")
            }

            seenOpIds.add(envelope.opId)
            RemoteResult(ok = true, message = "FakeRemote accepted ${request.method} ${request.path}")
        }
    }

    private fun applyToState(request: RemoteRequestPreview, body: JSONObject?): String? {
        val path = request.path
        val method = request.method

        // ITEMS
        if (path.startsWith("/v1/items/") && method == "PUT") {
            if (body == null) return "Missing body"
            val id = body.optInt("id", -1)
            if (id <= 0) return "Missing/invalid item.id"
            val name = body.optString("name").trim()
            if (name.isBlank()) return "Missing item.name"
            itemsById[id] = JSONObject(body.toString())
            return null
        }
        if (path.startsWith("/v1/items/") && method == "DELETE") {
            val id = path.removePrefix("/v1/items/").toIntOrNull() ?: return "Invalid item id in path"
            itemsById.remove(id)
            return null
        }

        // PARTIES
        if (path.startsWith("/v1/parties/") && method == "PUT") {
            if (body == null) return "Missing body"
            val id = body.optInt("id", -1)
            if (id <= 0) return "Missing/invalid party.id"
            val name = body.optString("name").trim()
            if (name.isBlank()) return "Missing party.name"
            val type = body.optString("type").trim()
            if (type != "CUSTOMER" && type != "VENDOR") return "Invalid party.type='$type'"
            partiesById[id] = JSONObject(body.toString())
            return null
        }
        if (path == "/v1/customers" && method == "POST") {
            if (body == null) return "Missing body"
            val id = body.optInt("id", -1)
            if (id <= 0) return "Missing/invalid customer.id"
            val phone = body.optString("phone").trim()
            if (phone.isBlank()) return "Missing customer.phone"
            val name = body.optString("name").trim()
            if (name.isBlank()) return "Missing customer.name"
            partiesById[id] = JSONObject(body.toString()).apply { put("type", "CUSTOMER") }
            return null
        }
        if (path == "/v1/vendors" && method == "POST") {
            if (body == null) return "Missing body"
            val id = body.optInt("id", -1)
            if (id <= 0) return "Missing/invalid vendor.id"
            val phone = body.optString("phone").trim()
            if (phone.isBlank()) return "Missing vendor.phone"
            val name = body.optString("name").trim()
            if (name.isBlank()) return "Missing vendor.name"
            partiesById[id] = JSONObject(body.toString()).apply { put("type", "VENDOR") }
            return null
        }
        if (path.startsWith("/v1/parties/") && method == "DELETE") {
            val id = path.removePrefix("/v1/parties/").toIntOrNull() ?: return "Invalid party id in path"
            partiesById.remove(id)
            return null
        }

        // TRANSACTIONS
        if (path == "/v1/transactions/sale" && method == "POST") {
            if (body == null) return "Missing body"
            val localId = body.optString("localId").trim()
            if (localId.isBlank()) return "Missing sale.localId"
            val paymentMode = body.optString("paymentMode").trim()
            if (paymentMode.isBlank()) return "Missing sale.paymentMode"

            // If customerId is provided, ensure remote party exists.
            val customerId = body.optInt("customerId", -1).takeIf { it > 0 }
            if (customerId != null && partiesById[customerId] == null) return "Unknown customerId=$customerId (not synced yet)"

            val items = body.optJSONArray("items") ?: return "Missing sale.items[]"
            val itemErr = validateLineItems(items)
            if (itemErr != null) return itemErr

            transactionsById[localId] = JSONObject(body.toString())
            return null
        }

        if (path == "/v1/transactions/payment" && method == "POST") {
            if (body == null) return "Missing body"
            val localId = body.optString("localId").trim()
            if (localId.isBlank()) return "Missing payment.localId"
            val partyId = body.optInt("partyId", -1)
            if (partyId <= 0) return "Missing/invalid payment.partyId"
            if (partiesById[partyId] == null) return "Unknown partyId=$partyId (not synced yet)"
            transactionsById[localId] = JSONObject(body.toString())
            return null
        }

        if (path == "/v1/transactions/vendor_purchase" && method == "POST") {
            if (body == null) return "Missing body"
            val localId = body.optString("localId").trim()
            if (localId.isBlank()) return "Missing vendor_purchase.localId"
            val vendorId = body.optInt("vendorId", -1)
            if (vendorId <= 0) return "Missing/invalid vendorId"
            val vendor = partiesById[vendorId] ?: return "Unknown vendorId=$vendorId (not synced yet)"
            if (vendor.optString("type") != "VENDOR") return "vendorId=$vendorId is not type=VENDOR"
            transactionsById[localId] = JSONObject(body.toString())
            return null
        }

        if (path == "/v1/transactions/expense" && method == "POST") {
            if (body == null) return "Missing body"
            val localId = body.optString("localId").trim()
            if (localId.isBlank()) return "Missing expense.localId"
            val vendorId = body.optInt("vendorId", -1).takeIf { it > 0 }
            if (vendorId != null) {
                val vendor = partiesById[vendorId] ?: return "Unknown vendorId=$vendorId (not synced yet)"
                if (vendor.optString("type") != "VENDOR") return "vendorId=$vendorId is not type=VENDOR"
            }
            transactionsById[localId] = JSONObject(body.toString())
            return null
        }

        // Default: accept but don't store state (lets us iterate without breaking).
        return null
    }

    private fun validateLineItems(items: JSONArray): String? {
        if (items.length() <= 0) return "sale.items[] is empty"
        for (i in 0 until items.length()) {
            val it = items.optJSONObject(i) ?: return "sale.items[$i] not an object"
            val qty = it.optInt("qty", 0)
            if (qty <= 0) return "sale.items[$i].qty invalid"
            val price = it.optDouble("price", -1.0)
            if (price < 0.0) return "sale.items[$i].price invalid"
            val itemId = it.optInt("itemId", -1).takeIf { x -> x > 0 }
            if (itemId != null) {
                if (itemsById[itemId] == null) return "sale.items[$i].itemId=$itemId not synced yet"
            } else {
                val name = it.optString("name").trim()
                if (name.isBlank()) return "sale.items[$i] needs itemId or name"
            }
        }
        return null
    }
}


