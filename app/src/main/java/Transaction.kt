package com.example.upaimonitor

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.*

// --- Transaction Type Enum ---
enum class TransactionType { DEBIT, CREDIT }

// --- Transaction Entity ---
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val transactionId: String = "T${System.currentTimeMillis()}",
    val amount: Double,
    val sender: String,
    val timestamp: String = formatTimestamp(System.currentTimeMillis()),
    val message: String = "",
    val isSynced: Boolean = false,
    val transactionType: String = TransactionType.DEBIT.name,
    val rawTimestamp: Long = System.currentTimeMillis()
) {
    fun isCredit() = transactionType == TransactionType.CREDIT.name
    fun isDebit() = transactionType == TransactionType.DEBIT.name
    fun getType(): TransactionType = try {
        TransactionType.valueOf(transactionType)
    } catch (e: Exception) {
        TransactionType.DEBIT
    }
}


// --- Timestamp formatting ---
fun formatTimestamp(timeInMillis: Long): String {
    val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    return sdf.format(Date(timeInMillis))
}
