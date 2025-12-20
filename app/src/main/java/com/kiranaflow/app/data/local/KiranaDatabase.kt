package com.kiranaflow.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ItemEntity::class,
        PartyEntity::class,
        TransactionEntity::class,
        TransactionItemEntity::class,
        ReminderEntity::class,
        OutboxEntity::class
    ],
    version = 8, // added TransactionEntity.receiptImageUri (dev uses destructive fallback)
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

        fun getDatabase(context: Context): KiranaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KiranaDatabase::class.java,
                    "kirana_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
