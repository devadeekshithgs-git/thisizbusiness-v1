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
    version = 11, // v11: add items.stockKg for loose-item stock stored in KG
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

        fun getDatabase(context: Context): KiranaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KiranaDatabase::class.java,
                    "kirana_database"
                )
                .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
