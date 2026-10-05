// ============================================================================
// Difusión — Web App de Google Apps Script para ESCRIBIR gestiones en la hoja.
//
// Qué hace: recibe un POST con los datos de un contacto (ID CUOTA, Cédula,
// Teléfono, SEGUIMIENTO, STATUS, MEDIO DE CONTACTO, FECHA DE GESTION...) y
// actualiza SOLO la fila correspondiente. Identifica la fila por:
//   1) ID CUOTA  (lo más exacto)
//   2) Cédula + Teléfono
//   3) Teléfono
// Así varias personas pueden trabajar a la vez sin pisarse.
//
// CÓMO PUBLICARLO:
//   1. Abre tu hoja de Google Sheets.
//   2. Extensiones ▸ Apps Script.  Pega este archivo (reemplaza todo).
//   3. Ajusta SHEET_NAME si tu pestaña no se llama "Hoja 1".
//   4. Opcional: pon un API_TOKEN (mismo que en la app).
//   5. Implementar ▸ Nueva implementación ▸ Tipo: "Aplicación web".
//        - Ejecutar como: Yo
//        - Quién tiene acceso: Cualquier persona
//   6. Copia la URL .../exec y pégala en la app (Ajustes ▸ Drive ▸
//      "URL del Web App") + activa "Activar sincronización automática".
// ============================================================================

const SHEET_NAME = 'Hoja 1';   // <-- nombre de la pestaña (ajústalo)
const API_TOKEN  = '';          // <-- opcional: token compartido con la app

function doPost(e) {
  var lock = LockService.getScriptLock();
  try {
    lock.waitLock(30000);
  } catch (err) {
    return json({ ok: false, error: 'No se pudo obtener el lock' });
  }
  try {
    var data = JSON.parse(e.postData.contents);
    if (API_TOKEN && data.token !== API_TOKEN) {
      return json({ ok: false, error: 'Token inválido' });
    }

    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getSheetByName(SHEET_NAME) || ss.getSheets()[0];
    var lastRow = sheet.getLastRow();
    var lastCol = sheet.getLastColumn();
    if (lastRow < 1) return json({ ok: false, error: 'La hoja está vacía' });

    var headers = sheet.getRange(1, 1, 1, lastCol).getValues()[0];
    var idx = {};
    for (var c = 0; c < headers.length; c++) {
      var h = String(headers[c]).trim().toUpperCase();
      if (h) idx[h] = c + 1; // 1-based
    }
    function col() {
      for (var i = 0; i < arguments.length; i++) {
        var key = arguments[i].toUpperCase();
        if (idx[key]) return idx[key];
      }
      return -1;
    }

    var cIdCuota   = col('ID CUOTA', 'IDCUOTA', 'CUOTA');
    var cCedula    = col('CEDULA', 'CÉDULA', 'CI', 'DOCUMENTO');
    var cTelefono  = col('TELEFONO', 'TELÉFONO', 'CELULAR', 'MOVIL', 'MÓVIL', 'PHONE');
    var cSeguim    = col('SEGUIMIENTO', 'TIPIFICACION', 'TIPIFICACIÓN', 'GESTION', 'GESTIÓN');
    var cStatus    = col('STATUS', 'ESTADO', 'ESTATUS');
    var cMedio     = col('MEDIO DE CONTACTO', 'MEDIO', 'CANAL');
    var cFechaGest = col('FECHA DE GESTION', 'FECHA DE GESTIÓN', 'FECCHA DE GESTION', 'FECHA GESTION');
    var cEjecutivo = col('EJECUTIVO', 'EJECUTIVA', 'ASIGNADO');

    if (cTelefono < 0 && cIdCuota < 0 && cCedula < 0) {
      return json({ ok: false, error: 'No encuentro columnas para identificar la fila' });
    }

    // Carga de trabajo desde la fila 2.
    var n = lastRow - 1;
    var all = sheet.getRange(2, 1, n, Math.max(lastCol, 5)).getValues();

    function normTel(v) {
      var d = String(v == null ? '' : v).replace(/\D/g, '');
      if (d.indexOf('58') === 0) d = d.substring(2);
      if (d.charAt(0) === '0') d = d.substring(1);
      return d.slice(-10);
    }
    function normCed(v) {
      return String(v == null ? '' : v).replace(/\D/g, '');
    }

    var targetRow = -1;
    for (var r = 0; r < all.length; r++) {
      var row = all[r];
      var match = false;
      if (cIdCuota > 0 && data.idCuota) {
        match = String(row[cIdCuota - 1]).trim() === String(data.idCuota).trim();
      }
      if (!match && cCedula > 0 && cTelefono > 0 && data.cedula && data.phone) {
        match = normCed(row[cCedula - 1]) === normCed(data.cedula) &&
                normTel(row[cTelefono - 1]) === normTel(data.phone);
      }
      if (!match && cTelefono > 0 && data.phone) {
        match = normTel(row[cTelefono - 1]) === normTel(data.phone) &&
                (!cCedula || !data.cedula || normCed(row[cCedula - 1]) === normCed(data.cedula) || normCed(row[cCedula - 1]) === '');
      }
      if (match) { targetRow = r + 2; break; }
    }

    if (targetRow < 0) {
      return json({ ok: false, error: 'Fila no encontrada para ' + (data.idCuota || data.phone) });
    }

    function writeIfSet(colNum, value) {
      if (colNum > 0 && value !== undefined && value !== null) {
        sheet.getRange(targetRow, colNum).setValue(value);
      }
    }
    writeIfSet(cSeguim, data.gestion);
    writeIfSet(cStatus, data.estado);
    writeIfSet(cMedio, data.medio);
    writeIfSet(cFechaGest, data.fechaGestion);
    if (cEjecutivo > 0 && data.assignment) {
      var actual = sheet.getRange(targetRow, cEjecutivo).getValue();
      if (!actual) sheet.getRange(targetRow, cEjecutivo).setValue(data.assignment);
    }

    return json({ ok: true, row: targetRow });
  } catch (err) {
    return json({ ok: false, error: String(err) });
  } finally {
    lock.releaseLock();
  }
}

function doGet() {
  return json({ ok: true, message: 'Difusión Drive Sync activo' });
}

function json(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
