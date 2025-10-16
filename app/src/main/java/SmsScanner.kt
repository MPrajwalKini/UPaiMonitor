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

    private const val TAG = "SmsScanner"

    private val BANK_KEYWORDS = listOf(
        "debited", "credited", "account", "balance", "upi", "transfer",
        "payment", "transaction", "bank", "a/c", "ac", "inr", "rs.",
        "withdrawn", "deposited", "paytm", "phonepe", "gpay", "amazon pay", "paid"
    )

    private val BANK_NAMES = mapOf(
        "HDFC" to listOf("HDFCBK", "HDFC", "HDFCBANK"),
        "SBI" to listOf("SBIINB", "SBICRD", "SBI", "STBANK"),
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
        Log.d(TAG, "Starting SMS scan...")
        val detectedSenders = mutableMapOf<String, DetectedSenderData>()

        try {
            val uri = Uri.parse("content://sms/inbox")
            Log.d(TAG, "Querying SMS inbox: $uri")

            val cursor: Cursor? = context.contentResolver.query(
                uri,
                arrayOf("address", "body", "date"),
                null,
                null,
                "date DESC LIMIT 1000" // Scan last 1000 messages
            )

            if (cursor == null) {
                Log.e(TAG, "Cursor is null - permission might not be granted or no SMS access")
                return emptyList()
            }

            cursor.use {
                val addressIndex = it.getColumnIndex("address")
                val bodyIndex = it.getColumnIndex("body")
                val dateIndex = it.getColumnIndex("date")

                Log.d(TAG, "Column indices - address: $addressIndex, body: $bodyIndex, date: $dateIndex")

                if (addressIndex == -1 || bodyIndex == -1 || dateIndex == -1) {
                    Log.e(TAG, "Invalid column indices - SMS permission might not be properly granted")
                    return emptyList()
                }

                val totalMessages = it.count
                Log.d(TAG, "Total messages to scan: $totalMessages")

                var scannedCount = 0
                var bankingMessagesFound = 0

                while (it.moveToNext()) {
                    scannedCount++

                    val address = it.getString(addressIndex)
                    val body = it.getString(bodyIndex)
                    val date = it.getLong(dateIndex)

                    if (address == null || body == null) {
                        continue
                    }

                    // Check if this looks like a banking SMS
                    if (isBankingMessage(body)) {
                        bankingMessagesFound++
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

                        // Log first few banking messages for debugging
                        if (bankingMessagesFound <= 3) {
                            Log.d(TAG, "Banking SMS found from $normalizedAddress: ${body.take(50)}...")
                        }
                    }
                }

                Log.d(TAG, "Scanned $scannedCount messages, found $bankingMessagesFound banking messages")
                Log.d(TAG, "Unique banking senders: ${detectedSenders.size}")
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException - SMS permission not granted", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning SMS", e)
            throw e
        }

        // Convert to list and identify bank names
        val result = detectedSenders.map { (senderId, data) ->
            DetectedSender(
                senderId = senderId,
                sampleMessage = data.sampleMessage.take(150), // Truncate for display
                count = data.count,
                lastReceived = data.lastReceived,
                bankName = identifyBank(senderId)
            )
        }.sortedByDescending { it.count } // Sort by frequency

        Log.d(TAG, "Returning ${result.size} detected senders")
        return result
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
     * Made public for use in SmsReceiver
     */
    fun normalizeAddress(address: String): String {
        // Remove +91, spaces, hyphens
        return address.replace("+91", "")
            .replace(" ", "")
            .replace("-", "")
            .trim()
    }

    /**
     * Extracts the bank name from sender ID
     * Used by SmsReceiver to get clean bank identifier
     */
    fun extractBankSender(address: String): String {
        val upper = address.uppercase()
        for ((_, identifiers) in BANK_NAMES) {
            for (id in identifiers) {
                if (upper.contains(id)) {
                    return id // "CANBNK", "HDFCBK", etc.
                }
            }
        }
        return upper.takeLast(6) // fallback
    }



    /**
     * Identifies the bank name from sender ID
     */
    fun identifyBank(senderId: String): String {
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