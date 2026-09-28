package com.masstext.app.import

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader

        data class ParsedRow(
    val name: String,
    val phone: String,
    val cedula: String = "",
    val assignment: String = "",
    val gestion: String = ""
)

object Importer {

    private val venezuelanAreas = listOf(
        "412", "414", "416", "424", "426", "212", "241", "251", "261", "271", "281", "291",
        "231", "235", "237", "238", "243", "244", "245", "248", "249", "252", "253", "254", "255", "256",
        "257", "258", "259", "268", "269", "272", "273", "274", "275", "276", "277", "278", "279", "282",
        "283", "284", "285", "286", "287", "288", "292", "293", "295", "297", "298", "299"
    )

    // Convierte una celda de Excel a texto limpio.
    // Importante: Excel guarda el teléfono como número y sin los formatos acostumbrados,
    // 04122873438 queda como 4122873438 (número). Si se convierte con toString(),
    // Java usa notación científica: 4.122873438E9. Aquí se formatea el valor numérico
    // entero sin exponentes para que el número conserve todos sus dígitos.
    private fun formatCell(cell: org.apache.poi.ss.usermodel.Cell): String {
        return try {
            when (cell.cellType) {
                org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue.trim()
                org.apache.poi.ss.usermodel.CellType.NUMERIC -> {
                    val v = cell.numericCellValue
                    if (v == Math.rint(v) && !v.isInfinite() && Math.abs(v) < 1e15) {
                        java.math.BigDecimal.valueOf(v).stripTrailingZeros().toBigInteger().toString()
                    } else {
                        cell.toString()
                    }
                }
                else -> cell.toString()
            }
        } catch (e: Exception) {
            cell.toString()
        }
    }

    fun parseFile(context: Context, uri: Uri): List<ParsedRow> {
        val name = uri.toString()
        val rows = if (name.endsWith(".xlsx", ignoreCase = true) || name.endsWith(".xls", ignoreCase = true)) {
            parseExcel(context, uri)
        } else {
            parseCsv(context, uri)
        }
        return rows
    }

    private fun parseCsv(context: Context, uri: Uri): List<ParsedRow> {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("No se pudo abrir el archivo")
        input.use { stream ->
            val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
            val lines = mutableListOf<List<String>>()
            reader.forEachLine { line ->
                if (line.isNotBlank()) {
                    lines.add(parseCsvLine(line))
                }
            }
            return mapRows(lines)
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < line.length && line[i + 1] == '"') {
                            current.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        current.append(c)
                    }
                }
                c == '"' -> inQuotes = true
                c == ',' || c == ';' || c == '\t' -> {
                    result.add(current.toString().trim())
                    current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    private fun parseExcel(context: Context, uri: Uri): List<ParsedRow> {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("No se pudo abrir el archivo")
        val rows = mutableListOf<List<String>>()
        input.use { stream ->
            val book = org.apache.poi.ss.usermodel.WorkbookFactory.create(stream)
            val sheet = book.getSheetAt(0)
            val rowIterator = sheet.rowIterator()
            while (rowIterator.hasNext()) {
                val row = rowIterator.next()
                val values = mutableListOf<String>()
                val last = row.lastCellNum.toInt()
                var max = -1
                for (c in row.cellIterator()) {
                    val idx = c.columnIndex
                    if (idx > max) max = idx
                }
                val cellCount = if (max >= 0) max + 1 else 0
                for (i in 0 until cellCount) {
                    val cell = row.getCell(i)
                    values.add(if (cell != null) formatCell(cell) else "")
                }
                if (values.any { it.isNotBlank() }) {
                    rows.add(values)
                }
            }
            book.close()
        }
        return mapRows(rows)
    }

    private data class ColumnMap(
        val nameIdx: Int,
        val phoneIdx: Int,
        val assignIdx: Int,
        val hasHeader: Boolean,
        val cedulaIdx: Int = -1,
        val gestionIdx: Int = -1
    )

    private val nameKeywords = setOf(
        "nombre", "name", "cliente", "clientes", "nombres", "contacto", "razon", "apellidos", "apellido"
    )
    private val phoneKeywords = setOf(
        "telefono", "numero", "numeros", "celular", "movil", "moviles", "phone", "tel", "whatsapp"
    )
    private val assignKeywords = setOf(
        "asignado", "asignacion", "encargado", "responsable", "cobrador", "agente", "gestor",
        "repartidor", "ruta", "equipo", "lista", "representante", "vendedor", "asesor", "coordinador",
        "grupo", "segmento", "categoria", "agencia", "promotor", "tecnico", "instalador", "supervisor",
        "jefe", "comercial", "ejecutivo"
    )
    private val cedulaKeywords = setOf(
        "cedula", "cédula", "documento", "doc", "identificacion", "identificación",
        "identidad", "numero de documento", "num doc", "ci", "nro", "n°", "doc identidad"
    )
    // Categoría de gestión: columna independiente de "Asignado a". Sus valores
    // (PROMESA, NO CONTESTA, PAGO…) se usan para agrupar y filtrar contactos.
    private val gestionKeywords = setOf(
        "gestion", "gestión", "gestiones", "gestionado", "gestionada",
        "tipificacion", "tipificación", "tipificacion de gestion", "tipo de gestion",
        "categoria gestion", "categoria de gestion"
    )

    // Normaliza un encabezado: minúsculas y sin tildes, para reconocer encabezados
    // con o sin acentos (ej. "Teléfono", "Asignación").
    private fun fold(value: String): String {
        val normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
        val sb = StringBuilder()
        for (c in normalized) {
            if (c.code < 128) sb.append(Character.toLowerCase(c))
        }
        return sb.toString()
    }

    private fun analyzeColumns(header: List<String>): ColumnMap {
        val folded = header.map { fold(it) }
        fun matches(h: String, keywords: Set<String>) =
            h.isNotBlank() && keywords.any { k -> h == k || h.contains(k) }
        // La cédula/documento se detecta primero para que un encabezado como
        // "numero de documento" no se confunda con la columna de teléfono.
        val cedulaIdx = folded.indexOfFirst { matches(it, cedulaKeywords) }
        val nameIdx = folded.indexOfFirst { h ->
            matches(h, nameKeywords) && !matches(h, cedulaKeywords)
        }
        val phoneIdx = folded.indexOfFirst { h ->
            matches(h, phoneKeywords) && !matches(h, cedulaKeywords) && !h.contains("documento")
        }
        // La gestión se detecta antes que la asignación: un encabezado como
        // "Categoría gestión" no debe confundirse con la columna de asignación.
        val gestionIdx = folded.indexOfFirst { matches(it, gestionKeywords) }
        val assignIdx = folded.indexOfFirst { h ->
            matches(h, assignKeywords) && !matches(h, gestionKeywords)
        }
        if (phoneIdx >= 0) {
            // Hay encabezado (al menos se reconoce el teléfono). Si el nombre no
            // tiene encabezado claro ("Columna 1", "ID", …) se usa la primera
            // columna que no sea teléfono/cédula/asignación/gestión.
            val resolvedName = if (nameIdx >= 0) nameIdx else {
                folded.indices.firstOrNull {
                    it != phoneIdx && it != cedulaIdx && it != assignIdx && it != gestionIdx
                } ?: 0
            }
            return ColumnMap(resolvedName, phoneIdx, assignIdx, true, cedulaIdx, gestionIdx)
        }
        // Sin encabezado reconocible: se asume primera columna = nombre,
        // segunda = teléfono, tercera = asignación y cuarta = gestión.
        return ColumnMap(
            0, 1,
            if (header.size > 2) 2 else -1,
            false,
            -1,
            if (header.size > 3) 3 else -1
        )
    }

    private fun mapRows(raw: List<List<String>>): List<ParsedRow> {
        val result = mutableListOf<ParsedRow>()
        if (raw.isEmpty()) return result
        val map = analyzeColumns(raw[0])
        val startIdx = if (map.hasHeader) 1 else 0
        for (i in startIdx until raw.size) {
            val row = raw[i]
            if (row.isEmpty()) continue
            val name = row.getOrNull(map.nameIdx)?.trim().orEmpty()
            val phone = row.getOrNull(map.phoneIdx).orEmpty().trim()
            if (name.isBlank() || phone.isBlank()) continue
            // Se importa el teléfono EXACTAMENTE como está en el archivo (con su 0, signos y formato).
            // El arreglo del 0 (u otros) se aplica después, solo al enviar o llamar,
            // para no modificar los datos que el usuario coloca en su Excel.
            val assignment = row.getOrNull(map.assignIdx).orEmpty().trim()
            val cedula = row.getOrNull(map.cedulaIdx).orEmpty().trim()
            val gestion = row.getOrNull(map.gestionIdx).orEmpty().trim()
            result.add(ParsedRow(name, phone, cedula, assignment, gestion))
        }
        return result
    }

    fun normalizePhone(phone: String): String {
        val cleaned = phone.replace(Regex("[^0-9+]"), "")
        if (cleaned.isEmpty()) return cleaned
        if (cleaned.startsWith("+")) return cleaned
        if (cleaned.startsWith("0")) return cleaned
        // Excel elimina el 0 inicial de los números (los guarda como numéricos).
        // En Venezuela: 0412... -> 412...; 0212... -> 212...
        // Si el número quedó sin el 0 inicial, se lo restauramos automáticamente.
        return if (cleaned.length == 10 && venezuelanAreas.any { cleaned.startsWith(it) }) {
            "0$cleaned"
        } else {
            cleaned
        }
    }

    // Repara los números que una versión anterior leyó en notación científica de Excel.
    // Ejemplo: 04122873438 -> Excel numérico 4122873438 -> leído como "4.122873438E9"
    // -> al limpiar quedó "41228734389" (11 dígitos, último '9' parásito del exponente).
    // Si los primeros 10 dígitos son un prefijo venezolano válido, el último dígito no forma
    // parte del número y se descarta, restaurando el 0.
    fun repairScientificArtifact(phone: String): String {
        val cleaned = phone.replace(Regex("[^0-9+]"), "")
        if (cleaned.length != 11) return cleaned
        if (cleaned.startsWith("+") || cleaned.startsWith("0")) return cleaned
        val first10 = cleaned.substring(0, 10)
        return if (venezuelanAreas.any { first10.startsWith(it) }) {
            "0$first10"
        } else {
            cleaned
        }
    }

    /**
     * Descarga una hoja de Google Sheets compartida como pública ("Cualquier
     * persona con el enlace → Lector") y la convierte en los mismos ParsedRow
     * que se obtienen al importar un archivo. Acepta tanto el enlace de la
     * hoja (…/spreadsheets/d/ID/edit…) como un enlace ya exportado a CSV o a
     * "Publicar en la web" (…/pub?output=csv).
     */
    fun fetchPublicCsv(url: String): List<ParsedRow> {
        val exportUrl = toExportCsvUrl(url)
        val connection = java.net.URL(exportUrl).openConnection() as java.net.HttpURLConnection
        try {
            connection.connectTimeout = 20000
            connection.readTimeout = 20000
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "text/csv,text/plain,*/*")
            connection.setRequestProperty("User-Agent", "MassText/1.0")
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException(
                    "Google responde $code. Revisa que la hoja esté compartida como " +
                            "'Cualquier persona con el enlace → Lector'"
                )
            }
            val sb = StringBuilder()
            connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line -> sb.append(line).append('\n') }
            }
            val rows = mutableListOf<List<String>>()
            sb.toString().split('\n').forEach { line ->
                if (line.isNotBlank()) {
                    rows.add(parseCsvLine(line))
                }
            }
            return mapRows(rows)
        } finally {
            connection.disconnect()
        }
    }

    // Convierte el enlace de la hoja al formato de exportación CSV.
    // "edit" → export; "pub" → csv. Un enlace que ya termina en .csv o lleva
    // output=csv/format=csv se usa tal cual.
    private fun toExportCsvUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.endsWith(".csv", ignoreCase = true)) return trimmed
        if (trimmed.contains("format=csv") || trimmed.contains("output=csv")) return trimmed
        val spreadsheetsMatch = Regex("""https://docs\\.google\\.com/spreadsheets/d/([A-Za-z0-9_-]+)""").find(trimmed)
        if (spreadsheetsMatch != null) {
            val id = spreadsheetsMatch.groupValues[1]
            val gidMatch = Regex("[?&#]gid=(\\d+)").find(trimmed)
            val gid = if (gidMatch != null) "&gid=${gidMatch.groupValues[1]}" else ""
            return "https://docs.google.com/spreadsheets/d/$id/export?format=csv$gid"
        }
        val pubMatch = Regex("([A-Za-z0-9_-]{40,})/pub\\??.*").find(trimmed)
        if (pubMatch != null && trimmed.contains("/pub")) {
            val base = trimmed.substringBefore("?")
            return base + "?output=csv"
        }
        return trimmed
    }
        // Enumerar las hojas VISIBLES de ese Drive: baja el libro completo en
        // formato XLSX (no CSV — el CSV de Google solo exporta la pestaña activa)
        // y devuelve los nombres de TODAS sus hojas con un número de secuencia.
        // La ventana flotante las muestra como chips; al finalizar, se importa
        // SOLO la hoja seleccionada por su índice (getSheetAt).
        fun listVisibleSheets(url: String): List<String> {
            val exportUrl = toExportXlsxUrl(url)
            val connection = java.net.URL(exportUrl).openConnection() as java.net.HttpURLConnection
            try {
                connection.connectTimeout = 20000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "*/*")
                connection.setRequestProperty("User-Agent", "MassText/1.0")
                val code = connection.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException(
                        "Google responde $code al pedir el libro. Revisa que esté " +
                            "compartido como 'Cualquier persona con el enlace → Lector'"
                    )
                }
                return connection.inputStream.use { stream ->
                    // OOM-FIX: leer SOLO 'xl/workbook.xml' del ZIP (KB) con ZipInputStream.
                    // NO se construye el POI Workbook (que carga TODAS las celdas del libro
                    // → OOM 192MB). Los <sheet name="..."> viven en ese XML liviano.
                    val names = mutableListOf<String>()
                    java.util.zip.ZipInputStream(stream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null && entry.name != "xl/workbook.xml") {
                            entry = zis.nextEntry
                        }
                        if (entry != null) {
                            val xml = zis.readBytes().toString(Charsets.UTF_8)
                            java.util.regex.Pattern
                                .compile("""<sheet[^<>]*name="([^"]+)"""")
                                .matcher(xml)
                                .also { m -> while (m.find()) names.add(m.group(1)) }
                        }
                    }
                    names.toList()
                }
            } finally {
                connection.disconnect()
            }
        }

        // Importa UNA hoja específica (por índice) del Drive. Reutiliza el mismo
        // parseo de filas que fetchPublicCsv, pero en vez de asumir la hoja activa
        // (índice 0) baja el XLSX y abre getSheetAt(idx). Devuelve TODAS sus filas.
        fun importSheet(url: String, sheetIndex: Int): List<ParsedRow> {
            val exportUrl = toExportXlsxUrl(url)
            val connection = java.net.URL(exportUrl).openConnection() as java.net.HttpURLConnection
            try {
                connection.connectTimeout = 20000
                connection.readTimeout = 40000
                connection.instanceFollowRedirects = true
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,*/*")
                connection.setRequestProperty("User-Agent", "MassText/1.0")
                val code = connection.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException(
                        "Google responde $code. La hoja seleccionada ya no está disponible " +
                            "o el libro cambió de permisos."
                    )
                }
                // OOM-FIX: antes se usaba WorkbookFactory.create(), que carga TODAS
                // las hojas del libro en memoria (192 MB) y mataba la app con
                // OutOfMemoryError. Ahora se descarga el XLSX a un temporal y se
                // lee SOLO la hoja elegida con un parser XML en streaming.
                val tmp = java.io.File.createTempFile("drive_sheet", ".xlsx")
                try {
                    connection.inputStream.use { input ->
                        java.io.FileOutputStream(tmp).use { out -> input.copyTo(out, 64 * 1024) }
                    }
                    return readSheetStreaming(tmp, sheetIndex)
                } finally {
                    tmp.delete()
                }
            } finally {
                connection.disconnect()
            }
        }

    // ===== Lectura XLSX en streaming (sin cargar el libro completo) =====

    private fun attr(parser: org.xmlpull.v1.XmlPullParser, name: String): String? {
        for (i in 0 until parser.attributeCount) {
            val an = parser.getAttributeName(i)
            if (an == name || an.endsWith(":$name")) return parser.getAttributeValue(i)
        }
        return null
    }

    private fun colFromRef(ref: String?): Int {
        if (ref.isNullOrEmpty()) return -1
        var col = 0
        var seen = false
        for (ch in ref) {
            when {
                ch in 'A'..'Z' -> { col = col * 26 + (ch - 'A' + 1); seen = true }
                ch in 'a'..'z' -> { col = col * 26 + (ch - 'a' + 1); seen = true }
                else -> break
            }
        }
        return if (seen) col - 1 else -1
    }

    private fun cellText(
        type: String?,
        rawValue: String?,
        inlineText: String,
        shared: List<String>
    ): String {
        if (type == "inlineStr") return inlineText.trim()
        if (rawValue == null) return ""
        return when (type) {
            "s" -> shared.getOrNull(rawValue.trim().toIntOrNull() ?: -1)?.trim().orEmpty()
            "str" -> rawValue.trim()
            "b" -> if (rawValue.trim() == "1") "TRUE" else "FALSE"
            "e" -> ""
            else -> {
                val v = rawValue.trim().toDoubleOrNull() ?: return rawValue.trim()
                if (v == Math.rint(v) && !v.isInfinite() && Math.abs(v) < 1e15) {
                    java.math.BigDecimal.valueOf(v).stripTrailingZeros().toBigInteger().toString()
                } else {
                    rawValue.trim()
                }
            }
        }
    }

    private fun newParser(input: java.io.InputStream): org.xmlpull.v1.XmlPullParser {
        val parser = android.util.Xml.newPullParser()
        parser.setInput(input, null)
        return parser
    }

    private fun readSharedStrings(zip: java.util.zip.ZipFile): List<String> {
        val entry = zip.getEntry("xl/sharedStrings.xml") ?: return emptyList()
        val shared = mutableListOf<String>()
        zip.getInputStream(entry).use { input ->
            val parser = newParser(input)
            var event = parser.eventType
            var inSi = false
            val sb = StringBuilder()
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (event) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                        "si" -> { inSi = true; sb.setLength(0) }
                        "t" -> if (inSi) sb.append(parser.nextText())
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        if (parser.name == "si") { shared.add(sb.toString()); inSi = false }
                    }
                }
                event = parser.next()
            }
        }
        return shared
    }

    private fun readSheetRIds(zip: java.util.zip.ZipFile): List<String> {
        val entry = zip.getEntry("xl/workbook.xml") ?: return emptyList()
        val ids = mutableListOf<String>()
        zip.getInputStream(entry).use { input ->
            val parser = newParser(input)
            var event = parser.eventType
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (event == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "sheet") {
                    attr(parser, "id")?.let { ids.add(it) }
                }
                event = parser.next()
            }
        }
        return ids
    }

    private fun resolveSheetPath(zip: java.util.zip.ZipFile, rId: String): String? {
        val entry = zip.getEntry("xl/_rels/workbook.xml.rels") ?: return null
        var target: String? = null
        zip.getInputStream(entry).use { input ->
            val parser = newParser(input)
            var event = parser.eventType
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (event == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "Relationship") {
                    if (attr(parser, "Id") == rId) target = attr(parser, "Target")
                }
                event = parser.next()
            }
        }
        val t = target ?: return null
        return if (t.startsWith("/")) t.substring(1) else "xl/" + t.removePrefix("./")
    }

    private fun readSheetXml(
        zip: java.util.zip.ZipFile,
        path: String,
        shared: List<String>
    ): List<List<String>> {
        val entry = zip.getEntry(path) ?: return emptyList()
        val rows = mutableListOf<List<String>>()
        zip.getInputStream(entry).use { input ->
            val parser = newParser(input)
            var event = parser.eventType
            var currentRow: MutableList<String>? = null
            var colIdx = -1
            var nextCol = 0
            var cellType: String? = null
            var value: String? = null
            var inline = false
            val inlineSb = StringBuilder()
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (event) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                        "row" -> { currentRow = mutableListOf(); nextCol = 0 }
                        "c" -> {
                            val ref = attr(parser, "r")
                            if (ref != null) {
                                colIdx = colFromRef(ref)
                                nextCol = colIdx + 1
                            } else {
                                colIdx = nextCol
                                nextCol++
                            }
                            cellType = attr(parser, "t")
                            value = null
                            inline = false
                            inlineSb.setLength(0)
                        }
                        "v" -> value = parser.nextText()
                        "is" -> inline = true
                        "t" -> if (inline) inlineSb.append(parser.nextText())
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> when (parser.name) {
                        "c" -> {
                            val row = currentRow
                            if (row != null && colIdx >= 0) {
                                while (row.size <= colIdx) row.add("")
                                row[colIdx] = cellText(cellType, value, inlineSb.toString(), shared)
                            }
                        }
                        "row" -> {
                            currentRow?.let { r -> if (r.any { it.isNotBlank() }) rows.add(r) }
                            currentRow = null
                        }
                    }
                }
                event = parser.next()
            }
        }
        return rows
    }

    // Lee SOLO la hoja `sheetIndex` del XLSX (streaming, sin POI completo).
    private fun readSheetStreaming(file: java.io.File, sheetIndex: Int): List<ParsedRow> {
        java.util.zip.ZipFile(file).use { zip ->
            val shared = readSharedStrings(zip)
            val rIds = readSheetRIds(zip)
            if (sheetIndex !in rIds.indices) {
                throw IllegalStateException(
                    "La hoja $sheetIndex no existe (el libro tiene ${rIds.size} hojas)."
                )
            }
            val path = resolveSheetPath(zip, rIds[sheetIndex])
                ?: throw IllegalStateException("No pude ubicar la hoja $sheetIndex dentro del libro.")
            return mapRows(readSheetXml(zip, path, shared))
        }
    }

        private fun toExportXlsxUrl(url: String): String {
            val trimmed = url.trim()
            if (trimmed.endsWith(".xlsx", ignoreCase = true)) return trimmed
            if (trimmed.contains("format=xlsx")) return trimmed
            val m = Regex("""https://docs\.google\.com/spreadsheets/d/([A-Za-z0-9_-]+)""").find(trimmed)
            if (m != null) {
                return "https://docs.google.com/spreadsheets/d/${m.groupValues[1]}/export?format=xlsx"
            }
            val pub = Regex("""https://docs\.google\.com/spreadsheets/d/([A-Za-z0-9_-]+)/pub\??.*""").find(trimmed)
            if (pub != null) {
                return "https://docs.google.com/spreadsheets/d/${pub.groupValues[1]}/export?format=xlsx"
            }
            if (trimmed.startsWith("https://drive.google.com/uc?id=")) {
                val id = trimmed.removePrefix("https://drive.google.com/uc?id=").substringBefore("&")
                return "https://docs.google.com/uc?export=download&id=$id"
            }
            return trimmed
        }
}
