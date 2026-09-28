package com.masstext.app.report

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class XlsxWriterTest {

    @Test
    fun generatesValidXlsx() {
        val bytes = XlsxWriter.write(
            "Contactos",
            listOf(
                listOf("Nombre", "Teléfono", "Tipificación"),
                listOf("José & Cía <x>", "0412-1234567", "NO CONTESTA"),
                listOf("María", "04140000000", "PAGO")
            )
        )
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("_rels/.rels"))
        assertTrue(entries.containsKey("xl/workbook.xml"))
        assertTrue(entries.containsKey("xl/_rels/workbook.xml.rels"))
        val sheet = entries["xl/worksheets/sheet1.xml"] ?: ""
        assertTrue(sheet.contains("José &amp; Cía &lt;x&gt;"))
        assertTrue(sheet.contains("0412-1234567"))
        assertTrue(sheet.contains("NO CONTESTA"))
        assertTrue(sheet.contains("<row r=\"3\">"))
        assertTrue(sheet.contains("r=\"C2\""))
        java.io.File("build/test-output.xlsx").writeBytes(bytes)
        println("XLSX_SIZE=" + bytes.size)
    }
}
