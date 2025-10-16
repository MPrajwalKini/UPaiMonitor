package com.example.upaimonitor

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import java.text.SimpleDateFormat
import java.util.Locale

class TransactionRepository(context: Context) {

    val dao = AppDatabase.getInstance(context).transactionDao()

    // LiveData bridge between background receiver and UI ViewModel
    val newTransactionLiveData = MutableLiveData<Transaction?>()

    /**
     * Inserts a transaction into Room DB.
     * Replaces existing transaction if IDs match (safe write).
     */
    suspend fun insert(transaction: Transaction) {
        dao.insert(transaction)
    }

    /**
     * Safely insert a transaction only if it doesn't already exist.
     * Prevents duplicate SMS transactions.
     */
    suspend fun insertIfNotExists(transaction: Transaction) {
        val exists = dao.exists(transaction.transactionId)
        if (exists == 0) {
            dao.insert(transaction)
            Log.d("TransactionRepository", "Inserted new transaction: ${transaction.transactionId}")
        } else {
            Log.d("TransactionRepository", "Skipped duplicate transaction: ${transaction.transactionId}")
        }
    }

    /**
     * Check if a transaction is a duplicate based on amount, type, and timestamp proximity.
     * Returns true if a similar transaction exists within 60 seconds.
     */
    suspend fun isDuplicateTransaction(transaction: Transaction): Boolean {
        val allTransactions = dao.getAll()
        return allTransactions.any { existing ->
            // Consider it duplicate if amount, type match and timestamps are within 60 seconds
            existing.amount == transaction.amount &&
                    existing.transactionType == transaction.transactionType &&
                    areTimestampsClose(existing.timestamp, transaction.timestamp, 60000)
        }
    }

    /**
     * Helper function to check if two timestamps are close (within threshold)
     */
    private fun areTimestampsClose(timestamp1: String, timestamp2: String, thresholdMillis: Long): Boolean {
        return try {
            val format = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
            val date1 = format.parse(timestamp1)
            val date2 = format.parse(timestamp2)
            if (date1 != null && date2 != null) {
                Math.abs(date1.time - date2.time) <= thresholdMillis
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error parsing timestamps", e)
            false
        }
    }

    /**
     * Loads all stored transactions from the database.
     */
    suspend fun getAll(): List<Transaction> {
        return dao.getAll()
    }

    /**
     * Called from SmsReceiver or DebugHelper to notify the UI.
     */
    fun postNewTransaction(transaction: Transaction) {
        newTransactionLiveData.postValue(transaction)
        Log.d("TransactionRepository", "New transaction posted to LiveData: ${transaction.transactionId}")
    }

    /**
     * Clears the LiveData after the MainActivity has consumed it.
     */
    fun clearNewTransaction() {
        newTransactionLiveData.postValue(null)
    }

    /**
     * Removes duplicate transactions from the database.
     * Keeps the first occurrence and deletes subsequent duplicates.
     */
    /**
     * Removes near-duplicate transactions.
     * Two transactions are considered duplicates if:
     * - They have the same normalized sender
     * - They have the same amount
     * - Their timestamps are within 60 seconds
     */
    suspend fun removeDuplicates(): Int {
        val allTransactions = dao.getAll().sortedBy { parseTimestamp(it.timestamp) }
        val toDelete = mutableListOf<Transaction>()
        val seen = mutableListOf<Transaction>()

        for (tx in allTransactions) {
            val isDuplicate = seen.any { existing ->
                normalizeSender(existing.sender) == normalizeSender(tx.sender) &&
                        existing.amount == tx.amount &&
                        areTimestampsClose(existing.timestamp, tx.timestamp, 60000)
            }

            if (isDuplicate) {
                toDelete.add(tx)
            } else {
                seen.add(tx)
            }
        }

        // Delete duplicates
        toDelete.forEach { dao.delete(it) }
        Log.d("TransactionRepository", "Removed ${toDelete.size} duplicate transactions")
        return toDelete.size
    }


    /**
     * Converts timestamp string to milliseconds.
     */
    fun parseTimestamp(timestamp: String): Long {
        return try {
            val format = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
            format.parse(timestamp)?.time ?: 0L
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error parsing timestamp: $timestamp", e)
            0L
        }
    }

    /**
     * Normalizes sender (e.g., "AX-CANBNK-S" → "CANBNK").
     */
    fun normalizeSender(sender: String): String {
        val upper = sender.uppercase(Locale.getDefault())
        val match = Regex("([A-Z]{3,8})").findAll(upper).map { it.value }.toList()
        return if (match.isNotEmpty()) match[match.size / 2] else upper.take(8)
    }

}
