package com.example.upaimonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Transaction Data Class (Room Entity Compatible) ---
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val transactionId: String = "T${System.currentTimeMillis()}",
    val amount: Double,
    val sender: String,
    val timestamp: String = formatTimestamp(System.currentTimeMillis()),
    val message: String = "",
    val isSynced: Boolean = false
)

// --- Helper function for timestamp formatting ---
fun formatTimestamp(timeInMillis: Long): String {
    val date = Date(timeInMillis)
    val format = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    return format.format(date)
}

// --- ViewModel to Manage Transaction State ---
class TransactionViewModel : ViewModel() {

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions = _transactions.asStateFlow()

    init {
        loadTransactions()
    }

    /** Loads all transactions from the database at startup */
    private fun loadTransactions() {
        viewModelScope.launch {
            val storedTransactions = MyApp.repository.getAll()
            _transactions.value = storedTransactions
        }
    }

    /** Adds a new transaction both to DB and in-memory list */
    fun addNewTransaction(transaction: Transaction) {
        viewModelScope.launch {
            MyApp.repository.insert(transaction)
            _transactions.value = listOf(transaction) + _transactions.value
        }
    }
}
