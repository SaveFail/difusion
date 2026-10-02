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
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.ScrollView
import android.widget.TextView

/**
 * Ventana flotante que muestra las hojas (pestañas) VISIBLES de un libro de
 * Google Sheets sincronizado desde Drive. Se superpone sobre cualquier app
 * (igual que CallOverlay: requiere "Mostrar sobre otras aplicaciones", ya
 * concedido por el overlay de llamadas) y permite:
 *
 *  1. Elegir UNA de las hojas listadas (la selección queda resaltada).
 *  2. Pulsar "Finalizar sincronización" para que se importe SOLO esa hoja.
 *
 * Es una ventana real de WindowManager con TYPE_APPLICATION_OVERLAY — el
 * mismo patrón que usa CallOverlay, clonado para mantener UNA sola fuente
 * de verdad en cómo se dibujan ventanas flotantes en la app.
 */
object SheetPickerOverlay {

    private const val TAG = "SheetPickerOverlay"

    private var windowManager: WindowManager? = null
    private var pillView: View? = null
    private var sheetLabels = emptyList<TextView>()
    private var sheetNames = emptyList<String>()
    private var finalizeButton: TextView? = null
    private var selectedIndex = -1
    private var onPick: ((Int) -> Unit)? = null
    private var onFinalize: (() -> Unit)? = null
    private var onCancel: (() -> Unit)? = null

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isVisible(): Boolean = pillView != null

    /** Muestra la ventana con las hojas del Drive. No-bloqueante: la selección
     *  y el botón Finalizar se enrutan por los callbacks. */
    fun show(
        context: Context,
        sheets: List<String>,
        initialSelected: Int = -1,
        onPick: (Int) -> Unit,
        onFinalize: () -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        if (pillView != null) {
            update(context, sheets, initialSelected)
            return
        }
        if (!canDraw(context)) return
        if (sheets.isEmpty()) return
        val window = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        this.sheetLabels = sheets.map { TextView(context).apply {
            textSize = 17f
            setPadding(dp(context, 6), dp(context, 5), dp(context, 6), dp(context, 5))
        } }
        this.sheetNames = sheets
        this.selectedIndex = initialSelected
        this.onPick = onPick
        this.onFinalize = onFinalize
        this.onCancel = onCancel

        // Píldora vertical: cabecera + botón Finalizar ARRIBA (zona siempre
        // táctil) y la lista de hojas con scroll debajo.
        val maxH = (context.resources.displayMetrics.heightPixels * 0.6f).toInt()
        val pillW = (context.resources.displayMetrics.widthPixels * 0.96f).toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable()
            bg.cornerRadius = dp(context, 20).toFloat()
            bg.setColor(0xFF16223A.toInt())
            background = bg
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
            layoutParams = ViewGroup.LayoutParams(pillW, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val pillContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val header = TextView(context).apply {
            text = "Elige la hoja"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(context, 4), dp(context, 1), dp(context, 4), dp(context, 1))
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        headerRow.addView(header)

        finalizeButton = TextView(context).apply {
            text = "✓ Finalizar"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8))
            val d = GradientDrawable()
            d.cornerRadius = dp(context, 14).toFloat()
            d.setColor(0xFF2ECC71.toInt())
            background = d
            setOnClickListener {
                if (sheets.isEmpty()) return@setOnClickListener
                val idx = selectedIndex
                if (idx !in sheets.indices) {
                    Toast.makeText(context, "Primero elige una hoja", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                onPick?.invoke(idx)
                onFinalize?.invoke()
                hide(context)
            }
        }
        headerRow.addView(finalizeButton)

        val closeButton = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(context, 12), dp(context, 6), dp(context, 6), dp(context, 6))
            setOnClickListener {
                onCancel?.invoke()
                hide(context)
            }
        }
        headerRow.addView(closeButton)
        pillContent.addView(headerRow)

        // Hojas: cada una en un chip. La elegida se marca en verde.
        sheetLabels.forEachIndexed { i, label ->
            label.text = sheets[i]
            val chip = asViewFor(i).also { v ->
                v.setOnClickListener {
                    setSelected(i)
                    onPick?.invoke(i)
                }
            }
            pillContent.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 4) })
        }
        root.addView(pillContent, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val metrics = context.resources.displayMetrics
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBarHeight(context) + dp(context, 30)
        }

        try {
            window.addView(root, params)
            pillView = root
            windowManager = window
        } catch (e: Exception) {
            Log.e(TAG, "No pude dibujar el picker: ${e.message}", e)
        }
    }

    /** Marca la hoja elegida (según el índice del ViewModel) y refresca los chips. */
    fun update(
        context: Context,
        sheets: List<String>,
        selectedIndex: Int = this.selectedIndex
    ) {
        // No se borra la selección local si el ViewModel llega en -1.
        if (selectedIndex in sheets.indices) this.selectedIndex = selectedIndex
        adjustColors()
    }

    fun setSelected(index: Int) {
        // Tocar una hoja la SELECCIONA (sin toggle: no se desmarca al re-tocar).
        selectedIndex = index
        adjustColors()
    }

    fun hide(context: Context) {
        pillView?.let { runCatching { windowManager?.removeView(it) } }
        pillView = null
        windowManager = null
        sheetLabels = emptyList()
        finalizeButton = null
        selectedIndex = -1
        onPick = null
        onFinalize = null
        onCancel = null
    }

    private fun adjustColors() {
        sheetLabels.forEachIndexed { i, label ->
            label.text = sheetNames[i]
            val chipBg = GradientDrawable()
            chipBg.cornerRadius = dp(label.context, 10).toFloat()
            if (i == selectedIndex) {
                label.setTextColor(Color.WHITE)
                chipBg.setColor(0xFF2ECC71.toInt())
                label.text = "✓ ${sheetNames[i]}"
            } else {
                label.setTextColor(Color.WHITE)
                chipBg.setColor(0x3362A1F4.toInt())
            }
            label.background = chipBg
        }
    }

    private fun asViewFor(index: Int): TextView =
        sheetLabels.getOrNull(index) ?: TextView(null).also { it.text = "" }

    private fun statusBarHeight(context: Context): Int {
        val res = context.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) res.getDimensionPixelSize(id) else dp(context, 24)
    }
}
