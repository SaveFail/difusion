package com.difusion.app.storage

import android.content.Context
import android.content.SharedPreferences

/** Guarda el estado del inicio de sesión con Google (token de acceso y cuenta). */
object GmailAuthPrefs {
    private const val PREFS = "gmail_auth_prefs"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_EXPIRES = "expires_at"
    private const val KEY_ACCOUNT = "account_email"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setClientId(context: Context, id: String) = prefs(context).edit().putString(KEY_CLIENT_ID, id).apply()
    fun getClientId(context: Context): String = prefs(context).getString(KEY_CLIENT_ID, "") ?: ""

    fun saveToken(context: Context, token: String, expiresAtMs: Long) =
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_EXPIRES, expiresAtMs)
            .apply()

    fun getToken(context: Context): String = prefs(context).getString(KEY_TOKEN, "") ?: ""
    fun getExpiresAt(context: Context): Long = prefs(context).getLong(KEY_EXPIRES, 0L)

    fun setAccount(context: Context, email: String) = prefs(context).edit().putString(KEY_ACCOUNT, email).apply()
    fun getAccount(context: Context): String = prefs(context).getString(KEY_ACCOUNT, "") ?: ""

    fun clear(context: Context) = prefs(context).edit()
        .remove(KEY_TOKEN).remove(KEY_EXPIRES).remove(KEY_ACCOUNT).apply()

    /** Token válido (no vencido) o null. */
    fun validToken(context: Context): String? {
        val t = getToken(context)
        if (t.isBlank()) return null
        // 60 s de margen.
        if (System.currentTimeMillis() > getExpiresAt(context) - 60_000) return null
        return t
    }
}
