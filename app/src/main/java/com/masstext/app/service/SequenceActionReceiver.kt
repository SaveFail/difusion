package com.masstext.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SequenceActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sequencer = CallSequencer.getInstance(context)
        when (intent.action) {
            SequenceNotification.ACTION_PAUSE -> {
                if (sequencer.isRunning.value && !sequencer.isPaused.value) {
                    sequencer.pause()
                }
            }
            SequenceNotification.ACTION_RESUME -> {
                if (sequencer.isRunning.value && sequencer.isPaused.value) {
                    sequencer.resume()
                }
            }
            SequenceNotification.ACTION_STOP -> {
                sequencer.stop()
                SequenceNotification.cancel(context)
            }
        }
    }
}