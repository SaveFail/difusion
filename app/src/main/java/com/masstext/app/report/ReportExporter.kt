package com.masstext.app.report

import com.masstext.app.data.Contact
import com.masstext.app.data.SendRecord
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    fun buildContactExcel(contacts: List<Contact>): ByteArray {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Contactos")
        val header = sheet.createRow(0)
        header.createCell(0).setCellValue("Nombre")
        header.createCell(1).setCellValue("Teléfono")
        header.createCell(2).setCellValue("Asignación")
        header.createCell(3).setCellValue("Tipificación")
        header.createCell(4).setCellValue("Estado")
        header.createCell(5).setCellValue("Medio")
        contacts.forEachIndexed { index, contact ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(contact.name)
            val cell = row.createCell(1)
            cell.setCellValue(com.masstext.app.import.Importer.normalizePhone(contact.phone))
            row.createCell(2).setCellValue(contact.assignment)
            row.createCell(3).setCellValue(contact.gestion)
            row.createCell(4).setCellValue(contact.estado)
            row.createCell(5).setCellValue(contact.medio)
        }
        sheet.createFreezePane(0, 1)
        val out = java.io.ByteArrayOutputStream()
        wb.write(out)
        wb.close()
        return out.toByteArray()
    }

    fun buildTemplateExcel(): ByteArray {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Contactos")
        val header = sheet.createRow(0)
        header.createCell(0).setCellValue("Nombre")
        header.createCell(1).setCellValue("Teléfono")
        header.createCell(2).setCellValue("Asignación")
        header.createCell(3).setCellValue("Tipificación")
        header.createCell(4).setCellValue("Estado")
        header.createCell(5).setCellValue("Medio")
        val example = sheet.createRow(1)
        example.createCell(0).setCellValue("Ejemplo")
        val cell = example.createCell(1)
        cell.setCellValue("04122873438 (con el 0 al inicio)")
        example.createCell(2).setCellValue("Juan Pérez")
        example.createCell(3).setCellValue("NO CONTESTA")
        example.createCell(4).setCellValue("PROMESA DE PAGO")
        example.createCell(5).setCellValue("WHATSAPP")
        sheet.createFreezePane(0, 1)
        val out = java.io.ByteArrayOutputStream()
        wb.write(out)
        wb.close()
        return out.toByteArray()
    }

    fun buildHistoryExcel(records: List<Pair<SendRecord, List<Contact>>>): ByteArray {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Historial")
        val header = sheet.createRow(0)
        header.createCell(0).setCellValue("Fecha")
        header.createCell(1).setCellValue("Total")
        header.createCell(2).setCellValue("Enviados")
        header.createCell(3).setCellValue("Fallidos")
        header.createCell(4).setCellValue("Mensaje")
        header.createCell(5).setCellValue("Usuario")
        records.forEachIndexed { index, (record, _) ->
            val row = sheet.createRow(index + 1)
            val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(record.date))
            row.createCell(0).setCellValue(date)
            row.createCell(1).setCellValue(record.total.toDouble())
            row.createCell(2).setCellValue(record.sent.toDouble())
            row.createCell(3).setCellValue(record.failed.toDouble())
            row.createCell(4).setCellValue(record.message)
            row.createCell(5).setCellValue(record.user)
        }
        sheet.createFreezePane(0, 1)
        val out = java.io.ByteArrayOutputStream()
        wb.write(out)
        wb.close()
        return out.toByteArray()
    }
}
