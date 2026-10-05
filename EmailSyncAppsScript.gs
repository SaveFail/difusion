// ============================================================================
// Difusión — Web App de Google Apps Script para CORREO (Gmail) y envío masivo.
//
// Hace TODO con tu cuenta de Google:
//   - Enviar correos masivos y programados  (action = "send")
//   - Ver la bandeja de entrada             (action = "list")
//   - Leer una conversación                 (action = "read")
//   - Responder una conversación            (action = "reply")
//   - Enviar un correo suelto               (action = "send")
//
// Es "iniciar sesión con tu cuenta de Google": el script se ejecuta COMO TÚ.
// Al desplegarlo por primera vez, Google te pedirá autorizar el acceso a Gmail.
// Nada de contraseñas viaja a la app; la app solo usa la URL del Web App + token.
//
// CÓMO PUBLICARLO:
//   1. https://script.google.com ▸ Nuevo proyecto.
//   2. Pega este archivo completo.
//   3. Pon un API_TOKEN largo y aleatorio (recomendado, va también en la app).
//   4. Implementar ▸ Nueva implementación ▸ Tipo "Aplicación web".
//        - Ejecutar como: Yo
//        - Quién tiene acceso: Cualquier persona (protegido por el token)
//   5. Autoriza con tu cuenta de Google cuando lo pida.
//   6. Copia la URL .../exec y pégala en la app (Mensaje ▸ Correo, o Ajustes).
// ============================================================================

const API_TOKEN = '';            // <-- token largo y aleatorio (recomendado)
const FROM_NAME = 'Difusión';    // nombre visible del remitente
const REPLY_TO  = '';            // opcional: correo de respuesta
const BATCH_SIZE = 20;
const DELAY_MS   = 800;

function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    if (API_TOKEN && data.token !== API_TOKEN) {
      return json({ ok: false, error: 'Token inválido' });
    }
    var action = (data.action || 'send').toString();
    switch (action) {
      case 'ping':  return actionPing();
      case 'list':  return actionList(data);
      case 'read':  return actionRead(data);
      case 'reply': return actionReply(data);
      case 'send':
      default:      return actionSend(data);
    }
  } catch (err) {
    return json({ ok: false, error: String(err) });
  }
}

// --- Probar conexión (devuelve la cuenta de Gmail conectada) ---
function actionPing() {
  var email = '';
  try { email = Session.getActiveUser().getEmail(); } catch (e) {}
  return json({ ok: true, email: email, unread: GmailApp.getInboxUnreadCount() });
}

// --- Enviar (masivo o uno) ---
function actionSend(data) {
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
      } catch (err) { failed++; }
      Utilities.sleep(120);
    }
    if (i + BATCH_SIZE < recipients.length) Utilities.sleep(DELAY_MS);
  }
  return json({ ok: true, sent: sent, failed: failed, invalid: invalid, total: recipients.length });
}

// --- Ver bandeja de entrada ---
function actionList(data) {
  var max = data.max || 25;
  var threads = GmailApp.getInboxThreads(0, max);
  var out = [];
  for (var i = 0; i < threads.length; i++) {
    var t = threads[i];
    var msgs = t.getMessages();
    var last = msgs[msgs.length - 1];
    out.push({
      threadId: t.getId(),
      from: last.getFrom(),
      subject: t.getFirstMessageSubject(),
      snippet: t.getSnippet(),
      date: last.getDate().toISOString(),
      unread: t.isUnread(),
      messageCount: msgs.length
    });
  }
  return json({ ok: true, messages: out, unread: GmailApp.getInboxUnreadCount() });
}

// --- Leer una conversación ---
function actionRead(data) {
  if (!data.threadId) return json({ ok: false, error: 'Falta threadId' });
  var t = GmailApp.getThreadById(data.threadId);
  if (!t) return json({ ok: false, error: 'Conversación no encontrada' });
  var msgs = t.getMessages();
  var list = [];
  for (var i = 0; i < msgs.length; i++) {
    var m = msgs[i];
    list.push({
      from: m.getFrom(),
      to: m.getTo(),
      date: m.getDate().toISOString(),
      body: m.getPlainBody(),
      isFromMe: fromMe(m)
    });
  }
  t.markRead();
  return json({ ok: true, subject: t.getFirstMessageSubject(), messages: list });
}

function fromMe(m) {
  try { return m.getFrom().indexOf(Session.getActiveUser().getEmail()) >= 0; }
  catch (e) { return false; }
}

// --- Responder ---
function actionReply(data) {
  if (!data.threadId || !data.body) return json({ ok: false, error: 'Faltan threadId/body' });
  var t = GmailApp.getThreadById(data.threadId);
  if (!t) return json({ ok: false, error: 'Conversación no encontrada' });
  t.reply(data.body, { name: FROM_NAME });
  return json({ ok: true });
}

function isEmail(s) { return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s); }

function json(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

function doGet() {
  return json({ ok: true, message: 'Difusión Correo (Gmail) activo' });
}
