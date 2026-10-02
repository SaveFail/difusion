package com.difusion.app.report

import com.difusion.app.data.Contact
import com.difusion.app.data.SendRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    fun buildContactExcel(contacts: List<Contact>): ByteArray {
        val rows = ArrayList<List<String>>(contacts.size + 1)
        rows.add(listOf("Nombre", "Teléfono", "Asignación", "Tipificación", "Estado", "Medio"))
        contacts.forEach { contact ->
            rows.add(
                listOf(
                    contact.name,
                    com.difusion.app.import.Importer.normalizePhone(contact.phone),
                    contact.assignment,
                    contact.gestion,
                    contact.estado,
                    contact.medio
                )
            )
        }
        return XlsxWriter.write("Contactos", rows)
    }

    fun buildTemplateExcel(): ByteArray {
        val rows = listOf(
            listOf("Nombre", "Teléfono", "Asignación", "Tipificación", "Estado", "Medio"),
            listOf(
                "Ejemplo",
                "04122873438 (con el 0 al inicio)",
                "Juan Pérez",
                "NO CONTESTA",
                "PROMESA DE PAGO",
                "WHATSAPP"
            )
        )
        return XlsxWriter.write("Contactos", rows)
    }

    fun buildHistoryExcel(records: List<Pair<SendRecord, List<Contact>>>): ByteArray {
        val rows = ArrayList<List<String>>(records.size + 1)
        rows.add(listOf("Fecha", "Total", "Enviados", "Fallidos", "Mensaje", "Usuario"))
        val format = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        records.forEach { (record, _) ->
            rows.add(
                listOf(
                    format.format(Date(record.date)),
                    record.total.toString(),
                    record.sent.toString(),
                    record.failed.toString(),
                    record.message,
                    record.user
                )
            )
        }
        return XlsxWriter.write("Historial", rows)
    }
}
