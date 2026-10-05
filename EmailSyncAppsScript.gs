// ============================================================================
// Difusión — Web App de Google Apps Script para ENVÍO MASIVO DE CORREOS.
//
// Envía desde tu cuenta de Google (GmailApp), así que:
//   - No se abre el cliente de correo del teléfono.
//   - Se respeta el límite diario de Gmail (evita bloqueos por spam).
//   - Puedes usar tu propio dominio/firma; el correo sale del remitente real.
//
// CÓMO PUBLICARLO:
//   1. Entra a https://script.google.com ▸ Nuevo proyecto.
//   2. Pega este archivo completo.
//   3. (Opcional) Pon el mismo API_TOKEN que en la app.
//   4. Implementar ▸ Nueva implementación ▸ Tipo "Aplicación web".
//        - Ejecutar como: Yo
//        - Quién tiene acceso: Cualquier persona
//   5. Copia la URL .../exec y pégala en la app
//      (Ajustes ▸ Drive ▸ "URL del Web App (correo)").
// ============================================================================

const API_TOKEN = '';            // <-- opcional: token compartido con la app
const FROM_NAME = 'Difusión';    // <-- nombre visible del remitente
const REPLY_TO  = '';            // <-- opcional: correo de respuesta
const BATCH_SIZE = 20;           // mensajes por tanda
const DELAY_MS   = 800;          // pausa entre tandas (evita spam)

function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    if (API_TOKEN && data.token !== API_TOKEN) {
      return json({ ok: false, error: 'Token inválido' });
    }

    var recipients = data.recipients || [];
    var subject = String(data.subject || '').trim();
    var body = String(data.body || '');
    var html = data.html === true;

    if (!subject || !body || recipients.length === 0) {
      return json({ ok: false, error: 'Faltan subject/body/recipients' });
    }

    var sent = 0, failed = 0, invalid = 0;
    for (var i = 0; i < recipients.length; i += BATCH_SIZE) {
      var batch = recipients.slice(i, i + BATCH_SIZE);
      for (var j = 0; j < batch.length; j++) {
        var email = String(batch[j] || '').trim().toLowerCase();
        if (!isEmail(email)) { invalid++; continue; }
        try {
          var options = { name: FROM_NAME };
          if (html) options.htmlBody = body; else options.body = body;
          if (REPLY_TO) options.replyTo = REPLY_TO;
          GmailApp.sendEmail(email, subject, html ? '' : body, options);
          sent++;
        } catch (err) {
          failed++;
        }
        Utilities.sleep(120);
      }
      if (i + BATCH_SIZE < recipients.length) Utilities.sleep(DELAY_MS);
    }

    return json({ ok: true, sent: sent, failed: failed, invalid: invalid, total: recipients.length });
  } catch (err) {
    return json({ ok: false, error: String(err) });
  }
}

function isEmail(s) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s);
}

function json(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

function doGet() {
  return json({ ok: true, message: 'Difusión Email Sync activo' });
}
