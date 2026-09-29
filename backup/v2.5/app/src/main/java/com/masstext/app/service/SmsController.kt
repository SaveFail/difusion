package com.masstext.app.service

import android.content.Context

// Una única instancia compartida de SmsSender para que la app y el servicio
// de primer plano vean el mismo progreso y estado.
object SmsController {
    @Volatile
    private var instance: SmsSender? = null

    fun get(context: Context): SmsSender {
        return instance ?: synchronized(this) {
            instance ?: SmsSender(context.applicationContext).also { instance = it }
        }
    }
}