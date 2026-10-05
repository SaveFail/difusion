package com.difusion.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Envío masivo programado para una fecha/hora concreta.
 * Los destinatarios se guardan como JSON (teléfonos y, si aplica, correos).
 */
@Entity(tableName = "scheduled_sends")
data class ScheduledSend(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phonesJson: String = "[]",
    val emailsJson: String = "[]",
    val message: String = "",
    val subject: String = "",
    // 0 = SMS, 1 = Correo
    val channel: Int = 0,
    val scheduledAt: Long = 0L,
    // 0 = pendiente, 1 = enviado, 2 = fallido, 3 = cancelado
    val status: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val sentAt: Long = 0L,
    val result: String = ""
)
