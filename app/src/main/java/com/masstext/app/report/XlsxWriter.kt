package com.masstext.app.report

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Escritor XLSX mínimo (OOXML) sin Apache POI. Genera el .xlsx como un ZIP con
 * los XML imprescindibles y celdas de texto en línea (`inlineStr`). Mantiene el
 * APK y el uso de memoria muy bajos, pensado para equipos de pocos recursos.
 */
object XlsxWriter {

    fun write(sheetName: String, rows: List<List<String>>): ByteArray {
        val out = ByteArrayOutputStream(64 * 1024)
        ZipOutputStream(out).use { zip ->
            zip.entry("[Content_Types].xml", CONTENT_TYPES)
            zip.entry("_rels/.rels", ROOT_RELS)
            zip.entry("xl/workbook.xml", workbookXml(sheetName))
            zip.entry("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            writeSheet(zip, rows)
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun workbookXml(sheetName: String): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="${esc(sheetName)}" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    private fun writeSheet(zip: ZipOutputStream, rows: List<List<String>>) {
        val head = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><sheetData>"""
        zip.write(head.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(256)
        rows.forEachIndexed { r, row ->
            sb.setLength(0)
            val rowNum = r + 1
            sb.append("<row r=\"").append(rowNum).append("\">")
            row.forEachIndexed { c, value ->
                if (value.isEmpty()) return@forEachIndexed
                sb.append("<c r=\"").append(colRef(c)).append(rowNum)
                    .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(esc(value)).append("</t></is></c>")
            }
            sb.append("</row>")
            zip.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
        zip.write("</sheetData></worksheet>".toByteArray(Charsets.UTF_8))
    }

    private fun colRef(index: Int): String {
        var i = index
        val sb = StringBuilder(3)
        while (true) {
            sb.insert(0, ('A' + (i % 26)))
            i = i / 26 - 1
            if (i < 0) break
        }
        return sb.toString()
    }

    private fun esc(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (ch.code >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') append(ch)
            }
        }
    }

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""
}
