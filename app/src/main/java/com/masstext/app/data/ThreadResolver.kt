package com.masstext.app.data

import android.content.Context
import android.provider.Telephony

// Resuelve el id de conversación (thread) que usa la bandeja del sistema para un
// número, de modo que los mensajes propios queden en el mismo hilo que los recibidos.
object ThreadResolver {

    fun resolve(context: Context, address: String): Long {
        return try {
            Telephony.Threads.getOrCreateThreadId(context, address)
        } catch (_: Exception) {
            val hash = address.hashCode().toLong()
            if (hash > 0) hash else -hash + 1
        }
    }
}