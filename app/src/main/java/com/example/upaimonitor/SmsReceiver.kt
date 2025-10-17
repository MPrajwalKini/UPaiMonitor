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

class SmsReceiver : BroadcastReceiver() {

    private fun extractUpiReference(message: String): String? {
        val patterns = listOf(
            """UPI\s*Ref(?:\s*No)?[:\s]*(\d+)""".toRegex(RegexOption.IGNORE_CASE),
            """UPI/(\d+)""".toRegex(),
            """Ref(?:\s*No)?[:\s]*(\d+)""".toRegex(RegexOption.IGNORE_CASE),
            """UTR[:\s]*(\d+)""".toRegex(RegexOption.IGNORE_CASE),
            """Transaction\s*ID[:\s]*(\d+)""".toRegex(RegexOption.IGNORE_CASE),
            """Txn\s*(?:ID|No)[:\s]*(\d+)""".toRegex(RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(message)
            if (match != null) {
                val ref = match.groupValues[1]
                Log.d("SmsReceiver", "Found UPI reference: $ref")
                return "UPI$ref"
            }
        }
        return null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bundle: Bundle? = intent.extras
                val pdus = bundle?.get("pdus") as? Array<*>
                if (pdus == null) {
                    Log.e("SmsReceiver", "SMS bundle is null or pdus invalid")
                    return@launch
                }

                val messages = Array(pdus.size) { i ->
                    SmsMessage.createFromPdu(pdus[i] as ByteArray, bundle.getString("format"))
                }

                val messageBody = buildString { for (msg in messages) append(msg?.messageBody) }
                val firstMessage = messages.firstOrNull()
                val sender = firstMessage?.displayOriginatingAddress ?: "Unknown"
                val timestamp = firstMessage?.timestampMillis ?: System.currentTimeMillis()

                val normalizedSender = SmsScanner.extractBankSender(SmsScanner.normalizeAddress(sender))
                Log.d("SmsReceiver", "Received SMS from $sender (full: $normalizedSender, bank: ${SmsScanner.identifyBank(normalizedSender)}): $messageBody")

                // Only process monitored senders
                val monitored = SmsMonitorManager.getMonitorsSynchronously(context)
                val fullNormalizedSender = SmsScanner.normalizeAddress(sender) // "AXCANBNKS"
                if (monitored.none { it.equals(fullNormalizedSender, ignoreCase = true) }) {
                    Log.d("SmsReceiver", "Sender $sender (normalized: $fullNormalizedSender) not in monitored list. Ignored.")
                    return@launch
                }


                // Amount extraction
                val amountRegex = """(?:Rs\.?|₹|INR|XOF)\s*([\d,]+(?:\.\d{1,2})?)""".toRegex()
                val typeRegex = """(?i)\b(credited|debited|paid|sent|received|transfer)\b""".toRegex()

                val amountMatch = amountRegex.find(messageBody)
                val typeMatch = typeRegex.find(messageBody)

                val amount = amountMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                val typeKeyword = typeMatch?.value?.lowercase() ?: ""

                if (amount <= 0) {
                    Log.d("SmsReceiver", "Amount could not be determined. Skipping.")
                    return@launch
                }

                val type = when {
                    typeKeyword.contains("credit") || typeKeyword.contains("received") -> "CREDIT"
                    typeKeyword.contains("debit") || typeKeyword.contains("paid") || typeKeyword.contains("sent") -> "DEBIT"
                    else -> {
                        Log.d("SmsReceiver", "Transaction type could not be determined. Skipping.")
                        return@launch
                    }
                }

                val upiRef = extractUpiReference(messageBody)
                // Use unique ID to avoid conflicts
                val transactionId = upiRef ?: "T${timestamp}_${System.nanoTime()}"

                val transaction = Transaction(
                    transactionId = transactionId,
                    amount = amount,
                    sender = normalizedSender,
                    timestamp = formatTimestamp(timestamp),
                    message = messageBody,
                    transactionType = type
                )

                Log.d("SmsReceiver", "Parsed transaction: $transaction")
                MyApp.repository.insertIfNotExists(transaction)

            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error processing SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return format.format(date)
    }
}
