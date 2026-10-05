package com.difusion.app.report

import com.difusion.app.data.Contact
import com.difusion.app.data.SendRecord
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ReportExporter {

    // Acepta las variantes de fecha que puede traer la hoja de Drive.
    private fun dateFormats(): List<SimpleDateFormat> = listOf(
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()),
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    ).onEach { it.timeZone = TimeZone.getDefault() }

    fun parseGestionDate(s: String): Date? {
        if (s.isBlank()) return null
        for (f in dateFormats()) {
            try {
                return f.parse(s.trim())
            } catch (_: ParseException) {
            }
        }
        return null
    }

    // Ordena por fecha de gestión (las que no tienen fecha van al final).
    fun sortByFechaGestion(contacts: List<Contact>): List<Contact> =
        contacts.sortedBy { parseGestionDate(it.fechaGestion) ?: Date(Long.MAX_VALUE) }

    // Nombre con el rango de fechas de gestión, p.ej.
    // contactos_gestion_20261001_20261005_20261005_112233
    fun exportBaseName(contacts: List<Contact>): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dates = contacts.mapNotNull { parseGestionDate(it.fechaGestion) }
        val out = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val min = dates.minOrNull()
        val max = dates.maxOrNull()
        val range = when {
            min != null && max != null && out.format(min) == out.format(max) -> out.format(min)
            min != null && max != null -> "${out.format(min)}_${out.format(max)}"
            min != null -> out.format(min)
            max != null -> out.format(max)
            else -> "sin_fecha_gestion"
        }
        return "contactos_gestion_${range}_$timestamp"
    }

    // Formato estándar (mismo que la hoja de Drive):
    // ID CUOTA, NOMBRES, CEDULA, MONTO, Telefono, SEGUIMIENTO, STATUS,
    // EJECUTIVO, MEDIO DE CONTACTO, FECHA DE GESTION
    val STANDARD_HEADERS = listOf(
        "ID CUOTA", "NOMBRES", "CEDULA", "MONTO", "Telefono",
        "SEGUIMIENTO", "STATUS", "EJECUTIVO", "MEDIO DE CONTACTO", "FECHA DE GESTION"
    )

    fun buildContactExcel(contacts: List<Contact>): ByteArray {
        val rows = ArrayList<List<String>>(contacts.size + 1)
        rows.add(STANDARD_HEADERS)
        contacts.forEach { contact ->
            rows.add(
                listOf(
                    contact.idCuota,
                    contact.name,
                    contact.cedula,
                    contact.monto,
                    contact.phone,
                    contact.gestion,
                    contact.estado,
                    contact.assignment,
                    contact.medio,
                    contact.fechaGestion
                )
            )
        }
        return XlsxWriter.write("Contactos", rows)
    }

    fun buildTemplateExcel(): ByteArray {
        val rows = listOf(
            STANDARD_HEADERS,
            listOf(
                "36973",
                "JESUS ALBERTO MENONES",
                "1.749.724",
                "$14,17",
                "4160969564",
                "NO CONTESTA",
                "PROMESA DE PAGO",
                "ALEJANDRA",
                "WHATSAPP",
                "01/10/2026 10:30"
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
