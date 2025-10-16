package com.example.upaimonitor

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.upaimonitor.com.example.upaimonitor.Transaction

@Dao
interface TransactionDao {

    /**
     * Inserts a new transaction into the database.
     * If a transaction with the same transactionId already exists,
     * it will be replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction)

    /**
     * Returns all stored transactions ordered by timestamp (latest first).
     */
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAll(): List<Transaction>

    /**
     * Checks if a transaction already exists by transactionId.
     */
    @Query("SELECT COUNT(*) FROM transactions WHERE transactionId = :id")
    suspend fun exists(id: String): Int

    /**
     * Clears all transactions from the database.
     */
    @Query("DELETE FROM transactions")
    suspend fun clearAll()

    /**
     * Deletes a specific transaction from the database.
     */
    @Delete
    suspend fun delete(transaction: Transaction)
}
