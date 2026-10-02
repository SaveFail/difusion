package com.difusion.app.smsrole

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.difusion.app.data.AppDatabase
import com.difusion.app.data.SmsErrorLabels
import com.difusion.app.data.SmsStatus
import com.difusion.app.service.MmsSender
import com.difusion.app.service.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Recibe la confirmación del sistema sobre el resultado de cada envío de SMS/MMS
// y actualiza el estado del mensaje en la base local (Enviado / No enviado),
// guardando también el código y la causa legible del fallo.
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isMms = action == MmsSender.ACTION_SENT
        if (action != SmsSender.ACTION_SENT && !isMms) return
        val rowId = if (isMms) intent.getLongExtra(MmsSender.EXTRA_ROW_ID, -1L)
        else intent.getLongExtra(SmsSender.EXTRA_ROW_ID, -1L)
        if (rowId < 0) return
        val errorCode = resultCode
        val delivered = resultCode == Activity.RESULT_OK
        val status = if (delivered) SmsStatus.SENT else SmsStatus.FAILED
        val label = when {
            delivered -> ""
            isMms -> SmsErrorLabels.forMmsResult(errorCode)
            else -> SmsErrorLabels.forSmsResult(errorCode)
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.getInstance(context).smsMessageDao()
                    .setStatusWithError(rowId, status, isMms, errorCode, label)
            } finally {
                pending.finish()
            }
        }
    }
}