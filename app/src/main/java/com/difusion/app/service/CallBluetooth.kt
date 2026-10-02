package com.difusion.app.service

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import android.media.AudioManager

object CallPrefs {

    private const val PREFS = "difusion_prefs"
    private const val KEY_BT = "call_prefer_bluetooth"

    // Por defecto las llamadas se enrutan por un dispositivo Bluetooth si hay uno conectado.
    fun preferBluetooth(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BT, true)

    fun setPreferBluetooth(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_BT, value).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

object CallBluetooth {

    // Dispositivos de audio Bluetooth conectados en este momento (sin permisos).
    fun connectedBluetoothDevices(context: Context): List<AudioDeviceInfo> {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return emptyList()
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
                it.isSink && (
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun hasConnectedHeadset(context: Context): Boolean =
        connectedBluetoothDevices(context).isNotEmpty()
}