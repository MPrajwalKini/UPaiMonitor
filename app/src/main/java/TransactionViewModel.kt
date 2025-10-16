package com.example.upaimonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

class TransactionViewModel(private val repository: TransactionRepository = MyApp.repository) : ViewModel() {

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions = _transactions.asStateFlow()

    private var lastRefreshDate: String = ""

    init {
        loadTransactions()
        refreshIfNewDay()
    }

    fun refreshIfNewDay() {
        viewModelScope.launch {
            val today = DateFilterHelper.getTodayFormatted()
            if (lastRefreshDate != today) {
                lastRefreshDate = today
                loadTransactions()
            }
        }
    }

    fun loadTransactions() {
        viewModelScope.launch {
            try {
                val list = repository.getAll()

                // Sort by parsing timestamp string using DateFilterHelper (newest first)
                _transactions.value = list.sortedByDescending { transaction ->
                    val parsed = DateFilterHelper.parseTimestamp(transaction.timestamp)
                    val timeMillis = parsed?.time ?: 0L
                    Log.d("TransactionViewModel", "${transaction.timestamp} -> $timeMillis (${Date(timeMillis)})")
                    timeMillis
                }

                Log.d("TransactionViewModel", "Loaded ${list.size} transactions from DB")
            } catch (e: Exception) {
                Log.e("TransactionViewModel", "Error loading transactions", e)
            }
        }
    }

    fun addNewTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                repository.insertIfNotExists(transaction)
                // Reload to maintain correct sort order
                loadTransactions()
            } catch (e: Exception) {
                Log.e("TransactionViewModel", "Error adding transaction", e)
            }
        }
    }

    fun removeDuplicates() {
        viewModelScope.launch {
            try {
                repository.removeDuplicates()
                // Reload after removing duplicates
                loadTransactions()
            } catch (e: Exception) {
                Log.e("TransactionViewModel", "Error removing duplicates", e)
            }
        }
    }

    fun clearAllTransactions() {
        viewModelScope.launch {
            try {
                repository.clearAllTransactions()
                _transactions.value = emptyList()
                Log.d("TransactionViewModel", "All transactions cleared")
            } catch (e: Exception) {
                Log.e("TransactionViewModel", "Error clearing transactions", e)
            }
        }
    }
}