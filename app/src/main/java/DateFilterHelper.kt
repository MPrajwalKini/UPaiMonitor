package com.example.upaimonitor

import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

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
     * Returns all months of current and previous year as ["Jan 2025", "Feb 2025", ...]
     */
    fun getAllMonthNames(): List<String> {
        val months = mutableListOf<String>()
        val cal = Calendar.getInstance()

        // Include current year and previous year for history
        for (yearOffset in 0..1) {
            for (i in 0..11) {
                cal.set(Calendar.MONTH, i)
                cal.set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR) - yearOffset)
                months.add(
                    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
                )
            }
        }

        // Sort latest months first
        return months.sortedByDescending {
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).parse(it)?.time
        }
    }

    /**
     * Checks if a transaction timestamp is from today
     */
    fun isToday(timestamp: String): Boolean {
        return try {
            val transactionDate = parseTimestamp(timestamp) ?: return false
            val today = Calendar.getInstance()
            val txnCalendar = Calendar.getInstance().apply { time = transactionDate }

            today.get(Calendar.YEAR) == txnCalendar.get(Calendar.YEAR) &&
                    today.get(Calendar.DAY_OF_YEAR) == txnCalendar.get(Calendar.DAY_OF_YEAR)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if a transaction timestamp is from the current month
     */
    fun isCurrentMonth(timestamp: String): Boolean {
        return try {
            val transactionDate = parseTimestamp(timestamp) ?: return false
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
     * Sorted by newest first
     */
    fun filterTodayTransactions(transactions: List<Transaction>): List<Transaction> {
        return transactions.filter { isToday(it.timestamp) }
            .sortedByDescending { transaction ->
                try {
                    // Extract timestamp from ID like "T1697123456789" or "UPI123456789"
                    transaction.transactionId
                        .removePrefix("T")
                        .removePrefix("UPI")
                        .filter { it.isDigit() }
                        .toLongOrNull() ?: 0L
                } catch (e: Exception) {
                    0L
                }
            }
    }

    /**
     * Filters a list of transactions to only include current month's transactions
     * Sorted by newest first
     */
    fun filterCurrentMonthTransactions(transactions: List<Transaction>): List<Transaction> {
        return transactions.filter { isCurrentMonth(it.timestamp) }
            .sortedByDescending { transaction ->
                try {
                    // Extract timestamp from ID like "T1697123456789" or "UPI123456789"
                    transaction.transactionId
                        .removePrefix("T")
                        .removePrefix("UPI")
                        .filter { it.isDigit() }
                        .toLongOrNull() ?: 0L
                } catch (e: Exception) {
                    0L
                }
            }
    }

    /**
     * Filters transactions by given month name (e.g. "October 2024")
     * Sorted by newest first
     */
    fun filterTransactionsByMonth(transactions: List<Transaction>, monthName: String): List<Transaction> {
        return transactions.filter { isFromMonth(it.timestamp, monthName) }
            .sortedByDescending { transaction ->
                try {
                    // Extract timestamp from ID like "T1697123456789" or "UPI123456789"
                    transaction.transactionId
                        .removePrefix("T")
                        .removePrefix("UPI")
                        .filter { it.isDigit() }
                        .toLongOrNull() ?: 0L
                } catch (e: Exception) {
                    0L
                }
            }
    }

    /**
     * Checks if a transaction timestamp matches a specific month string like "October 2024"
     */
    private fun isFromMonth(timestamp: String, monthName: String): Boolean {
        return try {
            val transactionDate = parseTimestamp(timestamp) ?: return false
            val txnMonthYear = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(transactionDate)
            txnMonthYear.equals(monthName, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Parses various UPI timestamp formats
     * FIXED: Now properly handles timestamps without year by assuming current year
     */
    fun parseTimestamp(timestamp: String): Date? {
        val formats = listOf(
            "MMM dd, yyyy, hh:mm a",
            "MMM dd yyyy, hh:mm a",
            "dd MMM yyyy, hh:mm a",
            "MMM dd, hh:mm a",
            "dd MMM, hh:mm a",
            "dd-MM-yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "yyyy-MM-dd HH:mm"
        )

        for (pattern in formats) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.getDefault())
                fmt.isLenient = false
                val parsed = fmt.parse(timestamp)
                if (parsed != null) {
                    // If year is missing, fix it properly
                    val cal = Calendar.getInstance()
                    cal.time = parsed

                    if (pattern.contains("yyyy").not()) {
                        val now = Calendar.getInstance()
                        cal.set(Calendar.YEAR, now.get(Calendar.YEAR))

                        // 🔥 Smart adjustment:
                        // If parsed date is "in the future" (e.g., Dec 31 parsed in Jan),
                        // it probably belongs to the *previous year*.
                        if (cal.after(now)) {
                            cal.add(Calendar.YEAR, -1)
                        }
                    }

                    Log.d("DateFilterHelper", "Parsed '$timestamp' → ${cal.time} using '$pattern'")
                    return cal.time
                }
            } catch (e: Exception) {
                // Continue trying next format
            }
        }

        Log.w("DateFilterHelper", "❌ Could not parse timestamp: $timestamp")
        return null
    }

}