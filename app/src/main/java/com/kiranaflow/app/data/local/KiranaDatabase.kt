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
        ReminderEntity::class,
        OutboxEntity::class
    ],
    version = 12, // v12: GST reporting fields (items.hsnCode, parties.stateCode, transaction_items tax snapshots)
    exportSchema = false
)
abstract class KiranaDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun partyDao(): PartyDao
    abstract fun transactionDao(): TransactionDao
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

        fun getDatabase(context: Context): KiranaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KiranaDatabase::class.java,
                    "kirana_database"
                )
                .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
