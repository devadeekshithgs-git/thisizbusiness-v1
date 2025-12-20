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
    version = 9, // added PartyEntity.openingDue (dev uses destructive fallback)
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

        fun getDatabase(context: Context): KiranaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KiranaDatabase::class.java,
                    "kirana_database"
                )
                .addMigrations(MIGRATION_8_9)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
