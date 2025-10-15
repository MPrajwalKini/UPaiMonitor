package com.example.upaimonitor

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack  // Updated
import androidx.compose.material.icons.filled.Delete
object SmsMonitorManager {

    private const val PREFS_NAME = "SmsMonitorPrefs"
    private const val KEY_MONITORS = "sms_monitors"

    private lateinit var sharedPreferences: SharedPreferences

    private val _monitoredIds = MutableStateFlow<Set<String>>(emptySet())
    val monitoredIds = _monitoredIds.asStateFlow()

    private val defaultMonitors = setOf("AX-FEDBNK-S", "AD-FEDBNK-S", "VM-HDFCBK", "VK-KOTAKB")

    fun init(context: Context) {
        Log.d("SmsMonitorManager", "Initializing SmsMonitorManager...")
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentMonitors = sharedPreferences.getStringSet(KEY_MONITORS, null)

        if (currentMonitors == null) {
            // Synchronous write during first-time setup
            sharedPreferences.edit().putStringSet(KEY_MONITORS, defaultMonitors).commit()
            _monitoredIds.value = defaultMonitors
            Log.d("SmsMonitorManager", "Initialized with default monitors: $defaultMonitors")
        } else {
            _monitoredIds.value = currentMonitors
            Log.d("SmsMonitorManager", "Loaded existing monitors: $currentMonitors")
        }
    }

    private fun ensureInitialized() {
        if (!::sharedPreferences.isInitialized) {
            throw IllegalStateException("SmsMonitorManager not initialized. Call init(context) first (e.g. in Application.onCreate()).")
        }
    }

    fun getMonitors(): Set<String> {
        ensureInitialized()
        return _monitoredIds.value
    }

    // For BroadcastReceiver or background threads
    fun getMonitorsSynchronously(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_MONITORS, emptySet()) ?: emptySet()
    }

    private fun saveMonitors(monitors: Set<String>) {
        ensureInitialized()
        sharedPreferences.edit().putStringSet(KEY_MONITORS, monitors).apply()
        _monitoredIds.value = monitors
        Log.d("SmsMonitorManager", "Updated monitored IDs: $monitors")
    }

    fun addMonitoredId(senderId: String) {
        ensureInitialized()
        val currentMonitors = getMonitors().toMutableSet()
        currentMonitors.add(senderId.uppercase())
        saveMonitors(currentMonitors)
        Log.d("SmsMonitorManager", "Added sender ID: ${senderId.uppercase()}")
    }

    fun removeMonitoredId(senderId: String) {
        ensureInitialized()
        val currentMonitors = getMonitors().toMutableSet()
        currentMonitors.remove(senderId)
        saveMonitors(currentMonitors)
        Log.d("SmsMonitorManager", "Removed sender ID: $senderId")
    }
}