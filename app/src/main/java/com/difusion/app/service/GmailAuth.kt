package com.difusion.app.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.difusion.app.storage.GmailAuthPrefs
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Inicio de sesión con Google para acceder al Gmail del usuario (permiso directo).
 *
 * Usa Google Identity Services (AuthorizationClient). El identificador que se
 * pasa es el **Client ID de tipo Web** del proyecto (el "server client id"), y
 * en el mismo proyecto debe existir además un **Client ID de tipo Android**
 * registrado con el paquete y la huella SHA-1 de la app.
 */
object GmailAuth {
    private val SCOPES = listOf(
        Scope("https://www.googleapis.com/auth/gmail.modify"),
        Scope("https://www.googleapis.com/auth/gmail.send"),
        Scope("https://www.googleapis.com/auth/userinfo.email")
    )

    private val http = OkHttpClient()

    /** true si el Client ID viene incrustado en la app (el caso normal). */
    fun hasBakedClientId(): Boolean =
        com.difusion.app.BuildConfig.GMAIL_OAUTH_CLIENT_ID.isNotBlank()

    /** Client ID a usar: el incrustado en la app o, si no, el que se escriba. */
    fun clientId(context: Context): String {
        val pref = GmailAuthPrefs.getClientId(context)
        if (pref.isNotBlank()) return pref
        return com.difusion.app.BuildConfig.GMAIL_OAUTH_CLIENT_ID
    }

    fun isConfigured(context: Context) = clientId(context).isNotBlank()
    fun isSignedIn(context: Context) = GmailAuthPrefs.validToken(context) != null
    fun account(context: Context) = GmailAuthPrefs.getAccount(context)
    fun token(context: Context): String? = GmailAuthPrefs.validToken(context)

    fun request(activity: Activity, serverClientId: String): Task<AuthorizationResult> {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(SCOPES)
            .requestOfflineAccess(serverClientId)
            .build()
        return Identity.getAuthorizationClient(activity).authorize(request)
    }

    fun resultFromIntent(context: Context, data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)

    /** Guarda el token y, si se puede, el correo de la cuenta. */
    suspend fun applyResult(context: Context, result: AuthorizationResult) {
        val accessToken = result.accessToken ?: return
        GmailAuthPrefs.saveToken(context, accessToken, System.currentTimeMillis() + 55 * 60_000L)
        val fromResult = runCatching { result.toGoogleSignInAccount()?.email }.getOrNull()
        if (!fromResult.isNullOrBlank()) {
            GmailAuthPrefs.setAccount(context, fromResult)
            return
        }
        runCatching {
            val email = withContext(Dispatchers.IO) { fetchEmail(accessToken) }
            if (email.isNotBlank()) GmailAuthPrefs.setAccount(context, email)
        }
    }

    private fun fetchEmail(token: String): String {
        val req = Request.Builder()
            .url("https://www.googleapis.com/oauth2/v3/userinfo?alt=json")
            .addHeader("Authorization", "Bearer $token")
            .build()
        return try {
            http.newCall(req).execute().use { r ->
                val body = r.body?.string() ?: return ""
                JSONObject(body).optString("email", "")
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun signOut(context: Context) {
        GmailAuthPrefs.clear(context)
    }
}
