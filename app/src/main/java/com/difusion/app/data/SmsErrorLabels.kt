package com.difusion.app.data

import android.telephony.SmsManager

// Traduce el resultado de envío del sistema a una causa legible para el usuario.
// Android NO puede saber de forma directa si el fallo fue por "saldo insuficiente":
// el operador responde genéricamente y suele mandar su propio SMS. Por eso los
// fallos sin código específico se clasifican como "Error del operador (posible
// saldo)" y los errores de radio/servicio como "Sin señal"/"Sin servicio".
object SmsErrorLabels {

    fun forSmsResult(resultCode: Int): String = when (resultCode) {
        SmsManager.RESULT_ERROR_RADIO_OFF -> "Sin señal"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "Sin servicio"
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "Límite del operador alcanzado"
        SmsManager.RESULT_ERROR_NULL_PDU -> "PDU nula"
        SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> "Número restringido"
        else -> "Error del operador (posible saldo)"
    }

    fun forMmsResult(resultCode: Int): String = "Error del operador al enviar MMS"
}