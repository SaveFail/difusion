package com.masstext.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getById(id: Long): Contact?

    @Query("SELECT * FROM contacts WHERE id IN (:ids)")
    suspend fun getByIds(ids: Set<Long>): List<Contact>

    @Query("SELECT * FROM contacts")
    suspend fun getAllOnce(): List<Contact>

    @Insert
    suspend fun insert(contact: Contact): Long

    @Update
    suspend fun update(contact: Contact)

    @Delete
    suspend fun delete(contact: Contact)

    @Query("DELETE FROM contacts WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: Set<Long>)

    @Query("DELETE FROM contacts")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun count(): Int
}
