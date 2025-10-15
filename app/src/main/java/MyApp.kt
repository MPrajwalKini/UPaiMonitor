package com.example.upaimonitor

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyApp : Application() {

    companion object {
        lateinit var repository: TransactionRepository
            private set
    }

    override fun onCreate() {
        super.onCreate()

        try {
            // Initialize the SMS monitor (SharedPreferences setup)
            SmsMonitorManager.init(this)
            Log.d("MyApp", "SmsMonitorManager initialized successfully.")

            // Initialize Room database + repository
            val db = AppDatabase.getInstance(this)
            repository = TransactionRepository(this)

            // Optional: Preload transactions into memory cache if desired
            CoroutineScope(Dispatchers.IO).launch {
                val storedCount = repository.getAll().size
                Log.d("MyApp", "Loaded $storedCount stored transactions from DB at startup.")
            }

        } catch (e: Exception) {
            Log.e("MyApp", "Initialization failed", e)
        }
    }
}