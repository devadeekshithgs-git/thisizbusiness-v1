package com.kiranaflow.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

// Using Int for all IDs for consistency and proper Room relations.

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val price: Double, // Renamed from sellingPrice
    val stock: Int,
    val category: String,
    val rackLocation: String?, // Renamed from location
    val marginPercentage: Double,
    val barcode: String?,
    val costPrice: Double,
    val gstPercentage: Double?,
    val reorderPoint: Int,
    val vendorId: Int?,
    val imageUri: String? = null,
    val expiryDateMillis: Long? = null, // Optional expiry date for alerts
    var isDeleted: Boolean = false // For soft delete
)

@Entity(tableName = "parties")
data class PartyEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, // Changed to Int
    val name: String,
    val phone: String,
    val type: String, // "CUSTOMER" or "VENDOR"
    val gstNumber: String? = null,
    val balance: Double = 0.0,
    val openingDue: Double = 0.0
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val type: String, // "SALE", "EXPENSE", "INCOME"
    val amount: Double,
    val date: Long, // Use timestamp for sorting
    val time: String, // Keep formatted time for display
    val customerId: Int?,
    val vendorId: Int?,
    val paymentMode: String,
    // Optional receipt image captured for expenses/purchases (local URI).
    val receiptImageUri: String? = null
)

@Entity(
    tableName = "transaction_items",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.SET_NULL // Keep history even if item is deleted
        )
    ],
    indices = [Index("transactionId"), Index("itemId")]
)
data class TransactionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val transactionId: Int,
    val itemId: Int?,
    val itemNameSnapshot: String, // Snapshot in case item is deleted/changed
    val qty: Int,
    val price: Double // Selling price at time of sale
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val type: String, // "ITEM" | "VENDOR" | "GENERAL"
    val refId: Int?, // itemId/vendorId where relevant
    val dueAt: Long,
    val note: String? = null,
    val isDone: Boolean = false
)

/**
 * Milestone D groundwork: offline-first outbox of local changes to be synced to cloud later.
 * This stays completely local until a remote sync engine is configured.
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val opId: String = UUID.randomUUID().toString(),
    val entityType: String, // e.g. ITEM | PARTY | TRANSACTION | TRANSACTION_ITEM | REMINDER
    val entityId: String?,  // string to support both int IDs + future server IDs
    val op: String,         // e.g. UPSERT | DELETE
    val payloadJson: String?,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val lastAttemptAtMillis: Long? = null,
    val status: String = "PENDING", // PENDING | DONE | FAILED
    val error: String? = null
)
