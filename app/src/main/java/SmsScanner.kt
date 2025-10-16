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
        "withdrawn", "deposited", "paytm", "phonepe", "gpay", "amazon pay", "paid"
    )

    private val BANK_NAMES = mapOf(
        "HDFC" to listOf("HDFCBK", "HDFC", "HDFCBANK"),
        "SBI" to listOf("SBIINB", "SBICRD", "SBI", "STBANK"),
        "ICICI" to listOf("ICICIB", "ICICI", "ICICIBK"),
        "AXIS" to listOf("AXISBK", "AXIS"),
        "KOTAK" to listOf("KOTAKB", "KOTAK"),
        "PNB" to listOf("PNBSMS", "PNB"),
        "BOB" to listOf("BOBTXN", "BOB"),
        "CANARA" to listOf("CBSMS", "CANARA", "CANBNK"),
        "NKGSB" to listOf("NKGSB"),
        "UNION" to listOf("UBISMS", "UNION"),
        "IDBI" to listOf("IDBIBN", "IDBI"),
        "FEDERAL" to listOf("FEDBNK", "FEDBK"),
        "INDUSIND" to listOf("INDBNK", "INDUS"),
        "YES BANK" to listOf("YESBK", "YES"),
        "PAYTM" to listOf("PAYTM"),
        "PHONEPE" to listOf("PHONPE", "PHNPE"),
        "GOOGLE PAY" to listOf("GPAY", "GOOGLEPAY"),
        "AMAZON PAY" to listOf("AMAZON", "AZNPAY")
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
                "date DESC LIMIT 1000"
            )

            cursor?.use {
                val addressIndex = it.getColumnIndex("address")
                val bodyIndex = it.getColumnIndex("body")
                val dateIndex = it.getColumnIndex("date")

                while (it.moveToNext()) {
                    val address = it.getString(addressIndex) ?: continue
                    val body = it.getString(bodyIndex) ?: continue
                    val date = it.getLong(dateIndex)

                    if (isBankingMessage(body)) {
                        val normalizedAddress = normalizeAddress(address)

                        // 🔹 Extract core sender id (e.g. HDFCBK from VM-HDFCBK)
                        val bankKey = extractBankSender(normalizedAddress)

                        if (detectedSenders.containsKey(bankKey)) {
                            val data = detectedSenders[bankKey]!!
                            data.count++
                            if (date > data.lastReceived) {
                                data.sampleMessage = body
                                data.lastReceived = date
                            }
                        } else {
                            detectedSenders[bankKey] = DetectedSenderData(
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

        return detectedSenders.map { (senderId, data) ->
            DetectedSender(
                senderId = senderId,
                sampleMessage = data.sampleMessage.take(150),
                count = data.count,
                lastReceived = data.lastReceived,
                bankName = identifyBank(senderId)
            )
        }.sortedByDescending { it.count }
    }

    private fun isBankingMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return BANK_KEYWORDS.any { lowerMessage.contains(it) }
    }

    fun normalizeAddress(address: String): String {
        return address.replace("+91", "")
            .replace(" ", "")
            .replace("-", "")
            .trim()
    }

    // 🔹 NEW: Extract main bank-related part from sender ID
    fun extractBankSender(address: String): String {
        val upper = address.uppercase()
        // Try to match known patterns from BANK_NAMES
        for ((_, identifiers) in BANK_NAMES) {
            for (id in identifiers) {
                if (upper.contains(id)) {
                    return id // return the core part like "HDFCBK"
                }
            }
        }
        // fallback if no match
        return upper.takeLast(6) // take last 6 chars, often unique for banks
    }

    private fun identifyBank(senderId: String): String {
        val upperSenderId = senderId.uppercase()
        for ((bankName, identifiers) in BANK_NAMES) {
            if (identifiers.any { upperSenderId.contains(it) }) {
                return bankName
            }
        }
        return "Other"
    }

    private data class DetectedSenderData(
        var sampleMessage: String,
        var count: Int,
        var lastReceived: Long
    )


}
