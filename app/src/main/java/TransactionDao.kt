package com.example.upaimonitor

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction)

    @Query("SELECT * FROM transactions ORDER BY transactionId DESC")
    suspend fun getAll(): List<Transaction>
}
