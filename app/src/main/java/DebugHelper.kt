package com.example.upaimonitor

import android.util.Log

object DebugHelper {
    fun simulateSMS(sender: String, body: String) {
        val regex = Regex("""(?i)(?:Rs\.?|INR)\s*([0-9,.]+)""")
        val match = regex.find(body)
        val amount = match?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        val timestamp = System.currentTimeMillis()  // 1731478923000
        val formatted = formatTimestamp(timestamp)
        // "Nov 13, 02:35 PM"
        if (amount > 0) {
            val transaction = Transaction(
                amount = amount,
                sender = sender,
                timestamp = formatted,
                message = body
            )
            MyApp.repository.postNewTransaction(transaction)
            Log.d("DebugHelper", "Simulated transaction: $transaction")
        } else {
            Log.d("DebugHelper", "Invalid simulated SMS: $body")
        }
    }

    fun printDebugInfo() {
        Log.d("DebugHelper", "Current monitored IDs: ${SmsMonitorManager.getMonitors()}")
    }
}
