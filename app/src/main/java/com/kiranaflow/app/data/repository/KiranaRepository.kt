package com.kiranaflow.app.data.repository

import androidx.room.withTransaction
import com.kiranaflow.app.data.local.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import org.json.JSONObject
import org.json.JSONArray
import com.kiranaflow.app.sync.PendingSyncQueue
import com.kiranaflow.app.sync.PendingSyncOp
import com.kiranaflow.app.sync.SyncEntityType
import com.kiranaflow.app.sync.SyncOpType
import kotlinx.coroutines.flow.map
import com.kiranaflow.app.util.BillOcrParser

class KiranaRepository(private val db: KiranaDatabase) {
    private val itemDao = db.itemDao()
    private val partyDao = db.partyDao()
    private val transactionDao = db.transactionDao()
    private val reminderDao = db.reminderDao()
    private val outboxDao = db.outboxDao()
    private val syncQueue = PendingSyncQueue(outboxDao)

    val allItems: Flow<List<ItemEntity>> = itemDao.getAllItems()
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val allTransactionItems: Flow<List<TransactionItemEntity>> = transactionDao.getAllTransactionItems()
    val activeReminders: Flow<List<ReminderEntity>> = reminderDao.getActiveReminders()
    val pendingOutboxCount: Flow<Int> = outboxDao.pendingCount()
    val failedOutboxCount: Flow<Int> = outboxDao.failedCount()
    val recentOutbox: Flow<List<OutboxEntity>> = outboxDao.observeRecent(limit = 200)
    val unsyncedTransactionIds: Flow<Set<Int>> = outboxDao.observeUnsyncedIds("TRANSACTION").map { it.toSet() }
    val unsyncedPartyIds: Flow<Set<Int>> = outboxDao.observeUnsyncedIds("PARTY").map { it.toSet() }
    val vendors: Flow<List<PartyEntity>> = partyDao.getVendors()
    val customers: Flow<List<PartyEntity>> = partyDao.getCustomers()
    val allParties: Flow<List<PartyEntity>> = partyDao.getAllParties()

    suspend fun clearOutboxDone() = outboxDao.clearDone()
    suspend fun clearOutboxAll() = outboxDao.clearAll()
    suspend fun devMarkAllUnsyncedOutboxDone() = outboxDao.markAllUnsyncedDone(System.currentTimeMillis())

    // --- Milestone D groundwork: Outbox helpers ---
    private suspend fun enqueue(op: PendingSyncOp) {
        syncQueue.enqueue(op)
    }

    // --- Seeding Data ---
    suspend fun seedInitialData(seedSyntheticData: Boolean = false) {
        if (itemDao.getAllItems().firstOrNull()?.isEmpty() == true) {
            // Seed Parties
            partyDao.insertParty(PartyEntity(name = "Hindustan Unilever", phone = "1800111111", type = "VENDOR", gstNumber = null, balance = -12000.0))
            partyDao.insertParty(PartyEntity(name = "ITC Limited", phone = "1800222222", type = "VENDOR", gstNumber = null, balance = 0.0))
            partyDao.insertParty(PartyEntity(name = "Sharma Ji", phone = "9876543210", type = "CUSTOMER", gstNumber = null, balance = 2500.0))
            
            // Seed Items
            // IDs are auto-generated (Int)
            itemDao.insertItem(ItemEntity(
                name = "Toor Dal Premium",
                price = 150.0,
                stock = 45,
                category = "Staples",
                rackLocation = "Rack A1",
                marginPercentage = ((150.0 - 120.0) / 120.0 * 100),
                barcode = "8901234567890",
                costPrice = 120.0,
                gstPercentage = 0.0,
                reorderPoint = 10,
                vendorId = null // Can't easily link to auto-generated ID in seed without querying back
            ))
            itemDao.insertItem(ItemEntity(
                name = "Fortune Sun Oil 1L",
                price = 155.0,
                stock = 5,
                category = "Oil",
                rackLocation = "Rack B2",
                marginPercentage = ((155.0 - 135.0) / 135.0 * 100),
                barcode = "8901234567891",
                costPrice = 135.0,
                gstPercentage = 5.0,
                reorderPoint = 10,
                vendorId = null
            ))
        }

        // Synthetic data (debug only): insert 1+ year of transactions if there are none.
        if (seedSyntheticData) {
            val existingTxEmpty = transactionDao.getAllTransactions().firstOrNull()?.isEmpty() == true
            if (existingTxEmpty) {
                seedSyntheticTransactions(days = 380)
            }
        }
    }

    /**
     * DEV/TEST ONLY: Generate synthetic sales + expenses over ~[days] days.
     * Idempotent by caller (only run when there are no existing transactions).
     *
     * Notes:
     * - Uses TransactionDao.insertSale() but does NOT adjust inventory stock.
     * - Updates customer/vendor balances only for CREDIT transactions, matching app semantics.
     */
    private suspend fun seedSyntheticTransactions(days: Int) {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60L * 60L * 1000L
        val start = now - (days.toLong() * dayMs)
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())

        val items = itemDao.getAllItems().first().ifEmpty { return }
        val customers = partyDao.getCustomers().first()
        val vendors = partyDao.getVendors().first()

        // Stable randomness so charts are repeatable across installs.
        val rnd = Random(1337)

        val expenseCategories = listOf("Rent", "Electricity", "Packaging", "Transport", "Staff", "Misc")
        val paymentModes = listOf("CASH", "UPI", "CREDIT")

        // Insert in chronological order so date-based UI looks natural.
        var day = start
        while (day <= now) {
            val dayStart = day

            // Sales: 0..6 per day (more on weekends)
            val isWeekend = ((day / dayMs) % 7L) in setOf(5L, 6L)
            val salesCount = if (isWeekend) rnd.nextInt(1, 7) else rnd.nextInt(0, 6)

            repeat(salesCount) {
                val txAt = dayStart + rnd.nextLong(dayMs.coerceAtLeast(1))
                val mode = when (rnd.nextInt(100)) {
                    in 0..49 -> "CASH"
                    in 50..84 -> "UPI"
                    else -> "CREDIT"
                }
                val customerId: Int? = when {
                    mode == "CREDIT" && customers.isNotEmpty() -> customers.random(rnd).id
                    customers.isNotEmpty() && rnd.nextInt(100) < 35 -> customers.random(rnd).id
                    else -> null
                }

                val lineCount = rnd.nextInt(1, minOf(5, items.size + 1))
                val picked = items.shuffled(rnd).take(lineCount)
                val lines = picked.map { item ->
                    val qty = rnd.nextInt(1, 4)
                    item to qty
                }
                val total = lines.sumOf { (item, qty) -> item.price * qty }

                val tx = TransactionEntity(
                    title = "Sale - ${lines.size} items ($mode)",
                    type = "SALE",
                    amount = total,
                    date = txAt,
                    time = timeFmt.format(Date(txAt)),
                    customerId = customerId,
                    vendorId = null,
                    paymentMode = mode
                )
                val txItems = lines.map { (item, qty) ->
                    TransactionItemEntity(
                        transactionId = 0,
                        itemId = item.id,
                        itemNameSnapshot = item.name,
                        qty = qty,
                        price = item.price
                    )
                }

                db.withTransaction {
                    transactionDao.insertSale(tx, txItems)
                    if (customerId != null && mode == "CREDIT") {
                        partyDao.updateBalance(customerId, total)
                    }
                }
            }

            // Expenses: 0..2 per day, plus a slightly larger weekly expense.
            val dailyExpenseCount = rnd.nextInt(0, 3)
            repeat(dailyExpenseCount) {
                val txAt = dayStart + rnd.nextLong(dayMs.coerceAtLeast(1))
                val mode = paymentModes.random(rnd)
                val vendor = if (vendors.isNotEmpty() && rnd.nextInt(100) < 45) vendors.random(rnd) else null
                val category = expenseCategories.random(rnd)
                val amount = when (category) {
                    "Rent" -> rnd.nextInt(800, 2500)
                    "Electricity" -> rnd.nextInt(150, 700)
                    "Staff" -> rnd.nextInt(300, 1500)
                    else -> rnd.nextInt(50, 600)
                }.toDouble()

                val title = buildString {
                    append("Expense • $category")
                    if (vendor != null) append(" • ${vendor.name}")
                    append(" ($mode)")
                }
                val tx = TransactionEntity(
                    title = title,
                    type = "EXPENSE",
                    amount = amount,
                    date = txAt,
                    time = timeFmt.format(Date(txAt)),
                    customerId = null,
                    vendorId = vendor?.id,
                    paymentMode = mode
                )
                val deltaBalance = if (vendor != null && mode == "CREDIT") -amount else 0.0
                db.withTransaction {
                    transactionDao.insertTransaction(tx)
                    if (deltaBalance != 0.0 && vendor != null) {
                        partyDao.updateBalance(vendor.id, deltaBalance)
                    }
                }
            }

            // Weekly "bulk purchase" on Monday
            if (((day / dayMs) % 7L) == 0L && vendors.isNotEmpty()) {
                val txAt = dayStart + rnd.nextLong(dayMs.coerceAtLeast(1))
                val vendor = vendors.random(rnd)
                val mode = if (rnd.nextInt(100) < 35) "CREDIT" else listOf("CASH", "UPI").random(rnd)
                val amount = rnd.nextInt(800, 6000).toDouble()
                val tx = TransactionEntity(
                    title = "Purchase from ${vendor.name} (Bulk) ($mode)",
                    type = "EXPENSE",
                    amount = amount,
                    date = txAt,
                    time = timeFmt.format(Date(txAt)),
                    customerId = null,
                    vendorId = vendor.id,
                    paymentMode = mode
                )
                val deltaBalance = if (mode == "CREDIT") -amount else 0.0
                db.withTransaction {
                    transactionDao.insertTransaction(tx)
                    if (deltaBalance != 0.0) {
                        partyDao.updateBalance(vendor.id, deltaBalance)
                    }
                }
            }

            day += dayMs
        }
    }
    
    // --- Actions ---

    /**
     * Insert item into Room and return a copy with the generated id.
     * Kept as a separate method so UIs can create-and-use the new Item immediately (e.g., billing quick-add).
     */
    suspend fun addItemReturning(item: ItemEntity): ItemEntity {
        val id = itemDao.insertItem(item).toInt()
        runCatching {
            enqueue(
                PendingSyncOp(
                    entityType = SyncEntityType.ITEM,
                    entityId = id.toString(),
                    op = SyncOpType.UPSERT,
                    payload = JSONObject()
                        .put("id", id)
                        .put("name", item.name)
                        .put("price", item.price)
                        .put("stock", item.stock)
                        .put("category", item.category)
                        .put("costPrice", item.costPrice)
                        .put("gstPercentage", item.gstPercentage)
                        .put("reorderPoint", item.reorderPoint)
                        .put("vendorId", item.vendorId)
                        .put("rackLocation", item.rackLocation)
                        .put("barcode", item.barcode)
                        .put("imageUri", item.imageUri)
                        .put("expiryDateMillis", item.expiryDateMillis)
                )
            )
        }
        return item.copy(id = id)
    }

    suspend fun addItem(item: ItemEntity) {
        addItemReturning(item)
    }
    
    suspend fun updateItem(item: ItemEntity) {
        val id = itemDao.insertItem(item).toInt() // Replace
        runCatching {
            enqueue(
                PendingSyncOp(
                    entityType = SyncEntityType.ITEM,
                    entityId = id.toString(),
                    op = SyncOpType.UPSERT,
                    payload = JSONObject()
                        .put("id", id)
                        .put("name", item.name)
                        .put("price", item.price)
                        .put("stock", item.stock)
                        .put("category", item.category)
                        .put("costPrice", item.costPrice)
                        .put("gstPercentage", item.gstPercentage)
                        .put("reorderPoint", item.reorderPoint)
                        .put("vendorId", item.vendorId)
                        .put("rackLocation", item.rackLocation)
                        .put("barcode", item.barcode)
                        .put("imageUri", item.imageUri)
                        .put("expiryDateMillis", item.expiryDateMillis)
                )
            )
        }
    }
    
    suspend fun deleteItem(item: ItemEntity) {
        itemDao.softDelete(item.id)
        runCatching {
            enqueue(
                PendingSyncOp(
                    entityType = SyncEntityType.ITEM,
                    entityId = item.id.toString(),
                    op = SyncOpType.DELETE,
                    payload = JSONObject().put("id", item.id)
                )
            )
        }
    }

    suspend fun deleteItemsByIds(ids: List<Int>) {
        if (ids.isEmpty()) return
        val distinct = ids.distinct()
        itemDao.softDeleteMany(distinct)
        runCatching {
            distinct.forEach { id ->
                enqueue(PendingSyncOp(SyncEntityType.ITEM, id.toString(), SyncOpType.DELETE, JSONObject().put("id", id)))
            }
        }
    }

    suspend fun addParty(party: PartyEntity) {
        val id = partyDao.insertParty(party).toInt()
        runCatching {
            enqueue(
                PendingSyncOp(
                    entityType = SyncEntityType.PARTY,
                    entityId = id.toString(),
                    op = SyncOpType.UPSERT,
                    payload = JSONObject()
                        .put("id", id)
                        .put("name", party.name)
                        .put("phone", party.phone)
                        .put("type", party.type)
                        .put("gstNumber", party.gstNumber)
                        .put("balance", party.balance)
                )
            )
        }
    }

    suspend fun addCustomer(name: String, phone: String): PartyEntity? {
        val cleanName = name.trim()
        val cleanPhone = phone.trim()
        if (cleanName.isBlank() || cleanPhone.isBlank()) return null
        val id = partyDao.insertParty(
            PartyEntity(
                name = cleanName,
                phone = cleanPhone,
                type = "CUSTOMER",
                gstNumber = null,
                balance = 0.0
            )
        ).toInt()
        runCatching {
            enqueue(
                PendingSyncOp(
                    entityType = SyncEntityType.PARTY,
                    entityId = id.toString(),
                    op = SyncOpType.UPSERT_CUSTOMER,
                    payload = JSONObject()
                        .put("id", id)
                        .put("name", cleanName)
                        .put("phone", cleanPhone)
                        .put("type", "CUSTOMER")
                )
            )
        }
        return partyDao.findPartyByPhoneAndType(cleanPhone, "CUSTOMER") ?: partyDao.findCustomerByPhone(cleanPhone)
    }

    private fun normalizePhoneDigits(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        if (digits.isBlank()) return ""
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    /**
     * Bulk-import customers by phone, with safe de-dupe against existing customers (by normalized digits).
     * Returns Pair(addedCount, skippedCount).
     */
    suspend fun addCustomersBulk(customers: List<Pair<String, String>>): Pair<Int, Int> {
        val existing = this.customers.first()
        val existingPhones = existing
            .asSequence()
            .map { normalizePhoneDigits(it.phone) }
            .filter { it.isNotBlank() }
            .toSet()

        var added = 0
        var skipped = 0
        for ((rawName, rawPhone) in customers) {
            val name = rawName.trim()
            val phone = normalizePhoneDigits(rawPhone)
            if (name.isBlank() || phone.isBlank()) {
                skipped++
                continue
            }
            if (existingPhones.contains(phone)) {
                skipped++
                continue
            }
            val id = partyDao.insertParty(
                PartyEntity(
                    name = name,
                    phone = phone,
                    type = "CUSTOMER",
                    gstNumber = null,
                    balance = 0.0
                )
            ).toInt()
            runCatching {
                enqueue(
                    PendingSyncOp(
                        entityType = SyncEntityType.PARTY,
                        entityId = id.toString(),
                        op = SyncOpType.UPSERT_CUSTOMER,
                        payload = JSONObject()
                            .put("id", id)
                            .put("name", name)
                            .put("phone", phone)
                            .put("type", "CUSTOMER")
                    )
                )
            }
            added++
        }
        return Pair(added, skipped)
    }

    suspend fun addVendor(name: String, phone: String, gstNumber: String?): PartyEntity? {
        val cleanName = name.trim()
        val cleanPhone = phone.trim()
        val cleanGst = gstNumber?.trim()?.ifBlank { null }
        if (cleanName.isBlank() || cleanPhone.isBlank()) return null
        val id = partyDao.insertParty(
            PartyEntity(
                name = cleanName,
                phone = cleanPhone,
                type = "VENDOR",
                gstNumber = cleanGst,
                balance = 0.0
            )
        ).toInt()
        runCatching {
            enqueue(
                PendingSyncOp(
                    entityType = SyncEntityType.PARTY,
                    entityId = id.toString(),
                    op = SyncOpType.UPSERT_VENDOR,
                    payload = JSONObject()
                        .put("id", id)
                        .put("name", cleanName)
                        .put("phone", cleanPhone)
                        .put("type", "VENDOR")
                        .put("gstNumber", cleanGst)
                )
            )
        }
        return partyDao.findPartyByPhoneAndType(cleanPhone, "VENDOR")
    }
    
    suspend fun updateParty(party: PartyEntity) {
        val id = partyDao.insertParty(party).toInt()
        runCatching {
            enqueue(
                PendingSyncOp(
                    entityType = SyncEntityType.PARTY,
                    entityId = id.toString(),
                    op = SyncOpType.UPSERT,
                    payload = JSONObject()
                        .put("id", id)
                        .put("name", party.name)
                        .put("phone", party.phone)
                        .put("type", party.type)
                        .put("gstNumber", party.gstNumber)
                        .put("balance", party.balance)
                )
            )
        }
    }

    suspend fun deleteParty(party: PartyEntity) {
        partyDao.deleteParty(party)
        runCatching {
            enqueue(PendingSyncOp(SyncEntityType.PARTY, party.id.toString(), SyncOpType.DELETE, JSONObject().put("id", party.id)))
        }
    }

    suspend fun deletePartiesByIds(ids: List<Int>) {
        if (ids.isEmpty()) return
        val distinct = ids.distinct()
        partyDao.deletePartiesByIds(distinct)
        runCatching {
            distinct.forEach { id ->
                enqueue(PendingSyncOp(SyncEntityType.PARTY, id.toString(), SyncOpType.DELETE, JSONObject().put("id", id)))
            }
        }
    }

    suspend fun deleteTransactionsByIds(ids: List<Int>) {
        if (ids.isEmpty()) return
        val distinct = ids.distinct()
        db.withTransaction {
            transactionDao.deleteTransactionsByIds(distinct)
        }
        runCatching {
            distinct.forEach { id ->
                enqueue(PendingSyncOp(SyncEntityType.TRANSACTION, id.toString(), SyncOpType.DELETE, JSONObject().put("id", id)))
            }
        }
    }

    fun transactionItemsFor(transactionId: Int): Flow<List<TransactionItemEntity>> {
        return transactionDao.getItemsForTransaction(transactionId)
    }

    fun transactionById(transactionId: Int): Flow<TransactionEntity?> {
        if (transactionId <= 0) return flowOf(null)
        return transactionDao.getTransactionById(transactionId)
    }

    fun partyById(partyId: Int): Flow<PartyEntity?> {
        if (partyId <= 0) return flowOf(null)
        return partyDao.getPartyById(partyId)
    }

    suspend fun addTransactionItems(transactionId: Int, items: List<TransactionItemEntity>) {
        if (transactionId <= 0) return
        if (items.isEmpty()) return
        val normalized = items.map {
            it.copy(id = 0, transactionId = transactionId)
        }
        db.withTransaction {
            transactionDao.insertTransactionItems(normalized)
        }
        runCatching {
            val payload = JSONObject()
                .put("transactionId", transactionId)
                .put(
                    "items",
                    JSONArray().apply {
                        normalized.forEach { it ->
                            put(
                                JSONObject()
                                    .put("itemId", it.itemId)
                                    .put("itemNameSnapshot", it.itemNameSnapshot)
                                    .put("qty", it.qty)
                                    .put("price", it.price)
                            )
                        }
                    }
                )
            enqueue(PendingSyncOp(SyncEntityType.TRANSACTION_ITEM, transactionId.toString(), SyncOpType.UPSERT_MANY, payload))
        }
    }

    suspend fun addReminder(type: String, refId: Int?, title: String, dueAt: Long, note: String? = null) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return
        reminderDao.insertReminder(
            ReminderEntity(
                title = cleanTitle,
                type = type,
                refId = refId,
                dueAt = dueAt,
                note = note?.trim()?.ifBlank { null }
            )
        )
        runCatching {
            enqueue(
                PendingSyncOp(
                    entityType = SyncEntityType.REMINDER,
                    entityId = null,
                    op = SyncOpType.UPSERT,
                    payload = JSONObject()
                        .put("type", type)
                        .put("refId", refId)
                        .put("title", cleanTitle)
                        .put("dueAt", dueAt)
                        .put("note", note?.trim()?.ifBlank { null })
                )
            )
        }
    }

    suspend fun markReminderDone(id: Int) {
        reminderDao.markDone(id)
        runCatching {
            enqueue(PendingSyncOp(SyncEntityType.REMINDER, id.toString(), SyncOpType.MARK_DONE, JSONObject().put("id", id)))
        }
    }

    suspend fun deleteReminder(id: Int) {
        reminderDao.deleteReminder(id)
        runCatching {
            enqueue(PendingSyncOp(SyncEntityType.REMINDER, id.toString(), SyncOpType.DELETE, JSONObject().put("id", id)))
        }
    }

    // Process Sale/Checkout
    suspend fun processSale(
        items: List<Pair<ItemEntity, Int>>, // Item, Qty
        paymentMode: String,
        customerId: Int?,
        totalAmount: Double
    ) {
        val now = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(now))
        
        val transaction = TransactionEntity(
            title = "Sale - ${items.size} items ($paymentMode)",
            type = "SALE",
            amount = totalAmount,
            date = now,
            time = timeStr,
            customerId = customerId,
            vendorId = null,
            paymentMode = paymentMode
        )
        
        val txItems = items.map { (item, qty) ->
            TransactionItemEntity(
                transactionId = 0, // Will be set after transaction insert
                itemId = item.id,
                itemNameSnapshot = item.name,
                qty = qty,
                unit = if (item.isLoose) "GRAM" else "PCS",
                price = item.price
            )
        }

        val txId = db.withTransaction {
            val id = transactionDao.insertSale(transaction, txItems)
            items.forEach { (item, qty) ->
                if (item.isLoose) {
                    // qty is grams; stock is stored in KG for loose items.
                    itemDao.decreaseStockKg(item.id, qty / 1000.0)
                } else {
                    itemDao.decreaseStock(item.id, qty)
                }
            }
            if (customerId != null && paymentMode == "CREDIT") {
                 partyDao.updateBalance(customerId, totalAmount)
            }
            id
        }

        runCatching {
            val lines = JSONArray().apply {
                items.forEach { (it, qty) ->
                    put(
                        JSONObject()
                            .put("itemId", it.id)
                            .put("name", it.name)
                            .put("qty", qty)
                            .put("unit", if (it.isLoose) "GRAM" else "PCS")
                            .put("price", it.price)
                    )
                }
            }
            val payload = JSONObject()
                .put("type", "SALE")
                .put("paymentMode", paymentMode)
                .put("customerId", customerId)
                .put("amount", totalAmount)
                .put("items", lines)
            enqueue(PendingSyncOp(SyncEntityType.TRANSACTION, txId.toString(), SyncOpType.CREATE_SALE, payload))
        }
    }

    suspend fun recordPayment(party: PartyEntity, amount: Double, mode: String) {
        val isVendor = party.type == "VENDOR"
        val type = if (isVendor) "EXPENSE" else "INCOME"
        val now = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(now))
        
        val tx = TransactionEntity(
            title = "Payment ${if (isVendor) "to" else "from"} ${party.name}",
            type = type,
            amount = amount,
            date = now,
            time = timeStr,
            customerId = if (!isVendor) party.id else null,
            vendorId = if (isVendor) party.id else null,
            paymentMode = mode
        )

        val txId = db.withTransaction {
            val id = transactionDao.insertTransaction(tx).toInt()
            partyDao.updateBalance(party.id, if (isVendor) amount else -amount)
            id
        }

        runCatching {
            enqueue(
                PendingSyncOp(
                    entityType = SyncEntityType.TRANSACTION,
                    entityId = txId.toString(),
                    op = SyncOpType.CREATE_PAYMENT,
                    payload = JSONObject()
                        .put("partyId", party.id)
                        .put("partyType", party.type)
                        .put("amount", amount)
                        .put("mode", mode)
                )
            )
        }
    }

    /**
     * Record a vendor purchase/expense. If [mode] is CREDIT (udhaar), it increases payables by
     * decreasing vendor balance (more negative). If paid via CASH/UPI, it records the expense
     * but does not affect payables balance.
     */
    suspend fun recordVendorPurchase(vendor: PartyEntity, amount: Double, mode: String, note: String? = null) {
        if (vendor.type != "VENDOR") return
        if (amount <= 0.0) return

        val now = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(now))
        val cleanNote = note?.trim()?.ifBlank { null }
        val title = buildString {
            append("Purchase from ${vendor.name}")
            if (!cleanNote.isNullOrBlank()) append(" • $cleanNote")
            append(" ($mode)")
        }

        val tx = TransactionEntity(
            title = title,
            type = "EXPENSE",
            amount = amount,
            date = now,
            time = timeStr,
            customerId = null,
            vendorId = vendor.id,
            paymentMode = mode
        )

        val deltaBalance = if (mode == "CREDIT") -amount else 0.0

        val txId = db.withTransaction {
            val id = transactionDao.insertTransaction(tx).toInt()
            if (deltaBalance != 0.0) {
                partyDao.updateBalance(vendor.id, deltaBalance)
            }
            id
        }

        runCatching {
            enqueue(
                PendingSyncOp(
                    entityType = SyncEntityType.TRANSACTION,
                    entityId = txId.toString(),
                    op = SyncOpType.CREATE_VENDOR_PURCHASE,
                    payload = JSONObject()
                        .put("vendorId", vendor.id)
                        .put("amount", amount)
                        .put("mode", mode)
                        .put("note", cleanNote)
                )
            )
        }
    }

    /**
     * Record a business expense. Optionally link to a vendor. If linked vendor is paid on Udhaar
     * (mode=CREDIT), this increases payables by decreasing vendor balance (more negative).
     */
    suspend fun recordExpense(
        amount: Double,
        mode: String,
        vendor: PartyEntity? = null,
        category: String? = null,
        description: String? = null,
        receiptImageUri: String? = null
    ) {
        if (amount <= 0.0) return
        if (vendor != null && vendor.type != "VENDOR") return

        val now = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(now))
        val cat = category?.trim()?.ifBlank { null }
        val desc = description?.trim()?.ifBlank { null }
        val title = buildString {
            append("Expense")
            if (!cat.isNullOrBlank()) append(" • $cat")
            if (!desc.isNullOrBlank()) append(" • $desc")
            if (vendor != null) append(" • ${vendor.name}")
            append(" ($mode)")
        }

        val tx = TransactionEntity(
            title = title,
            type = "EXPENSE",
            amount = amount,
            date = now,
            time = timeStr,
            customerId = null,
            vendorId = vendor?.id,
            paymentMode = mode,
            receiptImageUri = receiptImageUri?.trim()?.ifBlank { null }
        )

        val deltaBalance = if (vendor != null && mode == "CREDIT") -amount else 0.0

        val txId = db.withTransaction {
            val id = transactionDao.insertTransaction(tx).toInt()
            if (deltaBalance != 0.0 && vendor != null) {
                partyDao.updateBalance(vendor.id, deltaBalance)
            }
            id
        }

        runCatching {
            enqueue(
                PendingSyncOp(
                    entityType = SyncEntityType.TRANSACTION,
                    entityId = txId.toString(),
                    op = SyncOpType.CREATE_EXPENSE,
                    payload = JSONObject()
                        .put("amount", amount)
                        .put("mode", mode)
                        .put("vendorId", vendor?.id)
                        .put("category", cat)
                        .put("description", desc)
                        .put("receiptImageUri", receiptImageUri?.trim()?.ifBlank { null })
                )
            )
        }
    }

    data class InventoryImportResult(
        val added: Int,
        val updated: Int,
        val vendorId: Int? = null,
        val vendorName: String? = null
    )

    /**
     * Apply parsed vendor bill items to inventory:
     * - Adds new products if they don't exist (by case-insensitive exact name)
     * - Increases stock for existing products
     * - Updates costPrice when available
     * - Best-effort vendor linkage if vendor can be identified
     */
    suspend fun processVendorBill(parsed: BillOcrParser.ParsedBill): InventoryImportResult {
        val vendorsSnapshot = runCatching { partyDao.getVendors().first() }.getOrElse { emptyList() }
        val vendorId = runCatching {
            val v = parsed.vendor
            val vName = v.name?.trim().orEmpty()
            val vPhone = v.phone?.filter { it.isDigit() }?.takeLast(10).orEmpty()
            val vGst = v.gstNumber?.trim()?.uppercase().orEmpty()

            // 1) Strong identifiers: phone, GSTIN
            if (vPhone.length == 10) {
                val existing = partyDao.findPartyByPhoneAndType(vPhone, "VENDOR")
                if (existing != null) return@runCatching existing.id
                // Create only when we have a sane name + phone.
                if (vName.isNotBlank()) return@runCatching addVendor(vName, vPhone, v.gstNumber)?.id
            }

            if (vGst.isNotBlank()) {
                val matchByGst = vendorsSnapshot.firstOrNull { it.gstNumber?.trim()?.uppercase() == vGst }
                if (matchByGst != null) return@runCatching matchByGst.id
            }

            // 2) Fallback: fuzzy name match to existing vendors (do NOT auto-create).
            if (vName.isBlank()) return@runCatching null
            findBestVendorMatchId(vName, vendorsSnapshot)
        }.getOrNull()

        var added = 0
        var updated = 0

        val itemsSnapshot = runCatching { itemDao.getAllItems().first() }.getOrElse { emptyList() }
        val normalizedItemIndex: Map<String, ItemEntity> = itemsSnapshot
            .asSequence()
            .filter { !it.isDeleted }
            .map { it to normalizeForMatch(it.name) }
            .filter { (_, norm) -> norm.isNotBlank() }
            .distinctBy { (_, norm) -> norm }
            .associate { (item, norm) -> norm to item }

        for (it in parsed.items) {
            val name = it.name.trim()
            if (name.isBlank()) continue
            val qty = it.qty
            if (qty <= 0) continue

            val existing = itemDao.getItemByName(name) ?: run {
                val norm = normalizeForMatch(name)
                normalizedItemIndex[norm] ?: findBestItemMatch(name, itemsSnapshot)
            }
            if (existing != null) {
                val newStock = existing.stock + qty
                val newCost = it.unitPrice ?: existing.costPrice
                updateItem(
                    existing.copy(
                        stock = newStock,
                        costPrice = newCost,
                        vendorId = vendorId ?: existing.vendorId
                    )
                )
                updated++
            } else {
                val unit = it.unitPrice ?: 0.0
                addItemReturning(
                    ItemEntity(
                        name = name,
                        price = unit, // default selling price = unit cost (can be edited later)
                        stock = qty,
                        category = "General",
                        rackLocation = null,
                        marginPercentage = 0.0,
                        barcode = null,
                        costPrice = unit,
                        gstPercentage = null,
                        reorderPoint = 10,
                        vendorId = vendorId,
                        imageUri = null,
                        expiryDateMillis = null,
                        isDeleted = false
                    )
                )
                added++
            }
        }

        return InventoryImportResult(
            added = added,
            updated = updated,
            vendorId = vendorId,
            vendorName = parsed.vendor.name
        )
    }

    private fun findBestVendorMatchId(vendorName: String, vendors: List<PartyEntity>): Int? {
        val target = vendorNameTokens(vendorName)
        if (target.isEmpty()) return null
        val best = vendors
            .asSequence()
            .map { v -> v to jaccard(target, vendorNameTokens(v.name)) }
            .maxByOrNull { it.second }
            ?: return null
        // Conservative threshold so we don't mis-assign purchases.
        return best.takeIf { it.second >= 0.72 }?.first?.id
    }

    private fun findBestItemMatch(itemName: String, items: List<ItemEntity>): ItemEntity? {
        val target = itemNameTokens(itemName)
        if (target.isEmpty()) return null
        val best = items
            .asSequence()
            .filter { !it.isDeleted }
            .map { it to jaccard(target, itemNameTokens(it.name)) }
            .maxByOrNull { it.second }
            ?: return null
        // Slightly lower than vendor threshold, but still conservative.
        return best.takeIf { it.second >= 0.66 }?.first
    }

    private fun normalizeForMatch(s: String): String {
        return s
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun vendorNameTokens(s: String): Set<String> {
        val stop = setOf(
            "pvt", "pvt.", "ltd", "ltd.", "limited", "private", "llp",
            "store", "stores", "mart", "traders", "trader", "enterprise", "enterprises",
            "agency", "agencies", "distributor", "distributors", "wholesale", "retail",
            "and", "&", "the", "co", "co.", "company"
        )
        return normalizeForMatch(s)
            .split(" ")
            .asSequence()
            .filter { it.isNotBlank() && it.length >= 2 }
            .filterNot { it in stop }
            .toSet()
    }

    private fun itemNameTokens(s: String): Set<String> {
        val stop = setOf("pcs", "pc", "nos", "no", "qty", "rate", "mrp", "rs", "inr", "gm", "gms", "grams", "kg", "kgs")
        return normalizeForMatch(s)
            .split(" ")
            .asSequence()
            .filter { it.isNotBlank() && it.length >= 2 }
            .filterNot { it in stop }
            .toSet()
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val inter = a.intersect(b).size.toDouble()
        val union = (a.size + b.size - inter).toDouble()
        return if (union <= 0.0) 0.0 else inter / union
    }

    suspend fun processInventorySheetRows(rows: List<com.kiranaflow.app.util.InventorySheetParser.InventoryRow>): InventoryImportResult {
        var added = 0
        var updated = 0

        for (r in rows) {
            val name = r.name.trim()
            if (name.isBlank()) continue
            val delivered = (if (r.stock > 0) r.stock else r.qty).coerceAtLeast(0)

            val existing = itemDao.getItemByName(name)
            if (existing != null) {
                val newStock = existing.stock + delivered
                val newCost = r.costPrice ?: existing.costPrice
                val newSell = r.sellPrice ?: existing.price
                val newCat = r.category?.trim()?.ifBlank { null } ?: existing.category
                updateItem(
                    existing.copy(
                        stock = newStock,
                        costPrice = newCost,
                        price = newSell,
                        category = newCat
                    )
                )
                updated++
            } else {
                val cost = r.costPrice ?: 0.0
                val sell = r.sellPrice ?: cost
                addItemReturning(
                    ItemEntity(
                        name = name,
                        price = sell,
                        stock = delivered,
                        category = r.category?.trim()?.ifBlank { null } ?: "General",
                        rackLocation = null,
                        marginPercentage = 0.0,
                        barcode = null,
                        costPrice = cost,
                        gstPercentage = null,
                        reorderPoint = 10,
                        vendorId = null,
                        imageUri = null,
                        expiryDateMillis = null,
                        isDeleted = false
                    )
                )
                added++
            }
        }

        return InventoryImportResult(added = added, updated = updated)
    }

    /**
     * Record a vendor bill as an expense, optionally on Udhaar, with itemized line-items.
     *
     * Notes:
     * - This does NOT change inventory (inventory is updated separately via [processVendorBill]).
     * - If [mode] == CREDIT, it increases payables by decreasing vendor balance (more negative).
     */
    suspend fun recordVendorBillPurchaseWithItems(
        vendor: PartyEntity,
        parsed: BillOcrParser.ParsedBill,
        mode: String,
        receiptImageUri: String? = null
    ): Int? {
        if (vendor.type != "VENDOR") return null
        val lines = parsed.items
            .mapNotNull { it.toPurchaseLineOrNull() }
            .ifEmpty { return null }

        val amount = lines.sumOf { it.lineTotal }
        if (amount <= 0.0) return null

        val now = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(now))
        val title = "Vendor Bill • ${vendor.name} ($mode)"

        // Map to transaction_items for history display.
        val txItems: List<TransactionItemEntity> = lines.map { li ->
            TransactionItemEntity(
                transactionId = 0,
                itemId = li.itemId,
                itemNameSnapshot = li.name,
                qty = li.qty,
                unit = li.unit,
                price = li.unitPrice
            )
        }

        val tx = TransactionEntity(
            title = title,
            type = "EXPENSE",
            amount = amount,
            date = now,
            time = timeStr,
            customerId = null,
            vendorId = vendor.id,
            paymentMode = mode,
            receiptImageUri = receiptImageUri?.trim()?.ifBlank { null }
        )

        val deltaBalance = if (mode == "CREDIT") -amount else 0.0

        val txId = db.withTransaction {
            val id = transactionDao.insertTransactionWithItems(tx, txItems)
            if (deltaBalance != 0.0) {
                partyDao.updateBalance(vendor.id, deltaBalance)
            }
            id
        }

        return txId
    }

    private data class PurchaseLine(
        val itemId: Int?,
        val name: String,
        val qty: Int,
        val unit: String,
        val unitPrice: Double,
        val lineTotal: Double
    )

    private suspend fun BillOcrParser.ParsedBillItem.toPurchaseLineOrNull(): PurchaseLine? {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return null
        val qtyInt = qty
        if (qtyInt <= 0) return null
        val unitPrice = unitPrice ?: run {
            val t = total
            if (t != null && qtyInt > 0) t / qtyInt else null
        } ?: return null
        val lineTotal = total ?: (unitPrice * qtyInt)
        if (lineTotal <= 0.0) return null

        val unitNorm = when (unit?.uppercase()) {
            "KG" -> "KG"
            "G" -> "G"
            "GRAM" -> "G"
            else -> "PCS"
        }

        val itemId = runCatching { itemDao.getItemByName(cleanName)?.id }.getOrNull()
        return PurchaseLine(
            itemId = itemId,
            name = cleanName,
            qty = qtyInt,
            unit = unitNorm,
            unitPrice = unitPrice,
            lineTotal = lineTotal
        )
    }
}
