package com.example.upaimonitor

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.upaimonitor.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

class TransactionRepository(context: Context) {

    // Keep dao accessible as in Code 2
    val dao = AppDatabase.getInstance(context).transactionDao()

    // LiveData for UI observation (from Code 1)
    private val _allTransactionsLiveData = MutableLiveData<List<Transaction>>(emptyList())
    val allTransactionsLiveData: LiveData<List<Transaction>> get() = _allTransactionsLiveData

    // LiveData bridge for new incoming transaction (Code 2 style)
    val newTransactionLiveData = MutableLiveData<Transaction?>()

    /**
     * Loads all transactions from DB into LiveData.

     */
    suspend fun loadAllTransactions() = withContext(Dispatchers.IO) {
        val all = dao.getAll()
        _allTransactionsLiveData.postValue(all)
    }

    /**
     * Force insert (no duplicate check) — kept from Code 2 but wrapped in IO context.
     */
    suspend fun insert(transaction: Transaction) = withContext(Dispatchers.IO) {
        dao.insert(transaction)
        Log.d("TransactionRepository", "Inserted transaction (force): ${transaction.transactionId}")
        updateAllTransactions()
    }

    /**
     * Insert only if transactionId not present (safe insert).
     * Uses DAO.exists like both files.
     */
    suspend fun insertIfNotExists(transaction: Transaction) = withContext(Dispatchers.IO) {
        val exists = dao.exists(transaction.transactionId)
        if (exists == 0) {
            dao.insert(transaction)
            newTransactionLiveData.postValue(transaction)
            updateAllTransactions()
            Log.d("TransactionRepository", "Inserted new transaction: ${transaction.transactionId}")
        } else {
            Log.d("TransactionRepository", "Skipped duplicate transaction: ${transaction.transactionId}")
        }
    }

    /**
     * Checks if a similar transaction already exists (amount + type + close timestamp).
     * Uses Code 2 logic but keeps coroutine context safety.
     */
    suspend fun isDuplicateTransaction(transaction: Transaction): Boolean = withContext(Dispatchers.IO) {
        val allTransactions = dao.getAll()
        allTransactions.any { existing ->

            existing.amount == transaction.amount &&
                    existing.transactionType == transaction.transactionType &&
                    areTimestampsClose(existing.timestamp, transaction.timestamp, 60000)
        }
    }

    /**
     * Removes near-duplicate transactions.
     * Duplicate criteria (from Code 2):
     * - same normalized sender
     * - same amount
     * - timestamps within threshold (60s)
     *
     * Keeps the first occurrence and deletes subsequent duplicates.
     */
    suspend fun removeDuplicates(): Int = withContext(Dispatchers.IO) {
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

        // Delete duplicates and refresh LiveData
        toDelete.forEach { dao.delete(it) }
        updateAllTransactions()
        Log.d("TransactionRepository", "Removed ${toDelete.size} duplicate transactions")
        toDelete.size
    }

    /**
     * Delete everything (keeps Code 1 behavior of refreshing LiveData).
     */
    suspend fun clearAllTransactions() = withContext(Dispatchers.IO) {
        dao.clearAll()
        updateAllTransactions()
        Log.d("TransactionRepository", "Cleared all transactions")
    }

    /**
     * Get all (background-friendly).
     */
    suspend fun getAll(): List<Transaction> = withContext(Dispatchers.IO) { dao.getAll() }

    /**
     * Posts a new transaction to LiveData (for UI update by receiver/debug helper).
     * Uses postValue so it can be called from background threads (Code 2 style).
     */
    fun postNewTransaction(transaction: Transaction) {
        newTransactionLiveData.postValue(transaction)
        Log.d("TransactionRepository", "New transaction posted to LiveData: ${transaction.transactionId}")
    }

    /**
     * Clears the new transaction LiveData after being consumed by UI.
     */
    fun clearNewTransaction() {
        newTransactionLiveData.postValue(null)
    }

    // ---- Helpers ----





























    private suspend fun updateAllTransactions() {
        val all = dao.getAll()
        _allTransactionsLiveData.postValue(all)

    }


    /**
     * Parses timestamp string (Code 2 implementation).
     * Returns epoch millis or 0L on error.
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
     * Checks whether two timestamp strings are within threshold milliseconds.
     * Uses the same format as parseTimestamp.
     */
    private fun areTimestampsClose(ts1: String, ts2: String, thresholdMillis: Long): Boolean {
        return try {
            val format = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
            val d1 = format.parse(ts1)
            val d2 = format.parse(ts2)
            if (d1 != null && d2 != null) abs(d1.time - d2.time) <= thresholdMillis else false
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error comparing timestamps", e)
            false
        }
    }

    /**
     * Normalizes sender string to a compact identifier (Code 2 logic).
     * Example: "AX-CANBNK-S" -> "CANBNK"
     */
    fun normalizeSender(sender: String): String {
        val upper = sender.uppercase(Locale.getDefault())
        val match = Regex("([A-Z]{3,8})").findAll(upper).map { it.value }.toList()
        return if (match.isNotEmpty()) match[match.size / 2] else upper.take(8)
    }

}
