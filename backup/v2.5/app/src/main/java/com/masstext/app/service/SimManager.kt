package com.masstext.app.service

import android.content.Context
import android.content.SharedPreferences
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

object SimManager {

    data class SimInfo(
        val handle: PhoneAccountHandle,
        val label: String,
        val number: String?
    )

    private const val PREFS = "sim_prefs"
    private const val KEY_DEFAULT = "default_account_id"
    private const val KEY_SMS_DEFAULT = "sms_account_id"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Lista de SIMs disponibles para llamadas (excluye apps auto-gestionadas). */
    fun getSims(context: Context): List<SimInfo> {
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            ?: return emptyList()
        return runCatching {
            telecom.callCapablePhoneAccounts.mapNotNull { handle ->
                val pa = telecom.getPhoneAccount(handle) ?: return@mapNotNull null
                if (!pa.isEnabled) return@mapNotNull null
                if (pa.hasCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)) return@mapNotNull null
                SimInfo(
                    handle = handle,
                    label = pa.label?.toString()?.takeIf { it.isNotBlank() } ?: "SIM",
                    number = pa.address?.schemeSpecificPart
                )
            }
        }.getOrDefault(emptyList())
    }

    /** ID de la SIM predeterminada guardada (puede ser null). */
    fun getDefaultId(context: Context): String? =
        prefs(context).getString(KEY_DEFAULT, null)

    fun setDefaultId(context: Context, id: String?) {
        prefs(context).edit().putString(KEY_DEFAULT, id).apply()
    }

    /** PhoneAccountHandle de la SIM predeterminada, o la primera SIM si no hay default. */
    fun getDefaultHandle(context: Context): PhoneAccountHandle? {
        val sims = getSims(context)
        if (sims.isEmpty()) return null
        val defId = getDefaultId(context)
        return sims.find { it.handle.id == defId }?.handle
            ?: sims.first().handle
    }

    /** ID de la SIM elegida para enviar SMS. */
    fun getSmsId(context: Context): String? =
        prefs(context).getString(KEY_SMS_DEFAULT, null)

    fun setSmsId(context: Context, id: String?) {
        prefs(context).edit().putString(KEY_SMS_DEFAULT, id).apply()
    }

    /** SIM elegida para enviar SMS (o la de la suscripción por defecto). */
    fun getSmsSim(context: Context): SimInfo? {
        val sims = getSims(context)
        if (sims.isEmpty()) return null
        getSmsId(context)?.let { id -> sims.find { it.handle.id == id }?.let { return it } }
        val defaultSub = runCatching {
            android.telephony.SubscriptionManager.getDefaultSmsSubscriptionId()
        }.getOrDefault(android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        return sims.find { it.handle.id.toIntOrNull() == defaultSub } ?: sims.first()
    }

    /**
     * Subscription id de la SIM elegida para SMS, o **null** para usar el
     * SmsManager predeterminado. Se devuelve null (comportamiento normal, sin
     * confirmación por mensaje) salvo que haya 2+ SIM, el usuario haya elegido
     * una explícitamente y sea distinta de la suscripción de SMS por defecto.
     */
    fun getSmsSubId(context: Context): Int? {
        val sims = getSims(context)
        if (sims.size < 2) return null
        val chosenId = getSmsId(context) ?: return null
        val chosen = sims.find { it.handle.id == chosenId } ?: return null
        val subId = chosen.handle.id.toIntOrNull() ?: return null
        val defaultSub = runCatching {
            android.telephony.SubscriptionManager.getDefaultSmsSubscriptionId()
        }.getOrDefault(android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        return if (subId == defaultSub) null else subId
    }

    /** Devuelve la otra SIM (la que no es la actual). */
    fun getOtherSim(context: Context, current: PhoneAccountHandle?): SimInfo? {
        val sims = getSims(context)
        if (sims.size < 2) return null
        return sims.find { it.handle != current }
    }

    /** Obtiene la SIM por su PhoneAccountHandle. */
    fun getSimByHandle(context: Context, handle: PhoneAccountHandle?): SimInfo? {
        if (handle == null) return null
        return getSims(context).find { it.handle == handle }
    }

    /**
     * Realiza una llamada usando TelecomManager con la SIM especificada.
     * Requiere permiso CALL_PHONE.
     */
    fun placeCall(context: Context, number: String, simHandle: PhoneAccountHandle?): Boolean {
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            ?: return false
        val uri = android.net.Uri.parse("tel:${number}")
        val ok = runCatching {
            if (simHandle != null) {
                val extras = android.os.Bundle().apply {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, simHandle)
                }
                telecom.placeCall(uri, extras)
            } else {
                telecom.placeCall(uri, android.os.Bundle.EMPTY)
            }
        }.onFailure {
            CallRecorderLog.append(context, "No se pudo iniciar la llamada: ${it.message}")
        }.isSuccess
        if (ok) {
            // autoStart ya no bloquea el hilo principal (trabaja en segundo plano).
            CallRecorder.autoStart(context, number)
        }
        return ok
    }
}