package com.example.upaimonitor

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Locale

class TransactionRepository(private val transactionDao: TransactionDao) {

    // LiveData for notifying when new transactions are detected
    private val _newTransactionLiveData = MutableLiveData<Transaction?>()
    val newTransactionLiveData: LiveData<Transaction?> = _newTransactionLiveData

    /**
     * Retrieves all transactions from the database
     */
    suspend fun getAll(): List<Transaction> {
        return try {
            transactionDao.getAll()
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error fetching all transactions", e)
            emptyList()
        }
    }

    /**
     * Inserts a transaction into the database
     */
    suspend fun insert(transaction: Transaction) {
        try {
            transactionDao.insert(transaction)
            Log.d("TransactionRepository", "Transaction inserted: ${transaction.transactionId}")
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error inserting transaction", e)
        }
    }

    /**
     * Inserts a transaction only if it doesn't already exist
     */
    suspend fun insertIfNotExists(transaction: Transaction) {
        try {
            val existsCount = transactionDao.exists(transaction.transactionId)
            if (existsCount == 0) {
                transactionDao.insert(transaction)
                _newTransactionLiveData.postValue(transaction)
                Log.d("TransactionRepository", "New transaction inserted: ${transaction.transactionId}")
            } else {
                Log.d("TransactionRepository", "Transaction already exists: ${transaction.transactionId}")
            }
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error in insertIfNotExists", e)
        }
    }

    /**
     * Checks if a transaction exists by ID
     */
    suspend fun isDuplicateTransaction(transaction: Transaction): Boolean {
        return try {
            val existsCount = transactionDao.exists(transaction.transactionId)
            existsCount > 0
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error checking duplicate", e)
            false
        }
    }

    /**
     * Clears the new transaction LiveData
     */
    fun clearNewTransaction() {
        _newTransactionLiveData.value = null
    }

    /**
     * Removes duplicate transactions from the database
     * Keeps only the most recent transaction for each unique ID
     */
    suspend fun removeDuplicates(): Int {
        return try {
            val allTransactions = transactionDao.getAll()
            val seenIds = mutableSetOf<String>()
            var removedCount = 0

            for (transaction in allTransactions) {
                if (seenIds.contains(transaction.transactionId)) {
                    transactionDao.delete(transaction)
                    removedCount++
                    Log.d("TransactionRepository", "Removed duplicate: ${transaction.transactionId}")
                } else {
                    seenIds.add(transaction.transactionId)
                }
            }

            Log.d("TransactionRepository", "Total duplicates removed: $removedCount")
            removedCount
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error removing duplicates", e)
            0
        }
    }

    /**
     * Deletes a specific transaction
     */
    suspend fun deleteTransaction(transaction: Transaction) {
        try {
            transactionDao.delete(transaction)
            Log.d("TransactionRepository", "Transaction deleted: ${transaction.transactionId}")
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error deleting transaction", e)
        }
    }

    /**
     * Clears all transactions from the database
     */
    suspend fun clearAllTransactions() {
        try {
            transactionDao.clearAll()
            Log.d("TransactionRepository", "All transactions cleared")
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error clearing all transactions", e)
        }
    }

    /**
     * Gets transactions for a specific date
     */
    suspend fun getTransactionsByDate(date: String): List<Transaction> {
        return try {
            val allTransactions = transactionDao.getAll()
            allTransactions.filter { it.timestamp.contains(date) }
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error fetching transactions by date", e)
            emptyList()
        }
    }
}