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

            val messageBody = buildString {
                for (msg in messages) append(msg?.messageBody)
            }

            val sender = messages.firstOrNull()?.displayOriginatingAddress ?: "Unknown"
            val timestamp = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

            // 🔹 Normalize the sender to core bank ID (e.g., "CANBNK" from "VA-CANBNK-S")
            val normalizedSender = SmsScanner.run {
                extractBankSender(normalizeAddress(sender))
            }

            Log.d("SmsReceiver", "Received SMS from $sender (normalized: $normalizedSender): $messageBody")

            // Filter only messages from monitored senders
            val monitored = SmsMonitorManager.getMonitorsSynchronously(context)
            val bankCode = SmsScanner.extractBankSender(normalizedSender)

            if (monitored.none { bankCode.contains(it, ignoreCase = true) || it.contains(bankCode, ignoreCase = true) }) {
                Log.d("SmsReceiver", "Sender $sender (normalized: $bankCode) not in monitored list. Ignored.")
                return
            }

            // Parse transaction amount
            val regex = Regex("""(?i)(?:Rs\.?|INR|₹)\s*([0-9,.]+)""")
            val match = regex.find(messageBody)
            val amount = match?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0

            val upiRef = extractUpiReference(messageBody)

            Log.d("SmsReceiver", "Amount: $amount, UPI Ref: $upiRef")

            if (amount > 0) {
                val transactionId = upiRef ?: "T${timestamp}"
                val transaction = Transaction(
                    transactionId = transactionId,
                    amount = amount,
                    sender = normalizedSender, // ✅ Store normalized name
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

    /**
     * Extract UPI reference number from SMS
     */
    private fun extractUpiReference(message: String): String? {
        try {
            val patterns = listOf(
                """UPI\s*Ref(?:\s*No)?[:\s]*(\d+)""".toRegex(RegexOption.IGNORE_CASE),
                """UPI/(\d+)""".toRegex(),
                """Ref(?:\s*No)?[:\s]*(\d+)""".toRegex(RegexOption.IGNORE_CASE),
                """UTR[:\s]*(\d+)""".toRegex(RegexOption.IGNORE_CASE),
                """Transaction\s*ID[:\s]*(\d+)""".toRegex(RegexOption.IGNORE_CASE),
                """Txn\s*(?:ID|No)[:\s]*(\d+)""".toRegex(RegexOption.IGNORE_CASE)
            )

            for (pattern in patterns) {
                val matchResult = pattern.find(message)
                if (matchResult != null) {
                    val ref = matchResult.groupValues[1]
                    Log.d("SmsReceiver", "Found UPI reference: $ref")
                    return "UPI$ref"
                }
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error extracting UPI reference", e)
        }
        return null
    }

    private fun formatTimestamp(timestamp: Long): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        return format.format(date)
    }
}
