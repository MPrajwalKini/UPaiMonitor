package com.example.upaimonitor

import android.content.Context
import android.content.SharedPreferences

object MonitorManager {
    private const val PREFS_NAME = "monitor_prefs"
    private const val KEY_SENDERS = "monitored_senders"

    // Predefined bank sender patterns
    private val BANK_NAMES = mapOf(
        "HDFC" to listOf("HDFCBK", "HDFC", "HDFCBANK"),
        "SBI" to listOf("SBIINB", "SBICRD", "SBI", "STBANK"),
        "ICICI" to listOf("ICICIB", "ICICI", "ICICIBK"),
        "AXIS" to listOf("AXISBK", "AXIS"),
        "Kotak" to listOf("KOTAKB", "KOTAK"),
        "PNB" to listOf("PNBSMS", "PNB"),
        "BOB" to listOf("BOBTXN", "BOB"),
        "Canara" to listOf("CBSMS", "CANARA", "CANBNK"),
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

    /** Returns all monitored senders, auto-preloading common bank senders */
    fun getMonitoredSenders(context: Context): List<String> {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SENDERS, "") ?: ""
        val userSenders = if (saved.isNotEmpty()) saved.split(",").map { it.uppercase() } else emptyList()

        // Flatten BANK_NAMES values and normalize
        val bankSenders = BANK_NAMES.values.flatten().map { it.replace("-", "").uppercase() }

        // Merge user + bank senders, avoid duplicates
        return (userSenders + bankSenders).distinct()
    }

    /** Add a custom sender manually if needed */
    fun addSender(context: Context, sender: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getMonitoredSenders(context).toMutableList()
        val normalized = sender.replace("-", "").uppercase()
        if (!current.contains(normalized)) {
            current.add(normalized)
            prefs.edit().putString(KEY_SENDERS, current.joinToString(",")).apply()
        }
    }

    /** Clear only user-added senders; predefined bank senders remain */
    fun clearUserSenders(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_SENDERS).apply()
    }
}
