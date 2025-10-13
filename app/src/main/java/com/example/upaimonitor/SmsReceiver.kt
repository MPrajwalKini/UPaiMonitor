package com.example.upaimonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val bundle: Bundle? = intent.extras
        try {
            val pdus = bundle?.get("pdus") as? Array<*>
            if (pdus.isNullOrEmpty()) {
                Log.e("SmsReceiver", "SMS bundle is null or empty")
                return
            }

            val messages = Array(pdus.size) { i ->
                SmsMessage.createFromPdu(pdus[i] as ByteArray, bundle.getString("format"))
            }

            val messageBody = messages.joinToString(separator = "") { it?.messageBody ?: "" }
            val sender = messages.firstOrNull()?.displayOriginatingAddress ?: "Unknown"
            val timestamp = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

            Log.d("SmsReceiver", "Received SMS from $sender: $messageBody")

            // --- Filter senders based on monitored IDs ---
            val monitored = SmsMonitorManager.getMonitorsSynchronously(context)
            if (monitored.none { sender.contains(it, ignoreCase = true) }) {
                Log.d("SmsReceiver", "Sender $sender not in monitored list. Ignored.")
                return
            }

            val lowerMsg = messageBody.lowercase(Locale.getDefault())

            // --- Identify if debit or credit ---
            val isDebit = listOf(
                "debited", "spent", "withdrawn", "purchased", "paid", "sent", "transfer to", "upi debit"
            ).any { it in lowerMsg }

            val isCredit = listOf(
                "credited", "received", "added", "got", "deposit", "incoming", "upi credit"
            ).any { it in lowerMsg }

            // Skip if it's ONLY a balance inquiry (no transaction keywords)
            if (!isDebit && !isCredit) {
                Log.d("SmsReceiver", "No transaction keywords found. Likely balance-only message. Ignored.")
                return
            }

            // --- Extract amount ---
            val regex = Regex("""(?i)(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.\d{1,2})?)""")
            val match = regex.find(messageBody)
            val amount = match?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

            if (amount == null || amount <= 0.0) {
                Log.d("SmsReceiver", "Invalid amount in SMS: $messageBody")
                return
            }

            // --- Extract unique UPI reference ---
            val upiRef = extractUpiReference(messageBody)
            val transactionId = upiRef ?: "T${timestamp}_${sender.hashCode()}"

            // --- Format timestamp properly ---
            val formattedTime = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                .format(Date(timestamp))

            // --- Determine transaction type ---
            val transactionType = if (isCredit) TransactionType.CREDIT else TransactionType.DEBIT

            val transaction = Transaction(
                transactionId = transactionId,
                amount = amount,
                sender = sender,
                timestamp = formattedTime,
                message = messageBody,
                transactionType = transactionType.name
            )

            Log.d("SmsReceiver", "Transaction created: $transaction (Type: $transactionType)")

            // --- Store transaction safely ---
            MyApp.repository.postNewTransaction(transaction)
            CoroutineScope(Dispatchers.IO).launch {
                MyApp.repository.insert(transaction)
            }

        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error reading SMS", e)
        }
    }

    /**
     * Extracts a unique UPI or reference number from message text.
     */
    private fun extractUpiReference(message: String): String? {
        return try {
            val patterns = listOf(
                """UPI\s*Ref(?:\s*No)?[:\s]*(\w+)""".toRegex(RegexOption.IGNORE_CASE),
                """UPI/(\w+)""".toRegex(),
                """Ref(?:\s*No)?[:\s]*(\w+)""".toRegex(RegexOption.IGNORE_CASE),
                """UTR[:\s]*(\w+)""".toRegex(RegexOption.IGNORE_CASE),
                """Transaction\s*ID[:\s]*(\w+)""".toRegex(RegexOption.IGNORE_CASE),
                """Txn\s*(?:ID|No)[:\s]*(\w+)""".toRegex(RegexOption.IGNORE_CASE)
            )
            patterns.firstNotNullOfOrNull { it.find(message)?.groupValues?.get(1) }?.let {
                "UPI$it"
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error extracting UPI reference", e)
            null
        }
    }
}