package com.example.upaimonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val bundle: Bundle? = intent.extras
        val messages: Array<SmsMessage?>
        try {
            val pdus = bundle?.get("pdus") as? Array<*>
            if (pdus == null) {
                Log.e("SmsReceiver", "SMS bundle is null")
                return
            }

            messages = Array(pdus.size) { i ->
                SmsMessage.createFromPdu(pdus[i] as ByteArray, bundle.getString("format"))
            }

            // Combine multipart message text
            val messageBody = buildString {
                for (msg in messages) append(msg?.messageBody)
            }

            val sender = messages.firstOrNull()?.displayOriginatingAddress ?: "Unknown"
            val timestamp = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

            Log.d("SmsReceiver", "Received SMS from $sender: $messageBody")

            // Filter only messages from monitored senders
            val monitored = SmsMonitorManager.getMonitorsSynchronously(context)
            if (monitored.none { sender.contains(it, ignoreCase = true) }) {
                Log.d("SmsReceiver", "Sender $sender not in monitored list. Ignored.")
                return
            }

            // Parse transaction amount (basic pattern matching)
            val regex = Regex("""(?i)(?:Rs\.?|INR)\s*([0-9,.]+)""")
            val match = regex.find(messageBody)
            val amount = match?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0

            // If valid, push transaction to repository
            if (amount > 0) {
                val transaction = Transaction(
                    amount = amount,
                    sender = sender,
                    timestamp = formatTimestamp(timestamp),
                    message = messageBody
                )
                MyApp.repository.postNewTransaction(transaction)
                Log.d("SmsReceiver", "Transaction added: $transaction")
            }

        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error reading SMS", e)
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("MMM dd, hh:mm a", Locale.ENGLISH)
        return format.format(date)
    }
}
