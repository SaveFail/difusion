package com.difusion.app.storage

import android.content.Context
import android.content.SharedPreferences

/** Configuración del Web App de Google Apps Script que escribe en la hoja. */
object DriveSyncPrefs {
    private const val PREFS = "drive_sync_prefs"
    private const val KEY_URL = "drive_sync_url"
    private const val KEY_TOKEN = "drive_sync_token"
    private const val KEY_ENABLED = "drive_sync_enabled"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setUrl(context: Context, url: String) = prefs(context).edit().putString(KEY_URL, url).apply()
    fun getUrl(context: Context): String {
        val v = prefs(context).getString(KEY_URL, "") ?: ""
        return if (v.isNotBlank()) v else com.difusion.app.BuildConfig.DRIVE_BRIDGE_URL
    }

    fun setToken(context: Context, token: String) = prefs(context).edit().putString(KEY_TOKEN, token).apply()
    fun getToken(context: Context): String {
        val v = prefs(context).getString(KEY_TOKEN, "") ?: ""
        return if (v.isNotBlank()) v else com.difusion.app.BuildConfig.DRIVE_BRIDGE_TOKEN
    }

    fun setEnabled(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false) ||
            com.difusion.app.BuildConfig.DRIVE_BRIDGE_URL.isNotBlank()
}
