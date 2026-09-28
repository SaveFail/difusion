package com.masstext.app.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.masstext.app.import.ParsedRow

/**
 * Ventana flotante de REVISIÓN previa a importar desde Drive. Se muestra
 * después de elegir la hoja y antes de reemplazar la lista de contactos.
 *
 *  - Arriba, chips con las categorías de gestión (y "Sin gestionar" + "Todas").
 *  - Debajo, las filas de la categoría elegida con TODAS las columnas
 *    (Nombre, Cédula, Teléfono, Asignado a) y una casilla por fila.
 *  - Todas las casillas vienen marcadas; "Marcar todo"/"Nada" actúan sobre la
 *    categoría visible.
 *  - "Importar (N)" confirma y devuelve solo las filas marcadas.
 *
 * Es una ventana real de WindowManager (TYPE_APPLICATION_OVERLAY), el mismo
 * patrón que SheetPickerOverlay y CallOverlay.
 */
object ImportPreviewOverlay {

    private const val TAG = "ImportPreviewOverlay"
    private const val CAT_ALL = "\u0000ALL"
    private const val CAT_UNMANAGED = "\u0000NONE"
    private const val UNMANAGED_LABEL = "Sin gestionar"

    private const val BG = 0xFF16223A.toInt()
    private const val GREEN = 0xFF2ECC71.toInt()
    private const val CHIP = 0x3362A1F4.toInt()
    private const val TEXT = Color.WHITE
    private const val MUTED = 0xFFB8C4DA.toInt()
    private const val FAINT = 0xFF8FA0BC.toInt()

    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private var chipsContainer: LinearLayout? = null
    private var rowsContainer: LinearLayout? = null
    private var importButton: TextView? = null
    private var summaryText: TextView? = null

    private var onImport: ((List<ParsedRow>) -> Unit)? = null
    private var onCancel: (() -> Unit)? = null
    private var allRows: List<ParsedRow> = emptyList()
    private val selectedIndices = mutableSetOf<Int>()
    private var currentCategory: String = CAT_ALL

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isVisible(): Boolean = rootView != null

    fun show(
        context: Context,
        rows: List<ParsedRow>,
        onImport: (List<ParsedRow>) -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        hide(context)
        if (!canDraw(context) || rows.isEmpty()) return
        val window = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        allRows = rows
        selectedIndices.clear()
        selectedIndices.addAll(rows.indices)
        currentCategory = CAT_ALL
        this.onImport = onImport
        this.onCancel = onCancel

        val metrics = context.resources.displayMetrics
        val pillW = (metrics.widthPixels * 0.96f).toInt()
        val maxH = (metrics.heightPixels * 0.85f).toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 20).toFloat()
                setColor(BG)
            }
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
        }

        // --- Cabecera: título + cerrar ---
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerRow.addView(TextView(context).apply {
            text = "Revisar importación"
            setTextColor(TEXT)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(context, 4), dp(context, 1), dp(context, 4), dp(context, 1))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        headerRow.addView(TextView(context).apply {
            text = "✕"
            setTextColor(TEXT)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(context, 12), dp(context, 6), dp(context, 6), dp(context, 6))
            setOnClickListener {
                onCancel?.invoke()
                hide(context)
            }
        })
        root.addView(headerRow)

        summaryText = TextView(context).apply {
            setTextColor(MUTED)
            textSize = 12f
            setPadding(dp(context, 4), 0, dp(context, 4), dp(context, 6))
        }
        root.addView(summaryText)

        // --- Chips de categorías de gestión (scroll horizontal) ---
        val chipsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
        }
        chipsContainer = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        chipsScroll.addView(chipsContainer)
        root.addView(
            chipsScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(TextView(context).apply {
            text = "Nombre · Cédula · Teléfono · Asignado a"
            setTextColor(FAINT)
            textSize = 11f
            setPadding(dp(context, 4), dp(context, 6), dp(context, 4), dp(context, 2))
        })

        // --- Filas con casillas ---
        val scroll = ScrollView(context)
        rowsContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(rowsContainer)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        // --- Pie: marcar todo/nada + importar ---
        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 2), dp(context, 8), dp(context, 2), dp(context, 2))
        }
        footer.addView(footerButton(context, "Marcar todo") {
            selectedIndices.addAll(visibleIndices())
            renderRows(context)
            updateSummary()
        })
        footer.addView(footerButton(context, "Nada") {
            selectedIndices.removeAll(visibleIndices().toSet())
            renderRows(context)
            updateSummary()
        })
        importButton = TextView(context).apply {
            setTextColor(TEXT)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10))
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 14).toFloat()
                setColor(GREEN)
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { leftMargin = dp(context, 6) }
            setOnClickListener {
                val chosen = allRows.filterIndexed { index, _ -> index in selectedIndices }
                if (chosen.isEmpty()) {
                    Toast.makeText(context, "Marca al menos un contacto", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                onImport?.invoke(chosen)
                hide(context)
            }
        }
        footer.addView(importButton)
        root.addView(footer)

        val params = WindowManager.LayoutParams(
            pillW,
            maxH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBarHeight(context) + dp(context, 20)
        }

        try {
            window.addView(root, params)
            windowManager = window
            rootView = root
        } catch (e: Exception) {
            Log.e(TAG, "No pude dibujar la ventana de revisión: ${e.message}", e)
            return
        }
        renderChips(context)
        renderRows(context)
        updateSummary()
    }

    fun hide(context: Context) {
        rootView?.let { runCatching { windowManager?.removeView(it) } }
        rootView = null
        windowManager = null
        chipsContainer = null
        rowsContainer = null
        importButton = null
        summaryText = null
        onImport = null
        onCancel = null
        allRows = emptyList()
        selectedIndices.clear()
        currentCategory = CAT_ALL
    }

    // ---------- Datos ----------

    private fun categories(): List<String> =
        allRows.map { it.gestion.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    private fun hasUnmanaged(): Boolean = allRows.any { it.gestion.isBlank() }

    private fun countFor(category: String): Int = when (category) {
        CAT_ALL -> allRows.size
        CAT_UNMANAGED -> allRows.count { it.gestion.isBlank() }
        else -> allRows.count { it.gestion.trim() == category }
    }

    private fun matches(row: ParsedRow): Boolean = when (currentCategory) {
        CAT_ALL -> true
        CAT_UNMANAGED -> row.gestion.isBlank()
        else -> row.gestion.trim() == currentCategory
    }

    private fun visiblePairs(): List<Pair<Int, ParsedRow>> =
        allRows.withIndex().filter { matches(it.value) }.map { it.index to it.value }

    private fun visibleIndices(): List<Int> = visiblePairs().map { it.first }

    // ---------- Render ----------

    private fun renderChips(context: Context) {
        val container = chipsContainer ?: return
        container.removeAllViews()
        val options = mutableListOf(CAT_ALL)
        options.addAll(categories())
        if (hasUnmanaged()) options.add(CAT_UNMANAGED)
        options.forEach { category ->
            val label = when (category) {
                CAT_ALL -> "Todas"
                CAT_UNMANAGED -> UNMANAGED_LABEL
                else -> category
            }
            val chip = TextView(context).apply {
                text = "$label (${countFor(category)})"
                setTextColor(TEXT)
                textSize = 13f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6))
                background = GradientDrawable().apply {
                    cornerRadius = dp(context, 14).toFloat()
                    setColor(if (category == currentCategory) GREEN else CHIP)
                }
                setOnClickListener {
                    currentCategory = category
                    renderChips(context)
                    renderRows(context)
                    updateSummary()
                }
            }
            container.addView(
                chip,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = dp(context, 6) }
            )
        }
    }

    private fun renderRows(context: Context) {
        val container = rowsContainer ?: return
        container.removeAllViews()
        val pairs = visiblePairs()
        if (pairs.isEmpty()) {
            container.addView(TextView(context).apply {
                text = "Sin contactos en esta categoría"
                setTextColor(FAINT)
                textSize = 13f
                setPadding(dp(context, 6), dp(context, 12), dp(context, 6), dp(context, 12))
            })
            return
        }
        pairs.forEach { (index, row) ->
            val rowView = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(context, 2), dp(context, 4), dp(context, 2), dp(context, 4))
            }
            rowView.addView(CheckBox(context).apply {
                isChecked = index in selectedIndices
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedIndices.add(index) else selectedIndices.remove(index)
                    updateSummary()
                }
            })
            val info = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            info.addView(TextView(context).apply {
                text = row.name
                setTextColor(TEXT)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            val detail = buildString {
                val cedula = row.cedula.trim()
                if (cedula.isNotEmpty()) append("C.I. $cedula")
                val phone = row.phone.trim()
                if (phone.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append(phone)
                }
            }
            if (detail.isNotEmpty()) {
                info.addView(TextView(context).apply {
                    text = detail
                    setTextColor(MUTED)
                    textSize = 12f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
            }
            if (row.assignment.isNotBlank()) {
                info.addView(TextView(context).apply {
                    text = "Asignado a: ${row.assignment}"
                    setTextColor(FAINT)
                    textSize = 12f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
            }
            rowView.addView(info)
            container.addView(
                rowView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            container.addView(View(context).apply {
                setBackgroundColor(0x22FFFFFF)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)
                )
            })
        }
    }

    private fun updateSummary() {
        val selected = selectedIndices.size
        summaryText?.text =
            "Solo asignados a ti · ${allRows.size} disponibles · $selected seleccionados"
        importButton?.text = "Importar ($selected)"
    }

    private fun footerButton(context: Context, label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            setTextColor(TEXT)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 12).toFloat()
                setColor(CHIP)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(context, 6) }
            setOnClickListener { onClick() }
        }

    private fun statusBarHeight(context: Context): Int {
        val res = context.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) res.getDimensionPixelSize(id) else dp(context, 24)
    }
}
