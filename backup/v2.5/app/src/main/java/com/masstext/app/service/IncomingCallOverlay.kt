package com.masstext.app.service

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

/**
 * Pantalla de llamada entrante que se superpone a cualquier app —y también al
 * bloqueo de pantalla— mientras suena el teléfono. Muestra el número que llama
 * y permite Contestar, enviar al Buzón o Rechazar. Requiere el permiso
 * "Mostrar sobre otras aplicaciones" (SYSTEM_ALERT_WINDOW).
 */
object IncomingCallOverlay {

    private const val TAG = "IncomingCallOverlay"
    private const val BG = 0xE616223A.toInt()
    private const val GREEN = 0xFF2ECC71.toInt()
    private const val RED = 0xFFE53935.toInt()
    private const val YELLOW = 0xFFF5A623.toInt()

    private var windowManager: WindowManager? = null
    private var callView: View? = null
    private var numberText: TextView? = null

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isVisible(): Boolean = callView != null

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun actionChip(context: Context, text: String, bg: Int): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            isClickable = true
            gravity = Gravity.CENTER
            setPadding(dp(context, 18), dp(context, 14), dp(context, 18), dp(context, 14))
            val d = GradientDrawable()
            d.cornerRadius = dp(context, 30).toFloat()
            d.setColor(bg)
            background = d
        }

    fun show(context: Context, number: String) {
        if (callView != null) return update(context, number)
        if (!canDraw(context)) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(BG)
            setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 24))
        }

        val title = TextView(context).apply {
            text = "Llamada entrante"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(title)

        numberText = TextView(context).apply {
            text = number
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 14), 0, dp(context, 14))
        }
        root.addView(numberText)

        val hint = TextView(context).apply {
            text = "¿Contestar o rechazar la llamada?"
            setTextColor(Color.argb(200, 255, 255, 255))
            textSize = 13f
            gravity = Gravity.CENTER
        }
        root.addView(hint)

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val answer = actionChip(context, "Contestar", GREEN).apply {
            setOnClickListener {
                CallMonitor.answerIncomingCall()
                hide(context)
            }
        }
        val voicemail = actionChip(context, "Buzón", YELLOW).apply {
            setOnClickListener {
                CallMonitor.rejectIncomingCall(true)
                hide(context)
            }
        }
        val reject = actionChip(context, "Rechazar", RED).apply {
            setOnClickListener {
                CallMonitor.rejectIncomingCall(false)
                hide(context)
            }
        }
        buttons.addView(
            answer,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(context, 8)
            }
        )
        buttons.addView(
            voicemail,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(context, 8)
                leftMargin = dp(context, 8)
            }
        )
        buttons.addView(
            reject,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(context, 8)
            }
        )
        root.addView(
            buttons,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 30) }
        )

        val metrics = context.resources.displayMetrics
        val params = WindowManager.LayoutParams(
            metrics.widthPixels,
            metrics.heightPixels,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            wm.addView(root, params)
            windowManager = wm
            callView = root
            Log.i(TAG, "Overlay de llamada entrante mostrado: $number")
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo mostrar el overlay entrante: ${e.message}", e)
        }
    }

    fun update(context: Context, number: String) {
        if (callView == null) {
            show(context, number)
            return
        }
        numberText?.text = number
    }

    fun hide(context: Context) {
        val v = callView ?: return
        runCatching { windowManager?.removeView(v) }
        callView = null
        windowManager = null
        numberText = null
    }
}