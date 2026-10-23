package com.difusion.app.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.difusion.app.data.AppDatabase
import com.difusion.app.storage.WaPrefs
import com.difusion.app.worker.BotPollWorker
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Automatizaciones que ejecuta LA APP sobre los mensajes entrantes de WhatsApp:
 * responder, cambiar seguimiento/status/medio del contacto, etiquetar y
 * programar mensajes. Así el bot "autogestiona" procesos dentro de la app.
 */
object BotAutomation {

    data class Rule(
        val keyword: String,
        val reply: String = "",
        val setGestion: String = "",
        val setEstado: String = "",
        val setMedio: String = "",
        val addTag: String = "",
        val scheduleMinutes: Int = 0,
        val scheduleText: String = ""
    )

    private fun join(list: List<String>) = list.joinToString("||")

    fun rulesToJson(rules: List<Rule>): String {
        val arr = JSONArray()
        rules.forEach { r ->
            arr.put(JSONObject().apply {
                put("keyword", r.keyword)
                put("reply", r.reply)
                put("setGestion", r.setGestion)
                put("setEstado", r.setEstado)
                put("setMedio", r.setMedio)
                put("addTag", r.addTag)
                put("scheduleMinutes", r.scheduleMinutes)
                put("scheduleText", r.scheduleText)
            })
        }
        return arr.toString()
    }

    fun parseRules(json: String): List<Rule> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val k = o.optString("keyword", "")
            if (k.isBlank()) return@mapNotNull null
            Rule(
                keyword = k,
                reply = o.optString("reply", ""),
                setGestion = o.optString("setGestion", ""),
                setEstado = o.optString("setEstado", ""),
                setMedio = o.optString("setMedio", ""),
                addTag = o.optString("addTag", ""),
                scheduleMinutes = o.optInt("scheduleMinutes", 0),
                scheduleText = o.optString("scheduleText", "")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun ensurePolling(context: Context) {
        val req = PeriodicWorkRequestBuilder<BotPollWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "bot_poll", ExistingPeriodicWorkPolicy.UPDATE, req
        )
    }

    fun pollNow(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<BotPollWorker>().build())
    }

    private fun samePhone(a: String, b: String): Boolean {
        fun digits(s: String): String {
            var d = s.filter { it.isDigit() }
            if (d.startsWith("58")) d = d.removePrefix("58")
            if (d.startsWith("0")) d = d.removePrefix("0")
            return d.takeLast(10)
        }
        val da = digits(a)
        val db = digits(b)
        return da.isNotBlank() && da == db
    }

    /**
     * Procesa los mensajes entrantes y aplica las reglas. Devuelve cuántas
     * acciones se ejecutaron. Llamar desde IO.
     */
    suspend fun process(context: Context, messages: List<WhatsAppBridge.Incoming>): Int {
        val rules = parseRules(WaPrefs.getAutoRulesJson(context))
        if (rules.isEmpty() || messages.isEmpty()) return 0
        val db = AppDatabase.getInstance(context)
        val all = db.contactDao().getAllOnce()
        var applied = 0

        for (msg in messages) {
            val phone = msg.jid.substringBefore("@")
            val contact = all.firstOrNull { samePhone(phone, it.phone) }
            for (rule in rules) {
                if (!msg.text.contains(rule.keyword, ignoreCase = true)) continue

                if (rule.reply.isNotBlank()) {
                    WhatsAppBridge.send(
                        context,
                        msg.jid,
                        rule.reply.replace("{nombre}", phone).replace("{telefono}", phone)
                    )
                    applied++
                }

                if (contact != null) {
                    val newGestion = rule.setGestion.ifBlank { contact.gestion }
                    val newEstado = rule.setEstado.ifBlank { contact.estado }
                    val newMedio = rule.setMedio.ifBlank { contact.medio }
                    var newTags = contact.tags
                    if (rule.addTag.isNotBlank()) {
                        val has = contact.tags.split(",")
                            .any { it.trim().equals(rule.addTag.trim(), ignoreCase = true) }
                        if (!has) {
                            newTags = listOf(contact.tags, rule.addTag.trim())
                                .filter { it.isNotBlank() }
                                .joinToString(",")
                        }
                    }
                    if (newGestion != contact.gestion || newEstado != contact.estado ||
                        newMedio != contact.medio || newTags != contact.tags
                    ) {
                        db.contactDao().update(
                            contact.copy(
                                gestion = newGestion,
                                estado = newEstado,
                                medio = newMedio,
                                tags = newTags
                            )
                        )
                        applied++
                    }
                }

                if (rule.scheduleMinutes > 0 && rule.scheduleText.isNotBlank()) {
                    val at = System.currentTimeMillis() + rule.scheduleMinutes * 60_000L
                    Scheduler.schedule(
                        context,
                        phones = listOf(phone),
                        emails = emptyList(),
                        message = rule.scheduleText.replace("{nombre}", phone),
                        subject = "",
                        channel = 2,
                        atMillis = at
                    )
                    applied++
                }
            }
        }
        return applied
    }
}
