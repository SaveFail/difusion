package com.masstext.app.data

import androidx.room.Entity
import androidx.room.Index

/**
 * Ids (del proveedor del sistema) borrados de forma DEFINITIVA (papelera
 * vaciada o borrado permanente). Se usan para que un re-sync de la bandeja no
 * vuelva a importar esos mensajes, aunque el proveedor todavía los tenga.
 * Los ids de content://sms y content://mms son espacios separados, por eso la
 * clave es compuesta (providerId, isMms).
 */
@Entity(
    tableName = "sms_purged",
    primaryKeys = ["providerId", "isMms"],
    indices = [Index("providerId")]
)
data class PurgedSms(
    val providerId: Long = 0,
    val isMms: Boolean = false
)