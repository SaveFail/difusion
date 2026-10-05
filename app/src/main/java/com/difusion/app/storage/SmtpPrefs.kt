package com.difusion.app.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Credenciales de Gmail para SMTP/IMAP con "contraseña de aplicación".
 * Se guardan en el dispositivo (no se suben a ningún lado).
 */
object SmtpPrefs {
    private const val PREFS = "smtp_prefs"
    private const val KEY_EMAIL = "email"
    private const val KEY_PASS = "app_password"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setEmail(context: Context, email: String) = prefs(context).edit().putString(KEY_EMAIL, email.trim()).apply()
    fun getEmail(context: Context): String {
        val v = prefs(context).getString(KEY_EMAIL, "") ?: ""
        return if (v.isNotBlank()) v else com.difusion.app.BuildConfig.SMTP_USER
    }

    fun setPassword(context: Context, pass: String) = prefs(context).edit().putString(KEY_PASS, pass.replace(" ", "")).apply()
    fun getPassword(context: Context): String {
        val v = prefs(context).getString(KEY_PASS, "") ?: ""
        return if (v.isNotBlank()) v else com.difusion.app.BuildConfig.SMTP_APP_PASSWORD
    }

    fun clear(context: Context) = prefs(context).edit().remove(KEY_EMAIL).remove(KEY_PASS).apply()

    fun isConfigured(context: Context): Boolean =
        getEmail(context).isNotBlank() && getPassword(context).isNotBlank()
}
