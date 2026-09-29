package com.masstext.app.smsrole

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.masstext.app.data.AppDatabase
import com.masstext.app.service.AppFeedback
import com.masstext.app.service.SmsInbox
import com.masstext.app.ui.theme.ThemePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val config = ThemePrefs.read(context)
                for (sms in messages) {
                    val address = sms.originatingAddress ?: continue
                    val body = sms.displayMessageBody ?: continue
                    val date = if (sms.timestampMillis == 0L) System.currentTimeMillis() else sms.timestampMillis
                    SmsInbox.persistIncoming(context, db, address, body, date)
                    AppFeedback.notifyIncoming(context, config, address, body)
                }
            } finally {
                pending.finish()
            }
        }
    }
}