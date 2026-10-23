package com.difusion.app.storage

import android.content.Context
import android.content.SharedPreferences

/** Conexión con el worker de WhatsApp (Baileys) y configuración del bot. */
object WaPrefs {
    private const val PREFS = "wa_prefs"
    private const val KEY_URL = "wa_http_url"
    private const val KEY_TOKEN = "wa_token"
    private const val KEY_BOT = "wa_bot_enabled"
    private const val KEY_RULES = "wa_bot_rules"
    private const val KEY_AUTO_RULES = "wa_auto_rules"
    private const val KEY_LAST_TS = "wa_last_msg_ts"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setUrl(context: Context, url: String) =
        prefs(context).edit().putString(KEY_URL, url.trim().trimEnd('/')).apply()

    fun getUrl(context: Context): String =
        prefs(context).getString(KEY_URL, "")?.trim()?.trimEnd('/') ?: ""

    fun setToken(context: Context, token: String) =
        prefs(context).edit().putString(KEY_TOKEN, token.trim()).apply()

    fun getToken(context: Context): String = prefs(context).getString(KEY_TOKEN, "") ?: ""

    fun isConfigured(context: Context): Boolean = getUrl(context).isNotBlank()

    fun setBotEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_BOT, enabled).apply()

    fun isBotEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_BOT, false)

    fun setRulesJson(context: Context, json: String) =
        prefs(context).edit().putString(KEY_RULES, json).apply()

    fun getRulesJson(context: Context): String = prefs(context).getString(KEY_RULES, "[]") ?: "[]"

    // Reglas de automatización de la app (acciones sobre contactos, etc.).
    fun setAutoRulesJson(context: Context, json: String) =
        prefs(context).edit().putString(KEY_AUTO_RULES, json).apply()

    fun getAutoRulesJson(context: Context): String =
        prefs(context).getString(KEY_AUTO_RULES, "[]") ?: "[]"

    fun setLastMessageTs(context: Context, ts: Long) =
        prefs(context).edit().putLong(KEY_LAST_TS, ts).apply()

    fun getLastMessageTs(context: Context): Long = prefs(context).getLong(KEY_LAST_TS, 0L)
}
