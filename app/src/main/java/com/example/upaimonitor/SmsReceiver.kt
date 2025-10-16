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

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bundle: Bundle? = intent.extras
                val pdus = bundle?.get("pdus") as? Array<*>
                if (pdus == null) {
                    Log.e("SmsReceiver", "SMS bundle is null or pdus is wrong type")
                    return@launch
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

                // Normalize the sender to core bank ID
                val normalizedSender = SmsScanner.run {
                    extractBankSender(normalizeAddress(sender))
                }

                Log.d("SmsReceiver", "Received SMS from $sender (normalized: $normalizedSender): $messageBody")

                // Filter only messages from monitored senders
                val monitored = SmsMonitorManager.getMonitorsSynchronously(context)
                val bankCode = SmsScanner.extractBankSender(normalizedSender)

                if (monitored.none { bankCode.contains(it, ignoreCase = true) || it.contains(bankCode, ignoreCase = true) }) {
                    Log.d("SmsReceiver", "Sender $sender (normalized: $bankCode) not in monitored list. Ignored.")
                    return@launch
                }

                // Extract amount and transaction type
                val regex = Regex("""(?i)(credited|debited|paid|sent|received|transfer).*?(?:Rs\.?|₹|INR|XOF)\s*([\d,]+(?:\.\d{1,2})?)""")
                val match = regex.find(messageBody)

                val amount = match?.groupValues?.get(2)?.replace(",", "")?.toDoubleOrNull() ?: 0.0

                // Determine transaction type based on keyword
                val typeKeyword = match?.groupValues?.get(1)?.lowercase() ?: ""
                val type = if (typeKeyword.contains("credit") || typeKeyword.contains("received") || typeKeyword.contains("transfer")) {
                    "CREDIT"
                } else if (typeKeyword.contains("debit") || typeKeyword.contains("paid") || typeKeyword.contains("sent")) {
                    "DEBIT"
                } else {
                    Log.d("SmsReceiver", "Could not determine transaction type. Skipping.")
                    return@launch
                }

                val upiRef = extractUpiReference(messageBody)

                Log.d("SmsReceiver", "Amount: $amount, UPI Ref: $upiRef, Type: $type")

                if (amount > 0) {
                    val transactionId = upiRef ?: "T${timestamp}"

                    val transaction = Transaction(
                        transactionId = transactionId,
                        amount = amount, // ✅ Always positive - type stored separately
                        sender = normalizedSender,
                        timestamp = formatTimestamp(timestamp),
                        message = messageBody,
                        transactionType = type // ✅ Fixed field name
                    )

                    MyApp.repository.insertIfNotExists(transaction) // ✅ Correct method name
                    Log.d("SmsReceiver", "Transaction added: $transaction")
                }

            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error reading/processing SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}