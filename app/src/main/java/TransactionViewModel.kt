package com.example.upaimonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log
import com.example.upaimonitor.Transaction
import com.example.upaimonitor.TransactionType

/**
 * Detect transaction type from SMS message
 */
fun detectTransactionType(message: String): TransactionType {
    val creditKeywords = listOf(
        "credited", "received", "deposited", "added",
        "refund", "cashback", "reward", "credit"
    )

    val debitKeywords = listOf(
        "debited", "spent", "paid", "withdrawn",
        "deducted", "debit", "payment", "purchase"
    )

    val messageLower = message.lowercase()

    if (creditKeywords.any { messageLower.contains(it) }) return TransactionType.CREDIT
    if (debitKeywords.any { messageLower.contains(it) }) return TransactionType.DEBIT
    return TransactionType.DEBIT
}

// --- ViewModel to Manage Transaction State ---
class TransactionViewModel(
    private val repository: TransactionRepository = MyApp.repository
) : ViewModel() {

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions = _transactions.asStateFlow()

    private var lastRefreshDate: String = ""

    init {
        // Observe repository LiveData and update flow automatically
        repository.allTransactionsLiveData.observeForever { list ->
            _transactions.value = list.sortedByDescending { it.timestamp }
            Log.d("TransactionViewModel", "Transactions updated: ${list.size}")
        }

        // Initial daily refresh
        refreshIfNewDay()
    }

    /**
     * Refreshes data if current date changed.
     */
    fun refreshIfNewDay() {
        viewModelScope.launch {
            val today = DateFilterHelper.getTodayFormatted()
            if (lastRefreshDate != today) {
                lastRefreshDate = today
                loadTransactions()
            }
        }
    }

    /**
     * Loads all transactions from the DB and sorts them by timestamp.
     */
    fun loadTransactions() {
        viewModelScope.launch {
            val list = repository.getAll()
            _transactions.value = list.sortedByDescending { it.timestamp }
            Log.d("TransactionViewModel", "Loaded ${list.size} transactions from DB")
        }
    }

    /**
     * Inserts a new transaction only if not already present.
     */
    fun addNewTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.insertIfNotExists(transaction)
        }
    }

    /**
     * Removes duplicate transactions via repository.
     */
    fun removeDuplicates() {
        viewModelScope.launch {
            repository.removeDuplicates()
            Log.d("TransactionViewModel", "Duplicate cleanup requested")
        }
    }

    /**
     * Clears all transactions.
     */
    fun clearAllTransactions() {
        viewModelScope.launch {
            repository.clearAllTransactions()
            Log.d("TransactionViewModel", "Cleared all transactions from DB")
        }
    }
}
