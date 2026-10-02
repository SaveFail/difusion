package com.difusion.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_messages")
data class SmsMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long? = null,
    val threadId: Long = 0,
    val address: String = "",
    val body: String = "",
    val date: Long = 0,
    val isIncoming: Boolean = true,
    val status: Int = SmsStatus.SENT,
    val read: Boolean = true,
    val inTrash: Boolean = false,
    val isMms: Boolean = false,
    val mediaPath: String? = null,
    val mediaMime: String? = null,
    val subject: String? = null,
    val errorCode: Int = 0,
    val errorLabel: String? = null
)