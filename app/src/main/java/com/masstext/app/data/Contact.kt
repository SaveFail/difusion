package com.masstext.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val cedula: String = "",
    val assignment: String = "",
    // Categoría de gestión proveniente de la hoja de Drive (ej. PROMESA, NO CONTESTA…).
    // Vacío = "Sin gestionar".
    val gestion: String = ""
)
