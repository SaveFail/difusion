package com.difusion.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "templates")
data class MessageTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val body: String,
    // Asunto para plantillas de correo (vacío en plantillas de SMS).
    val subject: String = ""
)
