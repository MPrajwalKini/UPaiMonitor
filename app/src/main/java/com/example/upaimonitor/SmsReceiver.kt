package com.example.upaimonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log
import com.example.upaimonitor.Transaction
import kotlinx.coroutines.CoroutineScope // Added from Code 2
import kotlinx.coroutines.Dispatchers // Added from Code 2
import kotlinx.coroutines.launch // Added from Code 2
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {

    // --- Helper functions from Code 1 (retained) ---
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
    // --------------------------------------------------

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        // 🟢 FEATURE 1: Use goAsync and Coroutines for background processing (from Code 2)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bundle: Bundle? = intent.extras
                val pdus = bundle?.get("pdus") as? Array<*>
                if (pdus == null) {
                    Log.e("SmsReceiver", "SMS bundle is null or pdus is wrong type")
                    return@launch // Return from coroutine
                }

                val messages = Array(pdus.size) { i ->
                    SmsMessage.createFromPdu(pdus[i] as ByteArray, bundle.getString("format"))
                }

                val messageBody = buildString {
                    for (msg in messages) append(msg?.messageBody)
                }

                val firstMessage = messages.firstOrNull()
                val sender = firstMessage?.displayOriginatingAddress ?: "Unknown"
                val timestamp = firstMessage?.timestampMillis ?: System.currentTimeMillis()

                // 🔹 Normalize the sender to core bank ID (e.g., "CANBNK" from "VA-CANBNK-S")
                val normalizedSender = SmsScanner.run {
                    extractBankSender(normalizeAddress(sender))
                }

                Log.d("SmsReceiver", "Received SMS from $sender (normalized: $normalizedSender): $messageBody")

                // Filter only messages from monitored senders (Code 1 logic retained)
                val monitored = SmsMonitorManager.getMonitorsSynchronously(context)
                val bankCode = SmsScanner.extractBankSender(normalizedSender)

                if (monitored.none { bankCode.contains(it, ignoreCase = true) || it.contains(bankCode, ignoreCase = true) }) {
                    Log.d("SmsReceiver", "Sender $sender (normalized: $bankCode) not in monitored list. Ignored.")
                    return@launch
                }

                // 🟢 FEATURE 2: More robust regex that extracts amount AND transaction type (Merged from Code 2 logic)
                // New regex combines Code 1's amount extraction with Code 2's type keywords
                val regex = Regex("""(?i)(credited|debited|paid|sent|received|transfer).*?(?:Rs\.?|₹|INR|XOF)\s*([\d,]+(?:\.\d{1,2})?)""")
                val match = regex.find(messageBody)

                val amount = match?.groupValues?.get(2)?.replace(",", "")?.toDoubleOrNull() ?: 0.0

                // Determine transaction type based on keyword
                val typeKeyword = match?.groupValues?.get(1)?.lowercase(Locale.getDefault()) ?: ""
                val type = if (typeKeyword.contains("credit") || typeKeyword.contains("received") || typeKeyword.contains("transfer")) {
                    "CREDIT"
                } else if (typeKeyword.contains("debit") || typeKeyword.contains("paid") || typeKeyword.contains("sent")) {
                    "DEBIT"
                } else {
                    // Fallback or skip if type cannot be determined
                    Log.d("SmsReceiver", "Could not determine transaction type. Skipping.")
                    return@launch
                }


                val upiRef = extractUpiReference(messageBody)

                Log.d("SmsReceiver", "Amount: $amount, UPI Ref: $upiRef, Type: $type")

                if (amount > 0) {
                    val transactionId = upiRef ?: "T${timestamp}"
                    val finalAmount = if (type == "DEBIT") -amount else amount // Apply sign for internal storage

                    val transaction = Transaction(
                        transactionId = transactionId,
                        amount = finalAmount, // Use signed amount based on extracted type
                        sender = normalizedSender,
                        timestamp = formatTimestamp(timestamp),
                        message = messageBody,
                        // Assuming Transaction class needs 'type' field which is essential for UI
                        type = type
                    )
                    // Code 2 style insertion (insertIfNotExists) can be used here if it simplifies logic,
                    // but keeping the original postNewTransaction call from Code 1, assuming Repository handles it
                    MyApp.repository.postNewTransaction(transaction)
                    Log.d("SmsReceiver", "Transaction added: $transaction")
                }

            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error reading/processing SMS", e)
            } finally {
                pendingResult.finish() // Must be called after all work is done
            }
        }
    }
}