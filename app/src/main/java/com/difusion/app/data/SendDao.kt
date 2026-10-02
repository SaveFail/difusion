package com.difusion.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SendDao {
    @Query("SELECT * FROM sends ORDER BY date DESC")
    fun getAll(): Flow<List<SendRecord>>

    @Insert
    suspend fun insert(record: SendRecord): Long

    @Query("DELETE FROM sends")
    suspend fun clear()
}
