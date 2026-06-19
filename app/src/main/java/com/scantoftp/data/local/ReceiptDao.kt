package com.scantoftp.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {
    @Query("SELECT * FROM receipts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ReceiptEntity>>

    @Insert
    suspend fun insert(entity: ReceiptEntity): Long

    @Update
    suspend fun update(entity: ReceiptEntity)

    @Query("SELECT * FROM receipts WHERE id = :receiptId")
    suspend fun getById(receiptId: Long): ReceiptEntity?

    @Query("SELECT * FROM receipts WHERE status IN ('Pending', 'Failed', 'Uploading') ORDER BY createdAt ASC")
    suspend fun getRetryable(): List<ReceiptEntity>

    @Query("SELECT * FROM receipts WHERE status = 'Completed'")
    suspend fun getCompleted(): List<ReceiptEntity>

    @Query("SELECT COUNT(*) FROM receipts WHERE status = 'Pending' OR status = 'Uploading'")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM receipts WHERE fileName = :fileName")
    suspend fun countByFileName(fileName: String): Int

    @Query("DELETE FROM receipts WHERE id = :receiptId")
    suspend fun deleteById(receiptId: Long)
}
