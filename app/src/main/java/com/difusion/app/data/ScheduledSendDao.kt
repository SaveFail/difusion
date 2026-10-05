package com.difusion.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledSendDao {
    @Query("SELECT * FROM scheduled_sends ORDER BY scheduledAt ASC")
    fun getAll(): Flow<List<ScheduledSend>>

    @Query("SELECT * FROM scheduled_sends WHERE status = 0 AND scheduledAt <= :now ORDER BY scheduledAt ASC")
    suspend fun getDue(now: Long): List<ScheduledSend>

    @Insert
    suspend fun insert(item: ScheduledSend): Long

    @Update
    suspend fun update(item: ScheduledSend)

    @Delete
    suspend fun delete(item: ScheduledSend)

    @Query("UPDATE scheduled_sends SET status = :status, sentAt = :sentAt, result = :result WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int, sentAt: Long = 0L, result: String = "")

    /** Marca el envío como "en proceso" (4) solo si seguía pendiente (0).
     *  Devuelve 1 si este worker se quedó con él (evita envíos duplicados). */
    @Query("UPDATE scheduled_sends SET status = 4 WHERE id = :id AND status = 0")
    suspend fun claim(id: Long): Int

    @Query("DELETE FROM scheduled_sends WHERE status = 1")
    suspend fun clearSent()
}
