package com.kiranaflow.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Relation
import java.util.UUID

// Using Int for all IDs for consistency and proper Room relations.

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val price: Double, // Renamed from sellingPrice
    // Loose items (sold by weight): quantities are stored in KG across the app.
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
    // Optional: when receiving/purchasing stock, items are often added in batches (e.g., 12/24).
    // Used for quick +/- stock adjustment in the inventory list.
    val batchSize: Int? = null,
    var isDeleted: Boolean = false // For soft delete
)

@Entity(tableName = "parties")
data class PartyEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, // Changed to Int
    val name: String,
    val phone: String,
    val type: String, // "CUSTOMER" or "VENDOR"
    val gstNumber: String? = null,
    // Optional UPI VPA for payments (useful for vendors and for sharing payment links).
    val upiId: String? = null,
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
    // Controlled edit / audit fields
    val status: String = "POSTED", // DRAFT | POSTED | FINALIZED | ADJUSTED | VOIDED
    // If non-null => invoice is included in GST filing for that period (e.g. "2025-01")
    val gstFiledPeriod: String? = null,
    // Used for offline conflict resolution (client-side timestamp millis)
    val updatedAt: Long = System.currentTimeMillis(),
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
    val qty: Double,
    val unit: String = "PCS", // PCS | KG
    val price: Double, // Selling price at time of sale
    // GST reporting snapshots/overrides (may be 0 until user reviews in GST Reports).
    val hsnCodeSnapshot: String? = null,
    val gstRate: Double = 0.0,      // Total GST rate (e.g., 18.0)
    val taxableValue: Double = 0.0, // price * qty before tax
    val cgstAmount: Double = 0.0,
    val sgstAmount: Double = 0.0,
    val igstAmount: Double = 0.0
)

data class TransactionWithItems(
    @Embedded val tx: TransactionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "transactionId"
    )
    val items: List<TransactionItemEntity>
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val type: String, // "ITEM" | "VENDOR" | "GENERAL"
    val refId: Int?, // itemId/vendorId where relevant
    val dueAt: Long,
    val note: String? = null,
    val isDone: Boolean = false,
    val completedAtMillis: Long? = null // When the reminder was marked done (for 24hr auto-dismiss)
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

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val role: String, // OWNER | MANAGER | CASHIER
    val pin: String? = null, // optional PIN for switching users
    val isActive: Boolean = true
)

@Entity(
    tableName = "transaction_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["originalTransactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("originalTransactionId")]
)
data class TransactionAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originalTransactionId: Int,
    val adjustmentType: String, // EDIT | CANCEL | CORRECTION
    val reason: String,
    val userId: Int?,
    val createdAt: Long,
    val netAmountChange: Double,
    val gstType: String? = null // CREDIT_NOTE | DEBIT_NOTE
)

@Entity(
    tableName = "transaction_adjustment_items",
    foreignKeys = [
        ForeignKey(
            entity = TransactionAdjustmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["adjustmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("adjustmentId"), Index("itemId")]
)
data class TransactionAdjustmentItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val adjustmentId: Int,
    val itemId: Int?,
    val itemNameSnapshot: String,
    val quantityDelta: Double, // new_qty - old_qty
    val priceDelta: Double,
    val taxDelta: Double
)

@Entity(
    tableName = "stock_movements",
    indices = [Index("itemId"), Index("transactionId"), Index("adjustmentId")]
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val itemId: Int,
    val delta: Double, // +ve increase, -ve decrease
    val source: String, // SALE | PURCHASE | EDIT | ADJUSTMENT | VOID
    val transactionId: Int?,
    val adjustmentId: Int?,
    val userId: Int?,
    val reason: String? = null,
    val createdAt: Long
)

@Entity(
    tableName = "transaction_edit_history",
    indices = [Index("transactionId")]
)
data class TransactionEditHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val transactionId: Int,
    val fieldChanged: String,
    val oldValue: String?,
    val newValue: String?,
    val userId: Int?,
    val reason: String,
    val createdAt: Long
)
