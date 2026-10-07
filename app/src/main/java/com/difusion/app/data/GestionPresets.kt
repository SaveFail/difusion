package com.difusion.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Opciones de los desplegables de gestión (SEGUIMIENTO, STATUS, MEDIO, EJECUTIVO).
 *
 * Son **totalmente personalizables** desde Ajustes ▸ Gestión: se pueden
 * **agregar, editar y borrar** sin límite. La primera vez se cargan los valores
 * por defecto; a partir de ahí, la lista guardada manda (se puede vaciar).
 * Las importaciones de Drive **suman** valores, sin borrar los tuyos.
 */
object GestionPresets {
    private const val PREFS = "gestion_presets_prefs"
    private const val KEY_GESTION = "gestion_list"
    private const val KEY_ESTADO = "estado_list"
    private const val KEY_MEDIO = "medio_list"
    private const val KEY_ASSIGN = "assign_list"
    private const val KEY_VERSION = "gestion_presets_version"
    private const val VERSION = 2

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

    private fun split(s: String?): List<String> =
        (s ?: "").split("||").map { it.trim() }.filter { it.isNotBlank() }

    private fun join(list: List<String>): String = list
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .take(300)
        .joinToString("||")

    private fun mergeUnique(base: List<String>, extra: List<String>): List<String> {
        val out = ArrayList<String>(base)
        extra.forEach { e -> if (out.none { it.equals(e, ignoreCase = true) }) out.add(e.trim()) }
        return out
    }

    /** La primera vez copia los valores por defecto a la lista editable. */
    private fun migrate(context: Context) {
        val p = prefs(context)
        if (p.getInt(KEY_VERSION, 0) >= VERSION) return
        val g = mergeUnique(defaultGestion, split(p.getString(KEY_GESTION, "")))
        val e = mergeUnique(defaultEstado, split(p.getString(KEY_ESTADO, "")))
        val m = mergeUnique(defaultMedio, split(p.getString(KEY_MEDIO, "")))
        p.edit()
            .putString(KEY_GESTION, join(g))
            .putString(KEY_ESTADO, join(e))
            .putString(KEY_MEDIO, join(m))
            .putInt(KEY_VERSION, VERSION)
            .apply()
    }

    // Nota: migrate() garantiza que la primera vez queden los valores por
    // defecto guardados; de ahí en adelante manda la lista del usuario (puede
    // quedar vacía si él borra todo).
    fun getGestion(context: Context): List<String> {
        migrate(context)
        return split(prefs(context).getString(KEY_GESTION, ""))
    }

    fun getEstado(context: Context): List<String> {
        migrate(context)
        return split(prefs(context).getString(KEY_ESTADO, ""))
    }

    fun getMedio(context: Context): List<String> {
        migrate(context)
        return split(prefs(context).getString(KEY_MEDIO, ""))
    }

    fun getAsignado(context: Context): List<String> =
        split(prefs(context).getString(KEY_ASSIGN, ""))

    fun setGestion(context: Context, list: List<String>) {
        prefs(context).edit()
            .putString(KEY_GESTION, join(list))
            .putInt(KEY_VERSION, VERSION)
            .apply()
    }

    fun setEstado(context: Context, list: List<String>) {
        prefs(context).edit()
            .putString(KEY_ESTADO, join(list))
            .putInt(KEY_VERSION, VERSION)
            .apply()
    }

    fun setMedio(context: Context, list: List<String>) {
        prefs(context).edit()
            .putString(KEY_MEDIO, join(list))
            .putInt(KEY_VERSION, VERSION)
            .apply()
    }

    private fun addTo(context: Context, key: String, current: List<String>, values: List<String>) {
        val merged = mergeUnique(current, values)
        prefs(context).edit().putString(key, join(merged)).putInt(KEY_VERSION, VERSION).apply()
    }

    fun addGestion(context: Context, values: List<String>) =
        addTo(context, KEY_GESTION, getGestion(context), values)

    fun addEstado(context: Context, values: List<String>) =
        addTo(context, KEY_ESTADO, getEstado(context), values)

    fun addMedio(context: Context, values: List<String>) =
        addTo(context, KEY_MEDIO, getMedio(context), values)

    /** Guarda (sumando) los valores vistos en la última importación. */
    fun saveFromImport(
        context: Context,
        gestion: List<String>,
        estado: List<String>,
        medio: List<String>,
        asignado: List<String> = emptyList()
    ) {
        runCatching {
            addGestion(context, gestion)
            addEstado(context, estado)
            addMedio(context, medio)
            if (asignado.isNotEmpty()) {
                addTo(context, KEY_ASSIGN, getAsignado(context), asignado)
            }
        }
    }
}
