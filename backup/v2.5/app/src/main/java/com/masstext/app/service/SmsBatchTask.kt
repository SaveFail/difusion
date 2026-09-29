package com.masstext.app.service

import com.masstext.app.data.Contact

// Guarda el lote de mensajes pendiente para que el servicio en primer plano lo recoja.
object SmsBatchTask {
    @Volatile
    private var pending: PendingBatch? = null

    fun set(batch: PendingBatch) {
        pending = batch
    }

    fun take(): PendingBatch? {
        val batch = pending
        pending = null
        return batch
    }

    data class PendingBatch(
        val contacts: List<Contact>,
        val template: String,
        val delayMs: Long,
        // true = MODO SEGURO (bloques + contador). false = MODO DESATENDIDO.
        val safeMode: Boolean = true,
        // Subscription id de la SIM elegida para enviar (null = predeterminada).
        val subId: Int? = null
    )
}