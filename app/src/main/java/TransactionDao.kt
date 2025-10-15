package com.example.upaimonitor

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.text.SimpleDateFormat
import java.util.Locale

// TransactionDao Interface
@Dao
interface TransactionDao {

    /**
     * Inserts a new transaction into the database.
     * If a transaction with the same transactionId already exists,
     * it will be replaced (avoiding accidental duplication).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction)

    /**
     * Returns all stored transactions ordered by timestamp (latest first).
     */
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAll(): List<Transaction>

    /**
     * Checks if a transaction already exists by ID.
     */
    @Query("SELECT COUNT(*) FROM transactions WHERE transactionId = :id")
    suspend fun exists(id: String): Int

    /**
     * Deletes a transaction from the database.
     */
    @Delete
    suspend fun delete(transaction: Transaction)
}