package com.difusion.app.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Lista de correos "precargados" para el envío masivo.
 * Se guardan en el dispositivo y quedan listos para el próximo envío.
 */
object RecipientsPrefs {
    private const val PREFS = "recipients_prefs"
    private const val KEY_LIST = "manual_emails"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context): String = prefs(context).getString(KEY_LIST, "") ?: ""
    fun set(context: Context, value: String) = prefs(context).edit().putString(KEY_LIST, value).apply()

    /** Separa por líneas, comas, punto y coma o espacios y deja solo correos válidos. */
    fun parse(value: String): List<String> = value
        .split(Regex("[\\s,;]+"))
        .map { it.trim() }
        .filter { it.contains("@") && it.contains(".") }
        .distinct()
}
