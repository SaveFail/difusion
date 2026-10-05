package com.difusion.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Opciones de los desplegables de gestión (SEGUIMIENTO, STATUS, MEDIO, EJECUTIVO).
 *
 * Funcionan SIEMPRE, incluso sin importar nada: parten de valores por defecto
 * del formato estándar y, si el usuario importó su hoja, se añaden los valores
 * que venían en ella (sin duplicar).
 */
object GestionPresets {
    private const val PREFS = "gestion_presets_prefs"
    private const val KEY_GESTION = "gestion_list"
    private const val KEY_ESTADO = "estado_list"
    private const val KEY_MEDIO = "medio_list"
    private const val KEY_ASSIGN = "assign_list"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val defaultGestion = listOf(
        "NO CONTESTA",
        "CONTESTA",
        "OCUPADO",
        "TELÉFONO APAGADO",
        "NÚMERO EQUIVOCADO",
        "FUERA DE SERVICIO",
        "NO DISPONIBLE",
        "VOLVER A LLAMAR",
        "ENVIAR SMS",
        "YA PAGÓ",
        "RENUENTE A PAGAR",
        "SOLICITA ACUERDO",
        "LLAMADA",
        "Enviado por SMS"
    )

    val defaultEstado = listOf(
        "PENDIENTE",
        "PROMESA DE PAGO",
        "PAGO REALIZADO",
        "PAGO PARCIAL",
        "RENUENTE",
        "NO PAGA",
        "EN GESTIÓN",
        "GESTIONADO",
        "CERRADO",
        "POR VERIFICAR"
    )

    val defaultMedio = listOf(
        "LLAMADA",
        "SMS",
        "WHATSAPP",
        "LLAMADA ENTRANTE",
        "LLAMADA SALIENTE"
    )

    private fun merged(defaults: List<String>, saved: String): List<String> {
        val out = LinkedHashSet<String>()
        defaults.forEach { out.add(it) }
        if (saved.isNotBlank()) {
            saved.split("||").forEach { if (it.isNotBlank()) out.add(it.trim()) }
        }
        return out.toList()
    }

    fun getGestion(context: Context): List<String> =
        merged(defaultGestion, prefs(context).getString(KEY_GESTION, "") ?: "")

    fun getEstado(context: Context): List<String> =
        merged(defaultEstado, prefs(context).getString(KEY_ESTADO, "") ?: "")

    fun getMedio(context: Context): List<String> =
        merged(defaultMedio, prefs(context).getString(KEY_MEDIO, "") ?: "")

    fun getAsignado(context: Context): List<String> =
        merged(emptyList(), prefs(context).getString(KEY_ASSIGN, "") ?: "")

    /** Guarda los valores vistos en la última importación para reutilizarlos. */
    fun saveFromImport(
        context: Context,
        gestion: List<String>,
        estado: List<String>,
        medio: List<String>,
        asignado: List<String> = emptyList()
    ) {
        fun join(list: List<String>): String = list
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(80)
            .joinToString("||")

        prefs(context).edit()
            .putString(KEY_GESTION, join(gestion))
            .putString(KEY_ESTADO, join(estado))
            .putString(KEY_MEDIO, join(medio))
            .putString(KEY_ASSIGN, join(asignado))
            .apply()
    }
}
