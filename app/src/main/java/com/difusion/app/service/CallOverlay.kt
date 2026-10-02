package com.difusion.app.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.difusion.app.ui.openCallScreen

/**
 * Barra flotante que se superpone sobre cualquier app mientras hay una
 * llamada en curso (requiere el permiso "Mostrar sobre otras aplicaciones").
 * Aparece en la parte superior de la pantalla y permite volver a la llamada,
 * alternar el altavoz o colgar sin salir de la app que estés usando.
 */
object CallOverlay {

    private const val TAG = "CallOverlay"

    private var windowManager: WindowManager? = null
    private var pillView: View? = null
    private var numberText: TextView? = null
    private var stateText: TextView? = null
    private var speakerButton: TextView? = null

    // Cuando la pantalla de llamada (CallActivity) está al frente, no se muestra
    // la barra flotante: ahí ya están todos los controles.
    @Volatile
    var suppressed: Boolean = false

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isVisible(): Boolean = pillView != null

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun statusBarHeight(context: Context): Int {
        val res = context.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) res.getDimensionPixelSize(id) else dp(context, 24)
    }

    fun show(context: Context) {
        if (suppressed) return
        if (pillView != null) return update(context)
        if (!canDraw(context)) return
        val info = CallMonitor.info.value ?: return
        val window = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val density = context.resources.displayMetrics.density

        fun chip(text: String, bg: Int, fg: Int = Color.WHITE, size: Float = 11f): TextView =
            TextView(context).apply {
                this.text = text
                setTextColor(fg)
                textSize = size
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
                val d = GradientDrawable()
                d.cornerRadius = dp(context, 12).toFloat()
                d.setColor(bg)
                background = d
            }

        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val bg = GradientDrawable()
            bg.cornerRadius = dp(context, 20).toFloat()
            bg.setColor(0xFF16223A.toInt())
            background = bg
            setPadding(dp(context, 10), dp(context, 7), dp(context, 8), dp(context, 7))
            isClickable = true
            setOnClickListener { openCallScreen(context) }
        }

        val tag = chip("LLAMADA", 0xFF2ECC71.toInt())

        numberText = TextView(context).apply {
            text = displayName(context) ?: info.number
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        stateText = TextView(context).apply {
            text = info.state
            setTextColor(Color.argb(200, 255, 255, 255))
            textSize = 11f
            maxLines = 1
        }
        val center = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        center.addView(numberText)
        center.addView(stateText)

        speakerButton = chip(
            if (CallMonitor.speaker.value) "Altavoz ON" else "Altavoz",
            0xFF2979FF.toInt()
        ).apply {
            setOnClickListener {
                CallMonitor.toggleSpeaker()
                text = if (CallMonitor.speaker.value) "Altavoz ON" else "Altavoz"
            }
        }
        val endButton = chip("Colgar", 0xFFE53935.toInt()).apply {
            setOnClickListener { CallMonitor.endCall() }
        }

        pill.addView(tag, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val centerParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(context, 10)
            rightMargin = dp(context, 6)
        }
        pill.addView(center, centerParams)
        pill.addView(speakerButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            rightMargin = dp(context, 6)
        })
        pill.addView(endButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val metrics = context.resources.displayMetrics
        val params = WindowManager.LayoutParams(
            metrics.widthPixels - dp(context, 32),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBarHeight(context) + dp(context, 4)
        }

        try {
            window.addView(pill, params)
            windowManager = window
            pillView = pill
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo dibujar el overlay: ${e.message}", e)
        }
    }

    fun update(context: Context) {
        val info = CallMonitor.info.value
        if (info == null) return hide(context)
        if (pillView == null) return show(context)
        numberText?.text = displayName(context) ?: info.number
        stateText?.text = info.state
        speakerButton?.text = if (CallMonitor.speaker.value) "Altavoz ON" else "Altavoz"
    }

    // Durante una secuencia consecutiva muestra el nombre del cliente actual
    // (en lugar del número), igual que en la notificación y en la tarjeta.
    private fun displayName(context: Context): String? {
        val sequencer = CallSequencer.getInstance(context)
        if (!sequencer.isRunning.value) return null
        return sequencer.currentContact.value?.name
    }

    fun hide(context: Context) {
        if (pillView == null) return
        runCatching { windowManager?.removeView(pillView) }
        pillView = null
        windowManager = null
        numberText = null
        stateText = null
        speakerButton = null
    }
}