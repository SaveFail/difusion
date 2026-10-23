package com.difusion.app.service

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.difusion.app.data.AppDatabase
import com.difusion.app.data.ScheduledSend
import com.difusion.app.worker.ScheduledSendWorker
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Programa envíos (SMS/correo/WhatsApp) reutilizando la tabla y el worker. */
object Scheduler {
    suspend fun schedule(
        context: Context,
        phones: List<String>,
        emails: List<String>,
        message: String,
        subject: String,
        channel: Int,
        atMillis: Long
    ): Long {
        val db = AppDatabase.getInstance(context)
        val phonesArr = JSONArray()
        val emailsArr = JSONArray()
        val snapshot = JSONArray()
        phones.filter { it.isNotBlank() }.distinct().forEach {
            phonesArr.put(it)
            snapshot.put(JSONObject().put("name", it).put("phone", it).put("email", ""))
        }
        emails.filter { it.isNotBlank() }.distinct().forEach {
            emailsArr.put(it)
            snapshot.put(JSONObject().put("name", it).put("phone", "").put("email", it))
        }
        val item = ScheduledSend(
            phonesJson = phonesArr.toString(),
            emailsJson = emailsArr.toString(),
            recipientsJson = snapshot.toString(),
            message = message,
            subject = subject,
            channel = channel,
            scheduledAt = atMillis,
            status = 0
        )
        val id = db.scheduledSendDao().insert(item)
        val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val req = OneTimeWorkRequestBuilder<ScheduledSendWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueue(req)
        return id
    }
}
