package com.difusion.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val cedula: String = "",
    val assignment: String = "",
    // Categorías provenientes de la hoja de Drive. Vacío = sin dato.
    // gestion = tipificación (columna SEGUIMIENTO: NO CONTESTA, VOLVER A LLAMAR…).
    val gestion: String = "",
    // estado = columna STATUS (PROMESA DE PAGO, PAGO…).
    val estado: String = "",
    // medio = columna MEDIO DE CONTACTO (LLAMADA, WHATSAPP…).
    val medio: String = "",
    val idCuota: String = "",
    val monto: String = "",
    val fechaGestion: String = "",
    val email: String = ""
)
