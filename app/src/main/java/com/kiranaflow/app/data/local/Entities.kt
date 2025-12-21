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
    // Loose items (sold by weight): qty is interpreted in GRAMS in billing/transactions.
    val isLoose: Boolean = false,
    val pricePerKg: Double = 0.0,
    // For loose items only: stock is stored in KG (can be fractional).
    // For non-loose items, this field is currently unused (kept for forward-compat).
    val stockKg: Double = 0.0,
    val stock: Int,
    val category: String,
    val rackLocation: String?, // Renamed from location
    val marginPercentage: Double,
    val barcode: String?,
    val costPrice: Double,
    val gstPercentage: Double?,
    // GST reporting: optional HSN/SAC (4-8 digits). Can be filled later from GST Reports review.
    val hsnCode: String? = null,
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
    // GST reporting: 2-digit state code (e.g., 29 for Karnataka). Optional.
    val stateCode: Int? = null,
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
    val unit: String = "PCS", // PCS | GRAM
    val price: Double, // Selling price at time of sale
    // GST reporting snapshots/overrides (may be 0 until user reviews in GST Reports).
    val hsnCodeSnapshot: String? = null,
    val gstRate: Double = 0.0,      // Total GST rate (e.g., 18.0)
    val taxableValue: Double = 0.0, // price * qty before tax
    val cgstAmount: Double = 0.0,
    val sgstAmount: Double = 0.0,
    val igstAmount: Double = 0.0
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
