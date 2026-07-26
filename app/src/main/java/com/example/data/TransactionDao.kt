package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE status = :status ORDER BY timestamp DESC")
    fun getTransactionsByStatus(status: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY timestamp DESC")
    fun getTransactionsByType(type: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()

    // Matcher helper query: find an un-matched SMS or receipt with same amount or matching ref code
    @Query("SELECT * FROM transactions WHERE type = 'SMS' AND status = 'PENDING' AND (amountRial = :amountRial OR (trackingNumber != '' AND trackingNumber = :refNumber)) ORDER BY timestamp DESC LIMIT 1")
    suspend fun findMatchingSms(amountRial: Long, refNumber: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE type = 'RECEIPT' AND status = 'PENDING' AND (amountRial = :amountRial OR (trackingNumber != '' AND trackingNumber = :refNumber)) ORDER BY timestamp DESC LIMIT 1")
    suspend fun findPendingReceipt(amountRial: Long, refNumber: String): TransactionEntity?
}
