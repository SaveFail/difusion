package com.difusion.app.storage

import android.content.Context
import android.content.SharedPreferences

/** Configuración del Web App de Apps Script que envía correos masivos. */
object EmailSyncPrefs {
    private const val PREFS = "email_sync_prefs"
    private const val KEY_URL = "email_sync_url"
    private const val KEY_TOKEN = "email_sync_token"
    private const val KEY_ENABLED = "email_sync_enabled"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setUrl(context: Context, url: String) = prefs(context).edit().putString(KEY_URL, url).apply()
    fun getUrl(context: Context): String = prefs(context).getString(KEY_URL, "") ?: ""

    fun setToken(context: Context, token: String) = prefs(context).edit().putString(KEY_TOKEN, token).apply()
    fun getToken(context: Context): String = prefs(context).getString(KEY_TOKEN, "") ?: ""

    fun setEnabled(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)
}
