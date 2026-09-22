package com.masstext.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<MessageTemplate>>

    @Insert
    suspend fun insert(template: MessageTemplate): Long

    @Update
    suspend fun update(template: MessageTemplate)

    @Delete
    suspend fun delete(template: MessageTemplate)
}
