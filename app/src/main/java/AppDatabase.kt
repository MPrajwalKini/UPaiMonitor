package com.example.upaimonitor

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.upaimonitor.Transaction

@Database(entities = [Transaction::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "upai_transactions.db"
                )
                    .fallbackToDestructiveMigration() // Wipes DB on schema change (for development)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}