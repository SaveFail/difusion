package com.difusion.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sends")
data class SendRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val total: Int,
    val sent: Int,
    val failed: Int,
    val message: String,
    val user: String = "",
    val failureBreakdown: String = ""
)
