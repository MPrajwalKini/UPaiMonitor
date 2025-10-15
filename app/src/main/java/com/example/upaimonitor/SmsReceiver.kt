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
import java.util.*

class SmsReceiver : BroadcastReceiver() {

    private val TAG = "SmsReceiver"
    private val MONITORED_SENDERS = listOf("CANARA", "HDFC", "SBI", "ICICI")

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val bundle = intent.extras
        if (bundle == null) {
            pendingResult.finish()
            return
        }

        val pdus = bundle.get("pdus") as? Array<ByteArray>
        if (pdus == null) {
            pendingResult.finish()
            return
        }

        val messages = pdus.mapNotNull {
            SmsMessage.createFromPdu(it, bundle.getString("format"))
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (msg in messages) {
                    val sender = msg.displayOriginatingAddress ?: continue
                    val body = msg.messageBody ?: continue
                    val normalizedSender = sender.uppercase(Locale.getDefault())
                    if (!MONITORED_SENDERS.any { normalizedSender.contains(it) }) continue

                    Log.d(TAG, "Sender matches monitored list: $sender")

                    val regex = Regex("""(?i)(credited|debited|paid|sent|received).*?(?:Rs\.?|₹|INR)\s*([\d,]+(?:\.\d{1,2})?)""")
                    val match = regex.find(body)
                    if (match != null) {
                        val typeKeyword = match.groupValues[1].lowercase(Locale.getDefault())
                        val amount = match.groupValues[2].replace(",", "").toDoubleOrNull() ?: 0.0
                        val type = if (typeKeyword.contains("credit") || typeKeyword.contains("received")) "Credit" else "Debit"

                        val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
                        val timestamp = sdf.format(Date())

                        val transaction = Transaction(
                            transactionId = "${sender}_${System.currentTimeMillis()}",
                            sender = sender,
                            amount = amount,
                            transactionType = type,
                            message = body,
                            timestamp = timestamp
                        )

                        // Use the shared repository instance
                        MyApp.repository.insertIfNotExists(transaction)
                        Log.d(TAG, "Transaction saved: $transaction")
                    } else {
                        Log.d(TAG, "No transaction pattern matched in message.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing transaction: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
