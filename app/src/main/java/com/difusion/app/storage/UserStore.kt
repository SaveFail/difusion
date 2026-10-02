package com.difusion.app.storage

import android.content.Context

object UserStore {

    private const val PREFS = "difusion_prefs"
    private const val KEY = "app_user"

    fun getUser(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "")
            .orEmpty()
            .trim()

    fun setUser(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, name.trim())
            .apply()
    }
}