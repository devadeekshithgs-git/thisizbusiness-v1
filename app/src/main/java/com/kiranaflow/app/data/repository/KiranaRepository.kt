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
import com.kiranaflow.app.util.ImmediateSyncManager
import kotlin.math.abs
import kotlin.math.roundToInt

class KiranaRepository(private val db: KiranaDatabase) {
    private val itemDao = db.itemDao()
    private val partyDao = db.partyDao()
    private val transactionDao = db.transactionDao()
    private val userDao = db.userDao()
    private val adjustmentDao = db.transactionAdjustmentDao()
    private val stockMovementDao = db.stockMovementDao()
    private val editHistoryDao = db.transactionEditHistoryDao()
    private val reminderDao = db.reminderDao()
    private val outboxDao = db.outboxDao()
    private val syncQueue = PendingSyncQueue(outboxDao)

    val allItems: Flow<List<ItemEntity>> = itemDao.getAllItems()
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val allTransactionItems: Flow<List<TransactionItemEntity>> = transactionDao.getAllTransactionItems()
    val activeReminders: Flow<List<ReminderEntity>> = reminderDao.getActiveReminders()
    
    // Reminders including recently completed ones (within 24 hours) for strike-through display
    fun getRemindersWithRecentCompleted(): Flow<List<ReminderEntity>> {
        val cutoffMillis = System.currentTimeMillis() - (24L * 60L * 60L * 1000L)
        return reminderDao.getRemindersWithRecentCompleted(cutoffMillis)
    }
    val pendingOutboxCount: Flow<Int> = outboxDao.pendingCount()
    val failedOutboxCount: Flow<Int> = outboxDao.failedCount()
    val recentOutbox: Flow<List<OutboxEntity>> = outboxDao.observeRecent(limit = 200)
    val unsyncedTransactionIds: Flow<Set<Int>> = outboxDao.observeUnsyncedIds("TRANSACTION").map { it.toSet() }
    val unsyncedPartyIds: Flow<Set<Int>> = outboxDao.observeUnsyncedIds("PARTY").map { it.toSet() }
    val vendors: Flow<List<PartyEntity>> = partyDao.getVendors()
    val customers: Flow<List<PartyEntity>> = partyDao.getCustomers()
    val allParties: Flow<List<PartyEntity>> = partyDao.getAllParties()

    /**
     * Read-only helper for feature modules that need a one-time snapshot (e.g. scan/diff).
     * This is additive and does not change any existing flows.
     */
    suspend fun allItemsSnapshot(): List<ItemEntity> = runCatching { itemDao.getAllItems().first() }.getOrElse { emptyList() }

    suspend fun partySnapshotById(id: Int): PartyEntity? = runCatching { partyDao.getPartyById(id).first() }.getOrNull()

    suspend fun clearOutboxDone() = outboxDao.clearDone()
    suspend fun clearOutboxAll() = outboxDao.clearAll()
    suspend fun devMarkAllUnsyncedOutboxDone() = outboxDao.markAllUnsyncedDone(System.currentTimeMillis())

    // --- Milestone D groundwork: Outbox helpers ---
    /**
     * Enqueue operation and trigger immediate sync.
     * Data is synced immediately when online, never left pending.
     */
    private suspend fun enqueue(op: PendingSyncOp) {
        syncQueue.enqueue(op)
        // Trigger immediate sync - fire and forget
        ImmediateSyncManager.triggerSync()
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
                        qty = qty.toDouble(),
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
     * Result of trying to add an item.
     */
    sealed interface ItemAddResult {
        data class Success(val item: ItemEntity) : ItemAddResult
        data class DuplicateName(val existingName: String) : ItemAddResult
        data class DuplicateBarcode(val existingName: String, val barcode: String) : ItemAddResult
    }

    /**
     * Insert item into Room and return a copy with the generated id.
     * Checks for duplicate name (case-insensitive) and barcode before inserting.
     * Returns an ItemAddResult indicating success or duplicate detection.
     */
    suspend fun addItemReturning(item: ItemEntity): ItemAddResult {
        val cleanName = item.name.trim()
        val cleanBarcode = item.barcode?.trim()?.ifBlank { null }

        // Check for duplicate name (case-insensitive)
        val existingByName = itemDao.getItemByName(cleanName)
        if (existingByName != null && existingByName.id != item.id) {
            return ItemAddResult.DuplicateName(existingByName.name)
        }

        // Check for duplicate barcode (if provided)
        if (!cleanBarcode.isNullOrBlank()) {
            val existingByBarcode = itemDao.getItemByBarcode(cleanBarcode)
            if (existingByBarcode != null && existingByBarcode.id != item.id) {
                return ItemAddResult.DuplicateBarcode(existingByBarcode.name, cleanBarcode)
            }
        }

        val itemToInsert = item.copy(name = cleanName, barcode = cleanBarcode)
        val id = itemDao.insertItem(itemToInsert).toInt()
        runCatching {
            enqueue(
                PendingSyncOp(
                    entityType = SyncEntityType.ITEM,
                    entityId = id.toString(),
                    op = SyncOpType.UPSERT,
                    payload = JSONObject()
                        .put("id", id)
                        .put("name", cleanName)
                        .put("price", item.price)
                        .put("stock", item.stock)
                        .put("category", item.category)
                        .put("costPrice", item.costPrice)
                        .put("gstPercentage", item.gstPercentage)
                        .put("hsnCode", itemToInsert.hsnCode)
                        .put("reorderPoint", item.reorderPoint)
                        .put("vendorId", item.vendorId)
                        .put("rackLocation", item.rackLocation)
                        .put("barcode", cleanBarcode)
                        .put("imageUri", item.imageUri)
                        .put("expiryDateMillis", item.expiryDateMillis)
                        .put("batchSize", item.batchSize)
                )
            )
        }
        return ItemAddResult.Success(itemToInsert.copy(id = id))
    }

    /**
     * Legacy addItem that ignores duplicate results (for backward compatibility).
     */
    suspend fun addItem(item: ItemEntity) {
        addItemReturning(item)
    }
    
    suspend fun updateItem(item: ItemEntity): Int {
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
                        .put("hsnCode", item.hsnCode)
                        .put("reorderPoint", item.reorderPoint)
                        .put("vendorId", item.vendorId)
                        .put("rackLocation", item.rackLocation)
                        .put("barcode", item.barcode)
                        .put("imageUri", item.imageUri)
                        .put("expiryDateMillis", item.expiryDateMillis)
                        .put("batchSize", item.batchSize)
                )
            )
        }
        return id
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

    /**
     * Add a party only if no duplicate exists (by phone + type).
     * Returns the inserted ID, or null if a duplicate exists.
     */
    suspend fun addParty(party: PartyEntity): Int? {
        val cleanPhone = normalizePhoneDigits(party.phone)
        
        // Check for duplicate by phone + type
        val existing = partyDao.findPartyByPhoneAndType(cleanPhone, party.type)
        if (existing != null) return null // Duplicate exists
        
        val partyToInsert = party.copy(phone = cleanPhone)
        val id = partyDao.insertParty(partyToInsert).toInt()
        runCatching {
            enqueue(
                PendingSyncOp(
                    entityType = SyncEntityType.PARTY,
                    entityId = id.toString(),
                    op = SyncOpType.UPSERT,
                    payload = JSONObject()
                        .put("id", id)
                        .put("name", party.name)
                        .put("phone", cleanPhone)
                        .put("type", party.type)
                        .put("gstNumber", party.gstNumber)
                        .put("upiId", party.upiId)
                        .put("balance", party.balance)
                )
            )
        }
        return id
    }

    suspend fun addCustomer(name: String, phone: String): PartyEntity? {
        val cleanName = name.trim()
        val cleanPhone = normalizePhoneDigits(phone)
        if (cleanName.isBlank() || cleanPhone.isBlank()) return null
        
        // Check for duplicate by phone
        val existing = partyDao.findPartyByPhoneAndType(cleanPhone, "CUSTOMER")
        if (existing != null) return existing // Return existing instead of creating duplicate
        
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
        val cleanPhone = normalizePhoneDigits(phone)
        val cleanGst = gstNumber?.trim()?.ifBlank { null }
        if (cleanName.isBlank() || cleanPhone.isBlank()) return null
        
        // Check for duplicate by phone
        val existing = partyDao.findPartyByPhoneAndType(cleanPhone, "VENDOR")
        if (existing != null) return existing // Return existing instead of creating duplicate
        
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
                        .put("upiId", party.upiId)
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

    fun adjustmentsForTransaction(transactionId: Int): Flow<List<TransactionAdjustmentEntity>> {
        if (transactionId <= 0) return flowOf(emptyList())
        return adjustmentDao.adjustmentsForTransaction(transactionId)
    }

    fun editHistoryForTransaction(transactionId: Int): Flow<List<TransactionEditHistoryEntity>> {
        if (transactionId <= 0) return flowOf(emptyList())
        return editHistoryDao.historyForTransaction(transactionId)
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

    // --- Controlled transaction edit ---
    sealed interface EditEligibility {
        data object Allowed : EditEligibility
        data class Blocked(val reason: String) : EditEligibility
    }

    data class TransactionLineEdit(
        val lineId: Int,
        val newQty: Double? = null,
        val newUnitPrice: Double? = null
    )

    data class TransactionEditChanges(
        val lineEdits: List<TransactionLineEdit> = emptyList(),
        val paymentMode: String? = null,
        val note: String? = null
    )

    sealed interface EditResult {
        data class Success(val txId: Int) : EditResult
        data class NotFound(val txId: Int) : EditResult
        data class NotAllowed(val reason: String) : EditResult
        data class StockConflict(val offendingItemIds: Set<Int>) : EditResult
        data class InvalidInput(val reason: String) : EditResult
    }

    data class ItemAdjustment(
        val itemId: Int?,
        val itemNameSnapshot: String,
        val quantityDelta: Double,
        val priceDelta: Double = 0.0,
        val taxDelta: Double = 0.0
    )

    sealed interface AdjustmentResult {
        data class Success(val adjustmentId: Int) : AdjustmentResult
        data class NotFound(val txId: Int) : AdjustmentResult
        data class NotAllowed(val reason: String) : AdjustmentResult
        data class StockConflict(val offendingItemIds: Set<Int>) : AdjustmentResult
        data class InvalidInput(val reason: String) : AdjustmentResult
    }

    sealed interface VoidResult {
        data class Success(val txId: Int) : VoidResult
        data class NotFound(val txId: Int) : VoidResult
        data class NotAllowed(val reason: String) : VoidResult
    }

    fun canEditTransaction(tx: TransactionEntity, userRole: String): EditEligibility {
        val role = userRole.trim().uppercase()
        val status = tx.status.trim().uppercase()

        // GST-filed transactions: no direct edit, only adjustments.
        if (!tx.gstFiledPeriod.isNullOrBlank()) {
            return when (status) {
                "FINALIZED" -> EditEligibility.Blocked("GST filed. Create adjustment (Credit/Debit Note).")
                else -> EditEligibility.Blocked("GST filed. Transaction is locked.")
            }
        }

        return when (status) {
            "DRAFT" -> when (role) {
                "OWNER", "MANAGER", "CASHIER" -> EditEligibility.Allowed
                else -> EditEligibility.Blocked("Role not allowed")
            }

            "POSTED" -> when (role) {
                "OWNER", "MANAGER" -> EditEligibility.Allowed
                "CASHIER" -> EditEligibility.Blocked("Cashier can edit Draft only")
                else -> EditEligibility.Blocked("Role not allowed")
            }

            "FINALIZED" -> EditEligibility.Blocked("Finalized. Create adjustment.")
            "ADJUSTED" -> EditEligibility.Blocked("Adjusted. Locked.")
            "VOIDED" -> EditEligibility.Blocked("Voided. Locked.")
            else -> EditEligibility.Blocked("Unknown status")
        }
    }

    private fun updateTitleWithNote(originalTitle: String, note: String?): String {
        val cleanNote = note?.trim()?.ifBlank { null } ?: return originalTitle
        // Minimal-change: append note once, don't try to parse existing formats.
        // Avoid unbounded growth by replacing a trailing " • Note: ..." style if present.
        val marker = " • Note: "
        val base = originalTitle.substringBefore(marker)
        return base + marker + cleanNote
    }

    private fun computeGstAmounts(taxableValue: Double, gstRate: Double): Triple<Double, Double, Double> {
        if (gstRate <= 0.0 || taxableValue <= 0.0) return Triple(0.0, 0.0, 0.0)
        // Default to intra-state split for now (CGST/SGST), keep IGST at 0.
        val gstTotal = taxableValue * (gstRate / 100.0)
        val half = gstTotal / 2.0
        return Triple(half, half, 0.0)
    }

    private fun requireWholeNumberPcs(qty: Double): Int? {
        if (qty.isNaN() || qty.isInfinite()) return null
        val r = qty.roundToInt()
        return if (abs(qty - r.toDouble()) <= 1e-6) r else null
    }

    private suspend fun logEdit(
        txId: Int,
        field: String,
        oldValue: String?,
        newValue: String?,
        userId: Int?,
        reason: String
    ) {
        editHistoryDao.insert(
            TransactionEditHistoryEntity(
                transactionId = txId,
                fieldChanged = field,
                oldValue = oldValue,
                newValue = newValue,
                userId = userId,
                reason = reason,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun logStockMovement(
        itemId: Int,
        delta: Double,
        source: String,
        txId: Int?,
        adjustmentId: Int?,
        userId: Int?,
        reason: String?
    ) {
        stockMovementDao.insertMovement(
            StockMovementEntity(
                itemId = itemId,
                delta = delta,
                source = source,
                transactionId = txId,
                adjustmentId = adjustmentId,
                userId = userId,
                reason = reason,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Direct edit for DRAFT/POSTED:
     * - Updates allowed fields only (qty/price/paymentMode/title-note)
     * - Recomputes tx.amount from line-items
     * - Applies stock delta only for SALE (inventory integrity)
     * - Appends edit history + stock movements
     */
    suspend fun editTransaction(
        txId: Int,
        changes: TransactionEditChanges,
        reason: String,
        userId: Int?
    ): EditResult {
        val cleanReason = reason.trim()
        if (cleanReason.isBlank()) return EditResult.InvalidInput("Reason is required")

        val bundle = transactionDao.getTransactionWithItems(txId) ?: return EditResult.NotFound(txId)
        val tx = bundle.tx

        // Eligibility (direct edit only for DRAFT/POSTED)
        val role = runCatching { userId?.let { userDao.getById(it)?.role } }.getOrNull() ?: "OWNER"
        when (val elig = canEditTransaction(tx, role)) {
            is EditEligibility.Blocked -> return EditResult.NotAllowed(elig.reason)
            EditEligibility.Allowed -> Unit
        }
        if (tx.status.uppercase() !in setOf("DRAFT", "POSTED")) {
            return EditResult.NotAllowed("Direct edit allowed only for Draft/Posted")
        }

        val byId = bundle.items.associateBy { it.id }
        val edits = changes.lineEdits.filter { it.lineId > 0 }.distinctBy { it.lineId }

        val offending = mutableSetOf<Int>()
        val now = System.currentTimeMillis()
        var committedAmount: Double = tx.amount
        var committedPaymentMode: String = tx.paymentMode
        var committedTitle: String = tx.title

        runCatching {
            db.withTransaction {
                // 1) Apply line edits + stock deltas (SALE only)
                for (e in edits) {
                    val line = byId[e.lineId] ?: continue
                    val oldQty = line.qty
                    val oldPrice = line.price
                    val newQty = (e.newQty ?: oldQty).coerceAtLeast(0.0)
                    val newPrice = (e.newUnitPrice ?: oldPrice).coerceAtLeast(0.0)

                    // Stock delta: new_qty - old_qty (for SALE, delta > 0 means more items sold => deduct more stock)
                    if (tx.type == "SALE" && line.itemId != null) {
                        val delta = newQty - oldQty
                        if (delta > 0.0) {
                            val ok = if (line.unit.uppercase() == "KG") {
                                itemDao.decreaseStockKgSafe(line.itemId, delta) > 0
                            } else {
                                val pcs = requireWholeNumberPcs(delta) ?: throw IllegalArgumentException("Quantity must be whole number for PCS")
                                itemDao.decreaseStockSafe(line.itemId, pcs) > 0
                            }
                            if (!ok) offending += line.itemId
                        } else if (delta < 0.0) {
                            // Returning stock
                            val add = -delta
                            if (line.unit.uppercase() == "KG") itemDao.increaseStockKg(line.itemId, add)
                            else {
                                val pcs = requireWholeNumberPcs(add) ?: throw IllegalArgumentException("Quantity must be whole number for PCS")
                                itemDao.increaseStock(line.itemId, pcs)
                            }
                        }
                    }
                }

                if (offending.isNotEmpty()) {
                    // Abort the transaction (no partial stock adjustment, no edits).
                    throw StockConflictException(offending)
                }

                // 2) Persist updated line values + recompute tx total
                val newLines = bundle.items.map { li ->
                    val e = edits.firstOrNull { it.lineId == li.id }
                    if (e == null) li else li.copy(
                        qty = (e.newQty ?: li.qty).coerceAtLeast(0.0),
                        price = (e.newUnitPrice ?: li.price).coerceAtLeast(0.0)
                    )
                }

                newLines.forEach { li ->
                    val taxable = li.price * li.qty
                    val (cgst, sgst, igst) = computeGstAmounts(taxable, li.gstRate)
                    transactionDao.updateTransactionLineEditableFields(
                        lineId = li.id,
                        qty = li.qty,
                        price = li.price,
                        taxableValue = taxable,
                        cgstAmount = cgst,
                        sgstAmount = sgst,
                        igstAmount = igst
                    )
                }

                val newAmount = newLines.sumOf { it.price * it.qty }
                val newPaymentMode = changes.paymentMode?.trim()?.ifBlank { tx.paymentMode } ?: tx.paymentMode
                val newTitle = updateTitleWithNote(tx.title, changes.note)
                committedAmount = newAmount
                committedPaymentMode = newPaymentMode
                committedTitle = newTitle
                transactionDao.updateTransactionFields(
                    id = tx.id,
                    paymentMode = newPaymentMode,
                    title = newTitle,
                    amount = newAmount,
                    gstFiledPeriod = tx.gstFiledPeriod,
                    updatedAt = now
                )

                // 3) Append audit history + stock movements
                if (newPaymentMode != tx.paymentMode) {
                    logEdit(tx.id, "paymentMode", tx.paymentMode, newPaymentMode, userId, cleanReason)
                }
                if (newTitle != tx.title) {
                    logEdit(tx.id, "note", tx.title, newTitle, userId, cleanReason)
                }

                for (e in edits) {
                    val line = byId[e.lineId] ?: continue
                    val oldQty = line.qty
                    val oldPrice = line.price
                    val newQty = (e.newQty ?: oldQty).coerceAtLeast(0.0)
                    val newPrice = (e.newUnitPrice ?: oldPrice).coerceAtLeast(0.0)
                    if (newQty != oldQty) logEdit(tx.id, "qty(lineId=${line.id})", oldQty.toString(), newQty.toString(), userId, cleanReason)
                    if (newPrice != oldPrice) logEdit(tx.id, "price(lineId=${line.id})", oldPrice.toString(), newPrice.toString(), userId, cleanReason)

                    if (tx.type == "SALE" && line.itemId != null) {
                        val delta = newQty - oldQty
                        if (delta != 0.0) {
                            // Stock movement delta: +ve means increase stock, -ve means decrease stock
                            val stockDelta = -delta // invert: sale consumes stock
                            logStockMovement(
                                itemId = line.itemId,
                                delta = stockDelta,
                                source = "EDIT",
                                txId = tx.id,
                                adjustmentId = null,
                                userId = userId,
                                reason = cleanReason
                            )
                        }
                    }
                }
            }
        }.getOrElse { e ->
            val ids = (e as? StockConflictException)?.offending
            if (!ids.isNullOrEmpty()) return EditResult.StockConflict(ids)
            val msg = e.message?.ifBlank { null } ?: "Edit failed"
            return EditResult.InvalidInput(msg)
        }

        // Offline-first: enqueue sync op (best-effort).
        runCatching {
            val payload = JSONObject()
                .put("localId", txId)
                .put("type", tx.type)
                .put("paymentMode", committedPaymentMode)
                .put("amount", committedAmount)
                .put("title", committedTitle)
                .put("status", tx.status)
                .put("gstFiledPeriod", tx.gstFiledPeriod)
                .put("updatedAt", now)
            enqueue(PendingSyncOp(SyncEntityType.TRANSACTION, txId.toString(), SyncOpType.EDIT_TRANSACTION, payload))
        }

        return EditResult.Success(txId)
    }

    /**
     * FINALIZED => create delta-only adjustment record (original remains unchanged).
     */
    suspend fun createAdjustment(
        originalTxId: Int,
        itemChanges: List<ItemAdjustment>,
        reason: String,
        userId: Int?
    ): AdjustmentResult {
        val cleanReason = reason.trim()
        if (cleanReason.isBlank()) return AdjustmentResult.InvalidInput("Reason is required")
        val bundle = transactionDao.getTransactionWithItems(originalTxId) ?: return AdjustmentResult.NotFound(originalTxId)
        val tx = bundle.tx

        // GST-filed => adjustment only (CN/DN), but still must be FINALIZED per spec.
        if (tx.status.uppercase() != "FINALIZED") {
            return AdjustmentResult.NotAllowed("Adjustment allowed only for Finalized")
        }

        val normalized = itemChanges.filter { it.quantityDelta != 0.0 || it.priceDelta != 0.0 || it.taxDelta != 0.0 }
        if (normalized.isEmpty()) return AdjustmentResult.InvalidInput("No changes provided")

        val netAmountChange = normalized.sumOf { (it.priceDelta * it.quantityDelta) }
        val gstType = when {
            netAmountChange < 0.0 -> "CREDIT_NOTE"
            netAmountChange > 0.0 -> "DEBIT_NOTE"
            else -> null
        }

        val offending = mutableSetOf<Int>()
        val now = System.currentTimeMillis()

        val adjId = runCatching {
            db.withTransaction {
                val adjRowId = adjustmentDao.insertAdjustment(
                    TransactionAdjustmentEntity(
                        originalTransactionId = tx.id,
                        adjustmentType = "EDIT",
                        reason = cleanReason,
                        userId = userId,
                        createdAt = now,
                        netAmountChange = netAmountChange,
                        gstType = gstType
                    )
                ).toInt()

                val items = normalized.map { ch ->
                    TransactionAdjustmentItemEntity(
                        adjustmentId = adjRowId,
                        itemId = ch.itemId,
                        itemNameSnapshot = ch.itemNameSnapshot,
                        quantityDelta = ch.quantityDelta,
                        priceDelta = ch.priceDelta,
                        taxDelta = ch.taxDelta
                    )
                }
                adjustmentDao.insertAdjustmentItems(items)

                // Inventory integrity: apply stock movements only for SALE and itemId != null
                if (tx.type == "SALE") {
                    normalized.forEach { ch ->
                        val itemId = ch.itemId ?: return@forEach
                        val delta = ch.quantityDelta
                        val isLoose = itemDao.getItemById(itemId)?.isLoose == true
                        if (delta > 0.0) {
                            // More sold => deduct stock
                            val ok = if (isLoose) {
                                itemDao.decreaseStockKgSafe(itemId, delta) > 0
                            } else {
                                val pcs = requireWholeNumberPcs(delta) ?: throw IllegalArgumentException("Invalid PCS delta")
                                itemDao.decreaseStockSafe(itemId, pcs) > 0
                            }
                            if (!ok) offending += itemId
                            else logStockMovement(itemId, -delta, "ADJUSTMENT", tx.id, adjRowId, userId, cleanReason)
                        } else if (delta < 0.0) {
                            // Less sold => restore stock
                            val add = -delta
                            if (isLoose) itemDao.increaseStockKg(itemId, add)
                            else {
                                val pcs = requireWholeNumberPcs(add) ?: throw IllegalArgumentException("Invalid PCS delta")
                                itemDao.increaseStock(itemId, pcs)
                            }
                            logStockMovement(itemId, add, "ADJUSTMENT", tx.id, adjRowId, userId, cleanReason)
                        }
                    }
                }

                if (offending.isNotEmpty()) throw StockConflictException(offending)

                // Mark original as ADJUSTED (locked) per status table.
                transactionDao.updateTransactionStatus(tx.id, "ADJUSTED", now)

                adjRowId
            }
        }.getOrElse { e ->
            val ids = (e as? StockConflictException)?.offending ?: emptySet()
            if (ids.isNotEmpty()) return AdjustmentResult.StockConflict(ids)
            val msg = e.message?.ifBlank { null } ?: "Adjustment failed"
            return AdjustmentResult.InvalidInput(msg)
        }

        // Audit: record adjustment creation
        runCatching { logEdit(tx.id, "adjustment", null, "created(id=$adjId)", userId, cleanReason) }

        // Offline-first: enqueue sync op (best-effort).
        runCatching {
            val payload = JSONObject()
                .put("localId", originalTxId)
                .put("type", tx.type)
                .put("op", "CREATE_ADJUSTMENT")
                .put("reason", cleanReason)
                .put("updatedAt", now)
            enqueue(PendingSyncOp(SyncEntityType.TRANSACTION, originalTxId.toString(), SyncOpType.CREATE_ADJUSTMENT, payload))
        }

        return AdjustmentResult.Success(adjId)
    }

    suspend fun finalizeTransaction(txId: Int, userId: Int?): Boolean {
        val bundle = transactionDao.getTransactionWithItems(txId) ?: return false
        val tx = bundle.tx
        if (tx.status.uppercase() !in setOf("POSTED", "DRAFT")) return false
        val now = System.currentTimeMillis()
        db.withTransaction {
            transactionDao.updateTransactionStatus(tx.id, "FINALIZED", now)
            logEdit(tx.id, "status", tx.status, "FINALIZED", userId, "Finalized")
        }
        runCatching {
            val payload = JSONObject()
                .put("localId", txId)
                .put("type", tx.type)
                .put("status", "FINALIZED")
                .put("updatedAt", now)
            enqueue(PendingSyncOp(SyncEntityType.TRANSACTION, txId.toString(), SyncOpType.FINALIZE_TRANSACTION, payload))
        }
        return true
    }

    suspend fun voidTransaction(txId: Int, reason: String, userId: Int?): VoidResult {
        val cleanReason = reason.trim()
        if (cleanReason.isBlank()) return VoidResult.NotAllowed("Reason is required")
        val bundle = transactionDao.getTransactionWithItems(txId) ?: return VoidResult.NotFound(txId)
        val tx = bundle.tx
        if (tx.status.uppercase() in setOf("VOIDED", "ADJUSTED")) return VoidResult.NotAllowed("Transaction locked")

        val now = System.currentTimeMillis()
        db.withTransaction {
            // Restore stock for SALE when voiding
            if (tx.type == "SALE") {
                bundle.items.forEach { li ->
                    val itemId = li.itemId ?: return@forEach
                    if (li.unit.uppercase() == "KG") itemDao.increaseStockKg(itemId, li.qty)
                    else {
                        val pcs = requireWholeNumberPcs(li.qty) ?: return@forEach
                        itemDao.increaseStock(itemId, pcs)
                    }
                    logStockMovement(itemId, li.qty, "VOID", tx.id, null, userId, cleanReason)
                }
            }
            transactionDao.updateTransactionStatus(tx.id, "VOIDED", now)
            logEdit(tx.id, "status", tx.status, "VOIDED", userId, cleanReason)
        }

        runCatching {
            val payload = JSONObject()
                .put("localId", txId)
                .put("type", tx.type)
                .put("status", "VOIDED")
                .put("reason", cleanReason)
                .put("updatedAt", now)
            enqueue(PendingSyncOp(SyncEntityType.TRANSACTION, txId.toString(), SyncOpType.VOID_TRANSACTION, payload))
        }

        return VoidResult.Success(txId)
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
        val now = System.currentTimeMillis()
        reminderDao.markDoneWithTimestamp(id, now)
        runCatching {
            enqueue(PendingSyncOp(SyncEntityType.REMINDER, id.toString(), SyncOpType.MARK_DONE, JSONObject().put("id", id)))
        }
    }

    /**
     * Permanently dismiss a completed reminder (remove it from the list).
     */
    suspend fun dismissReminder(id: Int) {
        reminderDao.deleteReminder(id)
        runCatching {
            enqueue(PendingSyncOp(SyncEntityType.REMINDER, id.toString(), SyncOpType.DELETE, JSONObject().put("id", id)))
        }
    }

    /**
     * Clean up old completed reminders (called periodically or on app start).
     */
    suspend fun cleanupOldCompletedReminders() {
        val cutoffMillis = System.currentTimeMillis() - (24L * 60L * 60L * 1000L)
        reminderDao.deleteOldCompletedReminders(cutoffMillis)
    }

    suspend fun deleteReminder(id: Int) {
        reminderDao.deleteReminder(id)
        runCatching {
            enqueue(PendingSyncOp(SyncEntityType.REMINDER, id.toString(), SyncOpType.DELETE, JSONObject().put("id", id)))
        }
    }

    // Process Sale/Checkout
    sealed interface SaleResult {
        data class Success(val txId: Int) : SaleResult
        data class StockConflict(val offendingItemIds: Set<Int>) : SaleResult
    }

    private class StockConflictException(val offending: Set<Int>) : RuntimeException()

    suspend fun processSale(
        items: List<Pair<ItemEntity, Double>>, // Item, Qty (PCS as whole numbers; loose items in KG)
        paymentMode: String,
        customerId: Int?,
        totalAmount: Double
    ): SaleResult {
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
                unit = if (item.isLoose) "KG" else "PCS",
                price = item.price
            )
        }

        val txId = runCatching {
            db.withTransaction {
                val offending = mutableSetOf<Int>()

                // 1) Attempt atomic stock deductions (all-or-nothing within the transaction).
                items.forEach { (item, qty) ->
                    val ok = if (item.isLoose) {
                        itemDao.decreaseStockKgSafe(item.id, qty) > 0
                    } else {
                        val q = qty.toInt()
                        itemDao.decreaseStockSafe(item.id, q) > 0
                    }
                    if (!ok) offending += item.id
                }

                if (offending.isNotEmpty()) {
                    // Abort the transaction (no partial stock deduction, no sale insert).
                    throw StockConflictException(offending)
                }

                // 2) Persist sale + line items only after stock is safely deducted.
                val id = transactionDao.insertSale(transaction, txItems)
                if (customerId != null && paymentMode == "CREDIT") {
                    partyDao.updateBalance(customerId, totalAmount)
                }
                id
            }
        }.getOrElse { e ->
            val ids = (e as? StockConflictException)?.offending ?: emptySet()
            return SaleResult.StockConflict(ids)
        }

        runCatching {
            val lines = JSONArray().apply {
                items.forEach { (it, qty) ->
                    put(
                        JSONObject()
                            .put("itemId", it.id)
                            .put("name", it.name)
                            .put("qty", qty)
                            .put("unit", if (it.isLoose) "KG" else "PCS")
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

        return SaleResult.Success(txId)
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
                val result = addItemReturning(
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
                if (result is ItemAddResult.Success) {
                    added++
                }
                // If duplicate, skip silently (already exists in inventory)
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
                val result = addItemReturning(
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
                if (result is ItemAddResult.Success) {
                    added++
                }
                // If duplicate, skip silently (already exists in inventory)
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
        val qty: Double,
        val unit: String,
        val unitPrice: Double,
        val lineTotal: Double
    )

    private suspend fun BillOcrParser.ParsedBillItem.toPurchaseLineOrNull(): PurchaseLine? {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return null
        val qtyNum = (qtyRaw ?: qty.toDouble())
        if (qtyNum <= 0.0) return null

        val unitRaw = unit?.trim()?.uppercase()
        val isWeight = unitRaw in setOf("KG", "KGS", "G", "GM", "GMS", "GRAM", "GRAMS")
        val unitNorm = if (isWeight) "KG" else "PCS"
        val qtyNorm = if (isWeight) {
            when (unitRaw) {
                "KG", "KGS" -> qtyNum
                else -> qtyNum / 1000.0 // grams -> kg
            }
        } else {
            qtyNum
        }
        if (qtyNorm <= 0.0) return null

        val unitPriceNorm = unitPrice ?: run {
            val t = total
            if (t != null && qtyNorm > 0.0) t / qtyNorm else null
        } ?: return null

        val lineTotal = total ?: (unitPriceNorm * qtyNorm)
        if (lineTotal <= 0.0) return null

        val itemId = runCatching { itemDao.getItemByName(cleanName)?.id }.getOrNull()
        return PurchaseLine(
            itemId = itemId,
            name = cleanName,
            qty = qtyNorm,
            unit = unitNorm,
            unitPrice = unitPriceNorm,
            lineTotal = lineTotal
        )
    }
}
