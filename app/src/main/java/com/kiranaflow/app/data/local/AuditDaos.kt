package com.kiranaflow.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAOs for v17 audit/adjustment tables.
 *
 * These are intentionally minimal: they exist to support Room compilation and
 * future feature work without impacting current app behavior.
 */

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY id DESC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity): Long
}

@Dao
interface TransactionAdjustmentDao {
    @Query("SELECT * FROM transaction_adjustments WHERE originalTransactionId = :txId ORDER BY createdAt DESC")
    fun adjustmentsForTransaction(txId: Int): Flow<List<TransactionAdjustmentEntity>>

    // Naming matches repository usage.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdjustment(adjustment: TransactionAdjustmentEntity): Long

    // Naming matches repository usage.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdjustmentItems(items: List<TransactionAdjustmentItemEntity>)
}

@Dao
interface StockMovementDao {
    @Query("SELECT * FROM stock_movements WHERE itemId = :itemId ORDER BY createdAt DESC")
    fun movementsForItem(itemId: Int): Flow<List<StockMovementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: StockMovementEntity): Long
}

@Dao
interface TransactionEditHistoryDao {
    @Query("SELECT * FROM transaction_edit_history WHERE transactionId = :txId ORDER BY createdAt DESC")
    fun historyForTransaction(txId: Int): Flow<List<TransactionEditHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TransactionEditHistoryEntity): Long
}


