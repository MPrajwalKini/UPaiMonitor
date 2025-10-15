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

// --- Transaction Type Enum ---
enum class TransactionType {
    DEBIT,   // Money sent/spent
    CREDIT   // Money received
}

// --- Transaction Data Class (Room Entity Compatible) ---
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val transactionId: String = "T${System.currentTimeMillis()}",
    val amount: Double,
    val sender: String,
    val timestamp: String = formatTimestamp(System.currentTimeMillis()),
    val message: String = "",
    val isSynced: Boolean = false,
    val transactionType: String = TransactionType.DEBIT.name // Store as String for Room
) {
    /**
     * Check if this is a credit (money received) transaction
     */
    fun isCredit(): Boolean = transactionType == TransactionType.CREDIT.name

    /**
     * Check if this is a debit (money sent) transaction
     */
    fun isDebit(): Boolean = transactionType == TransactionType.DEBIT.name

    /**
     * Get the transaction type as enum
     */
    fun getType(): TransactionType {
        return try {
            TransactionType.valueOf(transactionType)
        } catch (e: Exception) {
            TransactionType.DEBIT // Default fallback
        }
    }
}

// --- Helper function for timestamp formatting ---
fun formatTimestamp(timeInMillis: Long): String {
    val date = Date(timeInMillis)
    val format = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    return format.format(date)
}

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

    // Check credit keywords first
    if (creditKeywords.any { messageLower.contains(it) }) {
        return TransactionType.CREDIT
    }

    // Check debit keywords
    if (debitKeywords.any { messageLower.contains(it) }) {
        return TransactionType.DEBIT
    }

    // Default to debit if unclear
    return TransactionType.DEBIT
}

// --- ViewModel to Manage Transaction State ---
class TransactionViewModel : ViewModel() {

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions = _transactions.asStateFlow()

    init {
        loadTransactions()
    }

    /** Loads all transactions from the database at startup */
    fun loadTransactions() {
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