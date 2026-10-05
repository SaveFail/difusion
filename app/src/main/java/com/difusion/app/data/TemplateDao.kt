package com.difusion.app.data

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

    @Query("SELECT * FROM templates WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): MessageTemplate?

    @Insert
    suspend fun insert(template: MessageTemplate): Long

    @Query("UPDATE templates SET body = :body WHERE id = :id")
    suspend fun updateBody(id: Long, body: String)

    @Query("UPDATE templates SET body = :body, subject = :subject WHERE id = :id")
    suspend fun updateBodyAndSubject(id: Long, body: String, subject: String)

    @Update
    suspend fun update(template: MessageTemplate)

    @Delete
    suspend fun delete(template: MessageTemplate)
}
