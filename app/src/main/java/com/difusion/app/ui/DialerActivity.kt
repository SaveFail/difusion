package com.difusion.app.ui

import android.app.Activity
import com.difusion.app.MainActivity
import android.content.Intent
import android.os.Bundle

/**
 * Activity ligera que maneja los intents de marcación del sistema
 * (DIAL / CALL_PRIVILEGED). Su sola presencia permite que el sistema
 * (Ajustes > Aplicaciones predeterminadas > App de teléfono) ofrezca
 * esta app como candidata a ser la app de llamadas predeterminada.
 */
class DialerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
            )
        }
        finish()
    }
}