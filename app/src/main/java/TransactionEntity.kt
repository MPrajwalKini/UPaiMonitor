package com.example.upaimonitor

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val transactionId: String,
    val amount: Double,
    val sender: String,
    val timestamp: String,
    val message: String,
    val isSynced: Boolean
)

// Helper to convert between Entity and ViewModel Transaction
fun TransactionEntity.toTransaction() = Transaction(
    amount = amount,
    sender = sender,
    timestamp = timestamp,
    transactionId = transactionId,
    message = message,
    isSynced = isSynced
)

fun Transaction.toEntity() = TransactionEntity(
    transactionId = transactionId,
    amount = amount,
    sender = sender,
    timestamp = timestamp,
    message = message,
    isSynced = isSynced
)
