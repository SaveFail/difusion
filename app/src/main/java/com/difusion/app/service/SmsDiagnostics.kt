package com.difusion.app.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * Diagnóstico de por qué un SMS puede quedar "Enviando" o fallar: revisa el
 * permiso, la SIM y el modo avión para dar una causa concreta al usuario.
 */
object SmsDiagnostics {

    /** Lista de causas detectadas que impiden enviar ahora mismo. */
    fun reasonsWhyCannotSend(context: Context): List<String> {
        val reasons = mutableListOf<String>()

        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPerm) reasons.add("Sin permiso para enviar SMS")

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val simReady = tm != null && tm.simState == TelephonyManager.SIM_STATE_READY
        if (!simReady) reasons.add("No hay SIM insertada o no está lista")

        val airplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        if (airplane) reasons.add("Modo avión activado")

        return reasons
    }

    /**
     * Causa principal del fallo. Si hay causas detectables las une; si no, usa
     * el fallo genérico del operador (que es lo único que puede quedar pendiente
     * cuando el sistema nunca confirma el envío).
     */
    fun primaryReason(context: Context): String {
        val reasons = reasonsWhyCannotSend(context)
        return if (reasons.isNotEmpty()) {
            reasons.joinToString(" · ")
        } else {
            "Sin confirmación del operador (posible falta de saldo o cobertura)"
        }
    }
}
