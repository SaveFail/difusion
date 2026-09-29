package com.masstext.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calls")
data class CallRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val contactName: String,
    val phone: String,
    val success: Boolean,
    val label: String = CallLabels.NONE,
    val user: String = ""
)