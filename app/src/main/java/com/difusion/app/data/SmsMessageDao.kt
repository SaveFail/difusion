package com.difusion.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsMessageDao {
    // Busca por provider id SIN filtrar papelera: así un mensaje que está en la
    // papelera (inTrash=1) no se re-importa duplicado al sincronizar. El id del
    // proveedor (sms o mms) vive en espacios separados, por eso isMms.
    @Query("SELECT * FROM sms_messages WHERE providerId = :providerId AND isMms = :isMms LIMIT 1")
    suspend fun byProviderId(providerId: Long, isMms: Boolean): SmsMessage?

    @Insert
    suspend fun insert(message: SmsMessage): Long

    @Insert
    suspend fun insertAll(messages: List<SmsMessage>): List<Long>

    // Ids ya importados / purgados por tipo (sms o mms). Se cargan UNA vez al
    // sincronizar para no consultar la base por cada mensaje del proveedor.
    @Query("SELECT providerId FROM sms_messages WHERE isMms = :isMms")
    suspend fun allProviderIds(isMms: Boolean): List<Long>

    @Query("SELECT providerId FROM sms_purged WHERE isMms = :isMms")
    suspend fun allPurgedIds(isMms: Boolean): List<Long>

    @Update
    suspend fun update(message: SmsMessage)

    @Query("UPDATE sms_messages SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: Int)

    @Query("UPDATE sms_messages SET status = :status WHERE id = :id AND isMms = :isMms")
    suspend fun setStatusAndMms(id: Long, status: Int, isMms: Boolean)

    @Query("UPDATE sms_messages SET status = :status, errorCode = :errorCode, errorLabel = :errorLabel WHERE id = :id AND isMms = :isMms")
    suspend fun setStatusWithError(id: Long, status: Int, isMms: Boolean, errorCode: Int, errorLabel: String)

    // Al reenviar se marca "enviando" y se limpia la causa del fallo anterior.
    @Query("UPDATE sms_messages SET status = :status, errorCode = 0, errorLabel = '' WHERE id = :id")
    suspend fun setStatusAndClearError(id: Long, status: Int)

    @Query("UPDATE sms_messages SET read = 1 WHERE threadId = :threadId AND inTrash = 0")
    suspend fun markThreadRead(threadId: Long)

    @Query("DELETE FROM sms_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sms_messages WHERE threadId = :threadId")
    suspend fun deleteThread(threadId: Long)

    @Query("SELECT * FROM sms_messages WHERE threadId = :threadId AND inTrash = 0 ORDER BY date ASC")
    fun messagesForThread(threadId: Long): Flow<List<SmsMessage>>

    @Query("SELECT * FROM sms_messages WHERE inTrash = 0 ORDER BY date DESC")
    fun allOrdered(): Flow<List<SmsMessage>>

    @Query("SELECT * FROM sms_messages WHERE inTrash = 1 ORDER BY date DESC")
    fun allInTrash(): Flow<List<SmsMessage>>

    @Query("SELECT * FROM sms_messages WHERE inTrash = 1")
    suspend fun allInTrashOnce(): List<SmsMessage>

    @Query("UPDATE sms_messages SET inTrash = 1 WHERE id IN (:ids)")
    suspend fun moveToTrash(ids: List<Long>)

    @Query("UPDATE sms_messages SET inTrash = 1 WHERE threadId = :threadId")
    suspend fun moveThreadToTrash(threadId: Long)

    @Query("UPDATE sms_messages SET inTrash = 1 WHERE threadId IN (:threadIds)")
    suspend fun moveThreadsToTrash(threadIds: List<Long>)

    @Query("UPDATE sms_messages SET inTrash = 0 WHERE id IN (:ids)")
    suspend fun restore(ids: List<Long>)

    @Query("DELETE FROM sms_messages WHERE id IN (:ids)")
    suspend fun purge(ids: List<Long>)

    @Query("DELETE FROM sms_messages WHERE inTrash = 1")
    suspend fun purgeAllTrash()

    @Query("SELECT * FROM sms_messages WHERE threadId = :threadId AND isIncoming = 0 AND status = :status AND inTrash = 0 ORDER BY date DESC LIMIT 1")
    suspend fun lastFailedForThread(threadId: Long, status: Int): SmsMessage?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPurged(purged: PurgedSms)

    @Query("SELECT COUNT(*) FROM sms_purged WHERE providerId = :providerId AND isMms = :isMms")
    suspend fun isPurged(providerId: Long, isMms: Boolean): Int

    // Desglose de causas de fallo dentro de un rango de ids (un lote de envío).
    @Query("SELECT errorLabel AS label, COUNT(*) AS n FROM sms_messages WHERE id >= :fromId AND id <= :toId AND status = :failedStatus GROUP BY errorLabel ORDER BY n DESC")
    suspend fun failureBreakdown(fromId: Long, toId: Long, failedStatus: Int): List<FailureCount>

    // Total de fallidos por causa (usado en Historial).
    @Query("SELECT errorLabel AS label, COUNT(*) AS n FROM sms_messages WHERE status = :failedStatus AND errorLabel IS NOT NULL AND errorLabel != '' GROUP BY errorLabel ORDER BY n DESC")
    fun failuresByLabel(failedStatus: Int): Flow<List<FailureCount>>
}

data class FailureCount(val label: String, val n: Int)