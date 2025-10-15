package com.example.upaimonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log

class TransactionViewModel(private val repository: TransactionRepository = MyApp.repository) : ViewModel() {

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions = _transactions.asStateFlow()

    private var lastRefreshDate: String = ""

    init {
        repository.allTransactionsLiveData.observeForever { list ->
            _transactions.value = list.sortedByDescending { it.timestamp }
            Log.d("TransactionViewModel", "Transactions updated: ${list.size}")
        }
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
            val list = repository.getAll()
            _transactions.value = list.sortedByDescending { it.timestamp }
            Log.d("TransactionViewModel", "Loaded ${list.size} transactions from DB")
        }
    }

    fun addNewTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.insertIfNotExists(transaction)
        }
    }

    fun removeDuplicates() {
        viewModelScope.launch { repository.removeDuplicates() }
    }

    fun clearAllTransactions() {
        viewModelScope.launch { repository.clearAllTransactions() }
    }
}
