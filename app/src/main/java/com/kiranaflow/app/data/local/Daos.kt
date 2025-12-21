package com.kiranaflow.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllItems(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE barcode = :barcode AND isDeleted = 0 LIMIT 1")
    suspend fun getItemByBarcode(barcode: String): ItemEntity?

    @Query("SELECT * FROM items WHERE LOWER(name) = LOWER(:name) AND isDeleted = 0 LIMIT 1")
    suspend fun getItemByName(name: String): ItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity): Long

    @Query("UPDATE items SET hsnCode = :hsnCode WHERE id = :itemId")
    suspend fun updateHsnCode(itemId: Int, hsnCode: String?)

    @Query("UPDATE items SET isDeleted = 1 WHERE id = :itemId")
    suspend fun softDelete(itemId: Int)

    @Query("UPDATE items SET isDeleted = 1 WHERE id IN (:itemIds)")
    suspend fun softDeleteMany(itemIds: List<Int>)

    // Using Int ID
    @Query("UPDATE items SET stock = stock - :qty WHERE id = :itemId")
    suspend fun decreaseStock(itemId: Int, qty: Int)
    
    // Using Int ID
    @Query("UPDATE items SET stock = stock + :qty WHERE id = :itemId")
    suspend fun increaseStock(itemId: Int, qty: Int)

    @Query("UPDATE items SET stockKg = stockKg - :qtyKg WHERE id = :itemId")
    suspend fun decreaseStockKg(itemId: Int, qtyKg: Double)

    @Query("UPDATE items SET stockKg = stockKg + :qtyKg WHERE id = :itemId")
    suspend fun increaseStockKg(itemId: Int, qtyKg: Double)
}

@Dao
interface PartyDao {
    @Query("SELECT * FROM parties WHERE type = 'VENDOR' ORDER BY name ASC")
    fun getVendors(): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties WHERE type = 'CUSTOMER' ORDER BY name ASC")
    fun getCustomers(): Flow<List<PartyEntity>>
    
    @Query("SELECT * FROM parties ORDER BY name ASC")
    fun getAllParties(): Flow<List<PartyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParty(party: PartyEntity): Long

    @Query("SELECT * FROM parties WHERE type = 'CUSTOMER' AND phone = :phone ORDER BY id DESC LIMIT 1")
    suspend fun findCustomerByPhone(phone: String): PartyEntity?

    @Query("SELECT * FROM parties WHERE type = :type AND phone = :phone ORDER BY id DESC LIMIT 1")
    suspend fun findPartyByPhoneAndType(phone: String, type: String): PartyEntity?

    // Use delete by object or ID? Entity has ID.
    @Delete
    suspend fun deleteParty(party: PartyEntity)

    @Query("DELETE FROM parties WHERE id IN (:partyIds)")
    suspend fun deletePartiesByIds(partyIds: List<Int>)

    // Using Int ID now
    @Query("UPDATE parties SET balance = balance + :amount WHERE id = :partyId")
    suspend fun updateBalance(partyId: Int, amount: Double)

    @Query("SELECT * FROM parties WHERE id = :id LIMIT 1")
    fun getPartyById(id: Int): Flow<PartyEntity?>
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    /**
     * Flat, join-based rows for GST exports. One row per line item.
     *
     * NOTE: toMillis is exclusive (recommended: startOfNextPeriod).
     */
    @Query(
        """
        SELECT
            t.id AS txId,
            t.title AS txTitle,
            t.amount AS txAmount,
            t.date AS txDate,
            t.customerId AS customerId,
            p.name AS customerName,
            p.gstNumber AS customerGstin,
            p.stateCode AS customerStateCode,
            ti.id AS itemLineId,
            ti.itemId AS itemId,
            ti.itemNameSnapshot AS itemNameSnapshot,
            ti.qty AS qty,
            ti.unit AS unit,
            ti.price AS price,
            ti.hsnCodeSnapshot AS hsnCodeSnapshot,
            ti.gstRate AS gstRateSnapshot,
            ti.taxableValue AS taxableValueSnapshot,
            ti.cgstAmount AS cgstAmountSnapshot,
            ti.sgstAmount AS sgstAmountSnapshot,
            ti.igstAmount AS igstAmountSnapshot,
            i.hsnCode AS itemHsnCode,
            i.gstPercentage AS itemGstPercentage
        FROM transactions t
        INNER JOIN transaction_items ti ON ti.transactionId = t.id
        LEFT JOIN parties p ON p.id = t.customerId
        LEFT JOIN items i ON i.id = ti.itemId
        WHERE t.type = 'SALE'
          AND t.date >= :fromMillis
          AND t.date < :toMillis
        ORDER BY t.date ASC, t.id ASC, ti.id ASC
        """
    )
    suspend fun getSaleLinesForPeriod(fromMillis: Long, toMillis: Long): List<GstSaleLineRow>

    // --- GST review persistence helpers (best-effort) ---
    @Query("UPDATE transaction_items SET hsnCodeSnapshot = :hsn WHERE id = :lineId")
    suspend fun updateLineHsnSnapshot(lineId: Int, hsn: String?)

    @Query("UPDATE transaction_items SET gstRate = :gstRate WHERE id = :lineId")
    suspend fun updateLineGstRate(lineId: Int, gstRate: Double)

    @Query("UPDATE transaction_items SET taxableValue = :taxableValue WHERE id = :lineId")
    suspend fun updateLineTaxableValue(lineId: Int, taxableValue: Double)

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    fun getTransactionById(id: Int): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE customerId = :customerId OR vendorId = :customerId ORDER BY date DESC")
    fun getTransactionsForParty(customerId: Int): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionItems(items: List<TransactionItemEntity>)

    @Query("SELECT * FROM transaction_items")
    fun getAllTransactionItems(): Flow<List<TransactionItemEntity>>

    @Query("SELECT * FROM transaction_items WHERE transactionId = :transactionId")
    fun getItemsForTransaction(transactionId: Int): Flow<List<TransactionItemEntity>>

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteTransactionsByIds(ids: List<Int>)
    
    @Transaction
    suspend fun insertSale(transaction: TransactionEntity, items: List<TransactionItemEntity>): Int {
        val txId = insertTransaction(transaction)
        // We need to set the transactionId on items
        val itemsWithId = items.map { it.copy(transactionId = txId.toInt()) }
        insertTransactionItems(itemsWithId)
        return txId.toInt()
    }

    /**
     * Generic helper to insert a transaction with associated line-items (works for SALE/EXPENSE/etc).
     */
    @Transaction
    suspend fun insertTransactionWithItems(transaction: TransactionEntity, items: List<TransactionItemEntity>): Int {
        val txId = insertTransaction(transaction)
        val itemsWithId = items.map { it.copy(transactionId = txId.toInt()) }
        insertTransactionItems(itemsWithId)
        return txId.toInt()
    }
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE isDone = 0 ORDER BY dueAt ASC")
    fun getActiveReminders(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity)

    @Query("UPDATE reminders SET isDone = 1 WHERE id = :id")
    suspend fun markDone(id: Int)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: Int)
}

@Dao
interface OutboxDao {
    // "Unsynced" = anything not DONE (PENDING or FAILED).
    @Query("SELECT COUNT(*) FROM outbox WHERE status != 'DONE'")
    fun pendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'FAILED'")
    fun failedCount(): Flow<Int>

    @Query("SELECT * FROM outbox ORDER BY createdAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<OutboxEntity>>

    @Query(
        "SELECT DISTINCT CAST(entityId AS INTEGER) " +
            "FROM outbox " +
            "WHERE status != 'DONE' AND entityType = :entityType AND entityId IS NOT NULL"
    )
    fun observeUnsyncedIds(entityType: String): Flow<List<Int>>

    @Query("SELECT * FROM outbox WHERE status != 'DONE' ORDER BY createdAtMillis ASC LIMIT :limit")
    suspend fun getPending(limit: Int = 50): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY createdAtMillis ASC LIMIT :limit")
    suspend fun getPendingOnly(limit: Int = 50): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE status = 'FAILED' ORDER BY createdAtMillis ASC LIMIT :limit")
    suspend fun getFailed(limit: Int = 50): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): OutboxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: OutboxEntity)

    @Query("UPDATE outbox SET status = 'DONE', lastAttemptAtMillis = :atMillis, error = NULL WHERE id = :id")
    suspend fun markDone(id: Int, atMillis: Long)

    @Query("UPDATE outbox SET status = 'FAILED', lastAttemptAtMillis = :atMillis, error = :error WHERE id = :id")
    suspend fun markFailed(id: Int, atMillis: Long, error: String)

    @Query("UPDATE outbox SET status = 'PENDING', lastAttemptAtMillis = NULL, error = NULL WHERE status = 'FAILED'")
    suspend fun resetAllFailedToPending()

    @Query("UPDATE outbox SET status = 'PENDING', lastAttemptAtMillis = NULL, error = NULL WHERE id = :id AND status = 'FAILED'")
    suspend fun resetFailedToPending(id: Int)

    @Query("UPDATE outbox SET lastAttemptAtMillis = :atMillis, error = :error WHERE id = :id")
    suspend fun markAttempt(id: Int, atMillis: Long, error: String?)

    @Query("UPDATE outbox SET status = 'DONE', lastAttemptAtMillis = :atMillis, error = NULL WHERE status != 'DONE'")
    suspend fun markAllUnsyncedDone(atMillis: Long)

    @Query("DELETE FROM outbox WHERE status = 'DONE'")
    suspend fun clearDone()

    @Query("DELETE FROM outbox")
    suspend fun clearAll()
}
