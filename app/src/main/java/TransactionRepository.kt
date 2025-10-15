package com.example.upaimonitor

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class TransactionRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).transactionDao()

    // LiveData for UI
    private val _allTransactionsLiveData = MutableLiveData<List<Transaction>>(emptyList())
    val allTransactionsLiveData: LiveData<List<Transaction>> get() = _allTransactionsLiveData

    val newTransactionLiveData = MutableLiveData<Transaction?>()

    suspend fun loadAllTransactions() {
        val all = dao.getAll()
        _allTransactionsLiveData.postValue(all)
    }

    suspend fun insertIfNotExists(transaction: Transaction) = withContext(Dispatchers.IO) {
        val exists = dao.exists(transaction.transactionId)
        if (exists == 0) {
            dao.insert(transaction)
            newTransactionLiveData.postValue(transaction)
            updateAllTransactions()
            Log.d("TransactionRepository", "Inserted: ${transaction.transactionId}")
        } else Log.d("TransactionRepository", "Duplicate skipped: ${transaction.transactionId}")
    }

    // In TransactionRepository.kt

    fun postNewTransaction(transaction: Transaction) {
        newTransactionLiveData.value = transaction
    }

    private suspend fun updateAllTransactions() {
        val all = dao.getAll()
        _allTransactionsLiveData.postValue(all)
    }

    suspend fun clearAllTransactions() = withContext(Dispatchers.IO) {
        dao.clearAll()
        updateAllTransactions()
    }

    suspend fun isDuplicateTransaction(txn: Transaction): Boolean {
        return allTransactionsLiveData.value?.any { it.transactionId == txn.transactionId } ?: false
    }

    fun clearNewTransaction() {
        newTransactionLiveData.value = null
    }

    suspend fun getAll(): List<Transaction> = withContext(Dispatchers.IO) { dao.getAll() }

    suspend fun removeDuplicates(): Int = withContext(Dispatchers.IO) {
        val all = dao.getAll()
        val seen = mutableListOf<Transaction>()
        val toDelete = all.filter { t ->
            val duplicate = seen.any { s ->
                s.amount == t.amount && s.transactionType == t.transactionType &&
                        areTimestampsClose(s.timestamp, t.timestamp, 60000)
            }
            if (!duplicate) seen.add(t)
            duplicate
        }
        toDelete.forEach { dao.delete(it) }
        updateAllTransactions()
        Log.d("TransactionRepository", "Removed ${toDelete.size} duplicates")
        toDelete.size
    }

    private fun areTimestampsClose(ts1: String, ts2: String, threshold: Long): Boolean {
        return try {
            val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
            val d1 = sdf.parse(ts1)
            val d2 = sdf.parse(ts2)
            if (d1 != null && d2 != null) kotlin.math.abs(d1.time - d2.time) <= threshold else false
        } catch (e: Exception) { false }
    }
}
