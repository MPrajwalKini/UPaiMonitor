package com.example.upaimonitor

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DebugHelper {
    fun simulateSMS(sender: String, body: String) {
        val regex = Regex("""(?i)(?:Rs\.?|INR)\s*([0-9,.]+)""")
        val match = regex.find(body)
        val amount = match?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0

        if (amount > 0) {
            val transaction = Transaction(
                amount = amount,
                sender = sender,
                timestamp = System.currentTimeMillis().toString(),
                message = body
            )
            // Insert transaction asynchronously
            CoroutineScope(Dispatchers.IO).launch {
                MyApp.repository.insertIfNotExists(transaction)
                Log.d("DebugHelper", "Simulated transaction inserted: $transaction")
            }
        } else {
            Log.d("DebugHelper", "Invalid simulated SMS: $body")
        }
    }

    fun printDebugInfo() {
        Log.d("DebugHelper", "Current monitored IDs: ${SmsMonitorManager.getMonitors()}")
    }
}