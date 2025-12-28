package com.kiranaflow.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ItemEntity::class,
        PartyEntity::class,
        TransactionEntity::class,
        TransactionItemEntity::class,
        UserEntity::class,
        TransactionAdjustmentEntity::class,
        TransactionAdjustmentItemEntity::class,
        StockMovementEntity::class,
        TransactionEditHistoryEntity::class,
        ReminderEntity::class,
        OutboxEntity::class
    ],
    version = 17, // v17: controlled transaction edit + audit tables
    exportSchema = false
)
abstract class KiranaDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun partyDao(): PartyDao
    abstract fun transactionDao(): TransactionDao
    abstract fun userDao(): UserDao
    abstract fun transactionAdjustmentDao(): TransactionAdjustmentDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun transactionEditHistoryDao(): TransactionEditHistoryDao
    abstract fun reminderDao(): ReminderDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        @Volatile
        private var INSTANCE: KiranaDatabase? = null

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE parties ADD COLUMN openingDue REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN isLoose INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE items ADD COLUMN pricePerKg REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE transaction_items ADD COLUMN unit TEXT NOT NULL DEFAULT 'PCS'")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Store loose stock in KG. Keep existing `stock` as-is for backward compatibility.
                db.execSQL("ALTER TABLE items ADD COLUMN stockKg REAL NOT NULL DEFAULT 0.0")
                // Migrate existing values:
                // - If isLoose: existing stock is treated as grams -> KG
                // - Else: copy integer stock into stockKg (currently unused for non-loose items)
                db.execSQL(
                    """
                    UPDATE items
                    SET stockKg = CASE
                        WHEN isLoose = 1 THEN (stock * 1.0) / 1000.0
                        ELSE (stock * 1.0)
                    END
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Items: GST HSN/SAC code (nullable)
                db.execSQL("ALTER TABLE items ADD COLUMN hsnCode TEXT")

                // Parties: 2-digit state code (nullable)
                db.execSQL("ALTER TABLE parties ADD COLUMN stateCode INTEGER")

                // Transaction items: GST snapshots/overrides (default 0)
                db.execSQL("ALTER TABLE transaction_items ADD COLUMN hsnCodeSnapshot TEXT")
                db.execSQL("ALTER TABLE transaction_items ADD COLUMN gstRate REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE transaction_items ADD COLUMN taxableValue REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE transaction_items ADD COLUMN cgstAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE transaction_items ADD COLUMN sgstAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE transaction_items ADD COLUMN igstAmount REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Reminders: completedAtMillis for 24hr auto-dismiss of completed tasks
                db.execSQL("ALTER TABLE reminders ADD COLUMN completedAtMillis INTEGER")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Parties: optional UPI ID (VPA) for UPI payment redirects (vendors/customers)
                db.execSQL("ALTER TABLE parties ADD COLUMN upiId TEXT")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // transaction_items.qty: INTEGER (legacy) -> REAL (kg for loose items)
                // Also normalize unit away from grams: GRAM/G/GM/GMS -> KG, and convert qty accordingly.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS transaction_items_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        transactionId INTEGER NOT NULL,
                        itemId INTEGER,
                        itemNameSnapshot TEXT NOT NULL,
                        qty REAL NOT NULL,
                        unit TEXT NOT NULL DEFAULT 'PCS',
                        price REAL NOT NULL,
                        hsnCodeSnapshot TEXT,
                        gstRate REAL NOT NULL DEFAULT 0.0,
                        taxableValue REAL NOT NULL DEFAULT 0.0,
                        cgstAmount REAL NOT NULL DEFAULT 0.0,
                        sgstAmount REAL NOT NULL DEFAULT 0.0,
                        igstAmount REAL NOT NULL DEFAULT 0.0,
                        FOREIGN KEY(transactionId) REFERENCES transactions(id) ON DELETE CASCADE,
                        FOREIGN KEY(itemId) REFERENCES items(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO transaction_items_new (
                        id, transactionId, itemId, itemNameSnapshot, qty, unit, price,
                        hsnCodeSnapshot, gstRate, taxableValue, cgstAmount, sgstAmount, igstAmount
                    )
                    SELECT
                        id,
                        transactionId,
                        itemId,
                        itemNameSnapshot,
                        CASE
                            WHEN UPPER(unit) IN ('GRAM','G','GM','GMS','GRAMS') THEN (qty * 1.0) / 1000.0
                            ELSE (qty * 1.0)
                        END AS qty,
                        CASE
                            WHEN UPPER(unit) IN ('GRAM','G','GM','GMS','GRAMS') THEN 'KG'
                            ELSE unit
                        END AS unit,
                        price,
                        hsnCodeSnapshot,
                        gstRate,
                        taxableValue,
                        cgstAmount,
                        sgstAmount,
                        igstAmount
                    FROM transaction_items
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE transaction_items")
                db.execSQL("ALTER TABLE transaction_items_new RENAME TO transaction_items")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_items_transactionId ON transaction_items(transactionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_items_itemId ON transaction_items(itemId)")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Items: optional batch size for quick stock adjustments (nullable).
                db.execSQL("ALTER TABLE items ADD COLUMN batchSize INTEGER")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // transactions: controlled edit fields
                db.execSQL("ALTER TABLE transactions ADD COLUMN status TEXT NOT NULL DEFAULT 'POSTED'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN gstFiledPeriod TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")

                // users
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        role TEXT NOT NULL,
                        pin TEXT,
                        isActive INTEGER NOT NULL DEFAULT 1
                    )
                    """.trimIndent()
                )

                // transaction_adjustments
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS transaction_adjustments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        originalTransactionId INTEGER NOT NULL,
                        adjustmentType TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        userId INTEGER,
                        createdAt INTEGER NOT NULL,
                        netAmountChange REAL NOT NULL,
                        gstType TEXT,
                        FOREIGN KEY(originalTransactionId) REFERENCES transactions(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_adjustments_originalTransactionId ON transaction_adjustments(originalTransactionId)")

                // transaction_adjustment_items
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS transaction_adjustment_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        adjustmentId INTEGER NOT NULL,
                        itemId INTEGER,
                        itemNameSnapshot TEXT NOT NULL,
                        quantityDelta REAL NOT NULL,
                        priceDelta REAL NOT NULL,
                        taxDelta REAL NOT NULL,
                        FOREIGN KEY(adjustmentId) REFERENCES transaction_adjustments(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_adjustment_items_adjustmentId ON transaction_adjustment_items(adjustmentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_adjustment_items_itemId ON transaction_adjustment_items(itemId)")

                // stock_movements
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS stock_movements (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        itemId INTEGER NOT NULL,
                        delta REAL NOT NULL,
                        source TEXT NOT NULL,
                        transactionId INTEGER,
                        adjustmentId INTEGER,
                        userId INTEGER,
                        reason TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_itemId ON stock_movements(itemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_transactionId ON stock_movements(transactionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_adjustmentId ON stock_movements(adjustmentId)")

                // transaction_edit_history
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS transaction_edit_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        transactionId INTEGER NOT NULL,
                        fieldChanged TEXT NOT NULL,
                        oldValue TEXT,
                        newValue TEXT,
                        userId INTEGER,
                        reason TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_edit_history_transactionId ON transaction_edit_history(transactionId)")
            }
        }

        fun getDatabase(context: Context): KiranaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KiranaDatabase::class.java,
                    "kirana_database"
                )
                .addMigrations(
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
