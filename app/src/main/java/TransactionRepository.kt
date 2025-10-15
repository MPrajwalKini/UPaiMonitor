package com.example.upaimonitor

import android.content.Context
import androidx.lifecycle.MutableLiveData

class TransactionRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).transactionDao()

    // LiveData bridge for SMSReceiver -> ViewModel updates
    val newTransactionLiveData = MutableLiveData<Transaction?>()

    /** Save a transaction in Room DB */
    suspend fun insert(transaction: Transaction) {
        dao.insert(transaction)
    }

    /** Load all saved transactions from DB */
    suspend fun getAll(): List<Transaction> {
        return dao.getAll()
    }

    /** Called from SMSReceiver or DebugHelper */
    fun postNewTransaction(transaction: Transaction) {
        newTransactionLiveData.postValue(transaction)
    }

    /** Clears the LiveData after MainActivity processes it */
    fun clearNewTransaction() {
        newTransactionLiveData.postValue(null)
    }
}
