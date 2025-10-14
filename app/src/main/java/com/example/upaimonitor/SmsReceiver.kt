package com.example.upaimonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log
import android.content.IntentFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val bundle = intent.extras
        try {
            // Safe PDU extraction (works on all SDKs)
            val pdus = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                bundle?.getParcelableArray("pdus", ByteArray::class.java)
            } else {
                @Suppress("DEPRECATION")
                bundle?.get("pdus") as? Array<*>
            }

            if (pdus.isNullOrEmpty()) {
                Log.e("SmsReceiver", "SMS bundle is null or empty")
                return
            }

            val format = bundle?.getString("format")
            val messages = Array(pdus.size) { i ->
                SmsMessage.createFromPdu(pdus[i] as ByteArray, format)
            }

            val messageBody = messages.joinToString("") { it.messageBody ?: "" }
            val sender = messages.firstOrNull()?.displayOriginatingAddress ?: "Unknown"
            val timestamp = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

            Log.d("SmsReceiver", "Received SMS from $sender: $messageBody")

            // Filter monitored senders
            val monitored = SmsMonitorManager.getMonitorsSynchronously(context)
            if (monitored.none { sender.contains(it, ignoreCase = true) }) {
                Log.d("SmsReceiver", "Sender $sender not in monitored list — ignored")
                return
            }

            val lowerMsg = messageBody.lowercase(Locale.getDefault())

            val isDebit = listOf(
                "debited", "spent", "withdrawn", "purchased", "paid", "sent",
                "transfer to", "upi debit"
            ).any { it in lowerMsg }

            val isCredit = listOf(
                "credited", "received", "added", "got", "deposit", "incoming", "upi credit"
            ).any { it in lowerMsg }

            if (!isDebit && !isCredit) {
                Log.d("SmsReceiver", "Ignored balance/info message (no txn keywords)")
                return
            }

            val regex = Regex("""(?i)(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.\d{1,2})?)""")
            val match = regex.find(messageBody)
            val amount = match?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            if (amount == null || amount <= 0.0) {
                Log.d("SmsReceiver", "Invalid or missing amount — skipped")
                return
            }

            val upiRef = extractUpiReference(messageBody)
            val transactionId = upiRef ?: "T${timestamp}_${sender.hashCode()}"
            val formattedTime = SimpleDateFormat("MMM dd yyyy, hh:mm a", Locale.getDefault())
                .format(Date(timestamp))
            val transactionType = if (isCredit) TransactionType.CREDIT else TransactionType.DEBIT

            val transaction = Transaction(
                transactionId = transactionId,
                amount = amount,
                sender = sender,
                timestamp = formattedTime,
                message = messageBody,
                transactionType = transactionType.name
            )

            Log.d("SmsReceiver", "Parsed $transactionType transaction: $transaction")

            MyApp.repository.postNewTransaction(transaction)
            CoroutineScope(Dispatchers.IO).launch {
                if (!MyApp.repository.isDuplicateTransaction(transaction)) {
                    MyApp.repository.insert(transaction)
                } else {
                    Log.d("SmsReceiver", "Duplicate transaction skipped: $transactionId")
                }
            }

        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error reading SMS", e)
        }
    }


    /** Extract UPI or reference number from text */
    private fun extractUpiReference(message: String): String? {
        return try {
            val patterns = listOf(
                """UPI\s*Ref(?:\s*No)?[:\s]*(\w+)""",
                """UPI/(\w+)""",
                """Ref(?:\s*No)?[:\s]*(\w+)""",
                """UTR[:\s]*(\w+)""",
                """Transaction\s*ID[:\s]*(\w+)""",
                """Txn\s*(?:ID|No)[:\s]*(\w+)"""
            ).map { it.toRegex(RegexOption.IGNORE_CASE) }

            patterns.firstNotNullOfOrNull { it.find(message)?.groupValues?.get(1) }?.let {
                "UPI$it"
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error extracting UPI reference", e)
            null
        }
    }
    companion object {
        private var isRegistered = false
        private var receiverInstance: SmsReceiver? = null

        /** Dynamically register SMS receiver */
        fun register(context: Context) {
            if (isRegistered) return
            val receiver = SmsReceiver()
            val filter = IntentFilter("android.provider.Telephony.SMS_RECEIVED")
            context.registerReceiver(receiver, filter)
            receiverInstance = receiver
            isRegistered = true
            Log.d("SmsReceiver", "SMS monitoring started")
        }

        /** Unregister SMS receiver */
        fun unregister(context: Context) {
            if (!isRegistered || receiverInstance == null) return
            try {
                context.unregisterReceiver(receiverInstance)
                isRegistered = false
                receiverInstance = null
                Log.d("SmsReceiver", "SMS monitoring stopped")
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error unregistering receiver", e)
            }
            isRegistered = false
            receiverInstance = null
        }
    }
}
