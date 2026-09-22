package com.masstext.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDao {
    @Query("SELECT * FROM calls ORDER BY date DESC")
    fun getAll(): Flow<List<CallRecord>>

    @Insert
    suspend fun insert(record: CallRecord): Long

    @Query("UPDATE calls SET label = :label WHERE id = :id")
    suspend fun updateLabel(id: Long, label: String)

    @Query("SELECT COUNT(*) FROM calls WHERE success = 1")
    suspend fun countSuccess(): Int
}