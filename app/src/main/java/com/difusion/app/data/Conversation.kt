package com.difusion.app.data

data class Conversation(
    val threadId: Long,
    val address: String,
    val name: String,
    val lastBody: String,
    val lastDate: Long,
    val lastStatus: Int,
    val unreadCount: Int,
    // true si el último mensaje del hilo es entrante (el contacto escribió y
    // todavía no se le ha respondido).
    val lastIsIncoming: Boolean = false
)