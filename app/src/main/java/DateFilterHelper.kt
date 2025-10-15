package com.example.upaimonitor

import java.text.SimpleDateFormat
import java.util.*

object DateFilterHelper {

    /**
     * Returns today's date formatted as "15 Oct 2024"
     */
    fun getTodayFormatted(): String {
        val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return formatter.format(Date())
    }

    /**
     * Returns current month name as "October 2024"
     */
    fun getCurrentMonthName(): String {
        val formatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return formatter.format(Date())
    }

    /**
     * Checks if a transaction timestamp is from today
     */
    fun isToday(timestamp: String): Boolean {
        return try {
            val formats = listOf(
                SimpleDateFormat("MMM dd yyyy, hh:mm a", Locale.getDefault()),
                SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()),
                SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()),
                SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()),
                SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            )

            val transactionDate = formats.firstNotNullOfOrNull { fmt ->
                runCatching { fmt.parse(timestamp) }.getOrNull()
            } ?: return false

            val today = Calendar.getInstance()
            val txnCalendar = Calendar.getInstance().apply { time = transactionDate }

            today.get(Calendar.YEAR) == txnCalendar.get(Calendar.YEAR) &&
                    today.get(Calendar.DAY_OF_YEAR) == txnCalendar.get(Calendar.DAY_OF_YEAR)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if a transaction timestamp is from current month
     */
    fun isCurrentMonth(timestamp: String): Boolean {
        return try {
            val formats = listOf(
                SimpleDateFormat("MMM dd yyyy, hh:mm a", Locale.getDefault()),
                SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()),
                SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()),
                SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()),
                SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            )

            val transactionDate = formats.firstNotNullOfOrNull { fmt ->
                runCatching { fmt.parse(timestamp) }.getOrNull()
            } ?: return false

            val today = Calendar.getInstance()
            val txnCalendar = Calendar.getInstance().apply { time = transactionDate }

            today.get(Calendar.YEAR) == txnCalendar.get(Calendar.YEAR) &&
                    today.get(Calendar.MONTH) == txnCalendar.get(Calendar.MONTH)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Filters a list of transactions to only include today's transactions
     */
    fun filterTodayTransactions(transactions: List<Transaction>): List<Transaction> {
        return transactions.filter { isToday(it.timestamp) }
            .sortedByDescending { it.transactionId }
    }

    /**
     * Filters a list of transactions to only include current month's transactions
     */
    fun filterCurrentMonthTransactions(transactions: List<Transaction>): List<Transaction> {
        return transactions.filter { isCurrentMonth(it.timestamp) }
            .sortedByDescending { it.transactionId }
    }
}