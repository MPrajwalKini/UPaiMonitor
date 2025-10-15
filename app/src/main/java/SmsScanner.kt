package com.example.upaimonitor

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log

data class DetectedSender(
    val senderId: String,
    val sampleMessage: String,
    val count: Int,
    val lastReceived: Long,
    val bankName: String = ""
)

object SmsScanner {

    private val BANK_KEYWORDS = listOf(
        "debited", "credited", "account", "balance", "upi", "transfer",
        "payment", "transaction", "bank", "a/c", "ac", "inr", "rs.",
        "withdrawn", "deposited", "paytm", "phonepe", "gpay", "amazon pay"
    )

    private val BANK_NAMES = mapOf(
        "HDFC" to listOf("HDFCBK", "HDFC", "HDFCBANK"),
        "SBI" to listOf("SBIINB", "SBICRD", "SBI", "STbank"),
        "ICICI" to listOf("ICICIB", "ICICI", "ICICIBK"),
        "AXIS" to listOf("AXISBK", "AXIS"),
        "Kotak" to listOf("KOTAKB", "KOTAK"),
        "PNB" to listOf("PNBSMS", "PNB"),
        "BOB" to listOf("BOBTXN", "BOB"),
        "Canara" to listOf("CBSMS", "CANARA","CANBNK"),
        "NKGSB" to listOf("NKGSB"),
        "Union" to listOf("UBISMS", "UNION"),
        "IDBI" to listOf("IDBIBN", "IDBI"),
        "Federal" to listOf("FEDBNK", "FEDBK"),
        "IndusInd" to listOf("INDBNK", "INDUS"),
        "YES Bank" to listOf("YESBK", "YES"),
        "PayTM" to listOf("PAYTM"),
        "PhonePe" to listOf("PHONPE", "PHNPE"),
        "Google Pay" to listOf("GPAY", "GOOGLEPAY"),
        "Amazon Pay" to listOf("AMAZON", "AZNPAY")
    )

    /**
     * Scans SMS inbox and returns all detected banking SMS senders
     */
    fun scanBankingSenders(context: Context): List<DetectedSender> {
        val detectedSenders = mutableMapOf<String, DetectedSenderData>()

        try {
            val uri = Uri.parse("content://sms/inbox")
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                arrayOf("address", "body", "date"),
                null,
                null,
                "date DESC LIMIT 1000" // Scan last 1000 messages
            )

            cursor?.use {
                val addressIndex = it.getColumnIndex("address")
                val bodyIndex = it.getColumnIndex("body")
                val dateIndex = it.getColumnIndex("date")

                while (it.moveToNext()) {
                    val address = it.getString(addressIndex) ?: continue
                    val body = it.getString(bodyIndex) ?: continue
                    val date = it.getLong(dateIndex)

                    // Check if this looks like a banking SMS
                    if (isBankingMessage(body)) {
                        val normalizedAddress = normalizeAddress(address)

                        if (detectedSenders.containsKey(normalizedAddress)) {
                            detectedSenders[normalizedAddress]!!.count++
                            // Keep the most recent message as sample
                            if (date > detectedSenders[normalizedAddress]!!.lastReceived) {
                                detectedSenders[normalizedAddress]!!.sampleMessage = body
                                detectedSenders[normalizedAddress]!!.lastReceived = date
                            }
                        } else {
                            detectedSenders[normalizedAddress] = DetectedSenderData(
                                sampleMessage = body,
                                count = 1,
                                lastReceived = date
                            )
                        }
                    }
                }
            }

            Log.d("SmsScanner", "Found ${detectedSenders.size} banking SMS senders")

        } catch (e: Exception) {
            Log.e("SmsScanner", "Error scanning SMS", e)
        }

        // Convert to list and identify bank names
        return detectedSenders.map { (senderId, data) ->
            DetectedSender(
                senderId = senderId,
                sampleMessage = data.sampleMessage.take(150), // Truncate for display
                count = data.count,
                lastReceived = data.lastReceived,
                bankName = identifyBank(senderId)
            )
        }.sortedByDescending { it.count } // Sort by frequency
    }

    /**
     * Checks if a message looks like a banking/financial SMS
     */
    private fun isBankingMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return BANK_KEYWORDS.any { lowerMessage.contains(it) }
    }

    /**
     * Normalizes SMS address (remove country codes, spaces, etc.)
     */
    private fun normalizeAddress(address: String): String {
        // Remove +91, spaces, hyphens
        return address.replace("+91", "")
            .replace(" ", "")
            .replace("-", "")
            .trim()
    }

    /**
     * Identifies the bank name from sender ID
     */
    private fun identifyBank(senderId: String): String {
        val upperSenderId = senderId.uppercase()

        for ((bankName, identifiers) in BANK_NAMES) {
            if (identifiers.any { upperSenderId.contains(it) }) {
                return bankName
            }
        }

        return "Other"
    }

    // Helper data class for building the map
    private data class DetectedSenderData(
        var sampleMessage: String,
        var count: Int,
        var lastReceived: Long
    )
}