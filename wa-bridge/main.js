'use strict';
/*
 * Worker WhatsApp (Baileys, open source, sin API de Meta).
 *
 * Corre en un PC/servidor con Node 18+. Expone:
 *   - HTTP (por defecto 0.0.0.0:3001) con token opcional (WA_TOKEN):
 *       GET  /status                 -> {state, qr, pairing}
 *       POST /pair   {phone}         -> solicita código de vinculación
 *       POST /send   {to,text}       -> envía mensaje (teléfono o JID)
 *       GET  /messages?since=<ms>    -> mensajes entrantes recientes
 *       GET  /bot                    -> {enabled, rules}
 *       POST /bot    {enabled,rules} -> configura el bot (auto-respuesta)
 *       GET  /                       -> ok
 *   - TCP NDJSON en 127.0.0.1 (uso embebido futuro).
 *
 * Variables de entorno:
 *   WA_HTTP_PORT=3001
 *   WA_HTTP_HOST=0.0.0.0
 *   WA_TOKEN=un-token-largo
 *   NODE_PATH=<carpeta para wa-auth>   (opcional; por defecto cwd)
 */

const net = require('net');
const http = require('http');
const fs = require('fs');
const path = require('path');
const pino = require('pino');

if (!globalThis.crypto) {
  try {
    const wc = require('crypto').webcrypto;
    if (wc) globalThis.crypto = wc;
  } catch (e) {
    console.log('[wa-worker] sin webcrypto disponible: ' + e.message);
  }
}

const FILES_DIR = process.env.NODE_PATH || process.argv[2] || process.cwd();
const AUTH_DIR = path.join(FILES_DIR, 'wa-auth');
const PORT_FILE = path.join(FILES_DIR, 'wa-port.txt');
const LOG_FILE = path.join(FILES_DIR, 'wa-worker.log');

const HTTP_PORT = Number(process.env.WA_HTTP_PORT || 3001);
const HTTP_HOST = process.env.WA_HTTP_HOST || '0.0.0.0';
const API_TOKEN = process.env.WA_TOKEN || '';

function logLine(msg) {
  try { fs.appendFileSync(LOG_FILE, `[${new Date().toISOString()}] ${msg}\n`); } catch (e) {}
  console.log(`[wa-worker] ${msg}`);
}

let sock = null;
let currentState = 'idle';
let lastQr = null;
let lastPairing = null;
let reconnectTimer = null;
let makeWASocket = null;
let useMultiFileAuthState = null;
let DisconnectReason = null;
let Browsers = null;
const clients = new Set();

// Bot: reglas {keyword, reply, match: 'contains'|'equals', stop:bool}
let botConfig = { enabled: false, rules: [] };
const messagesBuffer = [];

function broadcast(obj) {
  const line = JSON.stringify(obj);
  for (const c of clients) {
    try { c.write(line + '\n'); } catch (e) {}
  }
}

// ---------------- TCP NDJSON (embebido) ----------------
const server = net.createServer((socket) => {
  clients.add(socket);
  socket.setNoDelay(true);
  let buffer = '';
  socket.on('data', (d) => {
    buffer += d.toString('utf8');
    let idx;
    while ((idx = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, idx).trim();
      buffer = buffer.slice(idx + 1);
      if (line) handleLine(socket, line);
    }
  });
  socket.on('close', () => clients.delete(socket));
  socket.on('error', () => clients.delete(socket));
});

function handleLine(socket, line) {
  let msg;
  try { msg = JSON.parse(line); } catch (e) { return; }
  switch (msg.cmd) {
    case 'status':
      socket.write(JSON.stringify({ type: 'status', state: currentState }) + '\n');
      if (lastQr) socket.write(JSON.stringify({ type: 'qr', qr: lastQr }) + '\n');
      break;
    case 'pair':
      doPair(msg.phone, socket);
      break;
    case 'send':
      doSend(msg.jid || msg.to, msg.text, (ok, err) => {
        socket.write(JSON.stringify({ type: 'sent', id: msg.id || '', ok, err }) + '\n');
      });
      break;
    case 'erase':
      doDisconnect(true);
      socket.write(JSON.stringify({ type: 'status', state: currentState }) + '\n');
      break;
    case 'ping':
      socket.write(JSON.stringify({ type: 'pong' }) + '\n');
      break;
  }
}

// ---------------- Utilidades ----------------
function toJid(to) {
  const s = String(to || '').trim();
  if (!s) return null;
  if (s.includes('@')) return s;
  let d = s.replace(/[^0-9]/g, '');
  if (!d) return null;
  if (d.startsWith('0')) d = '58' + d.substring(1);
  return d + '@s.whatsapp.net';
}

function doPair(phone, socket) {
  if (!sock) {
    if (socket) socket.write(JSON.stringify({ type: 'error', msg: 'worker no conectado' }) + '\n');
    return;
  }
  if (currentState === 'open') {
    if (socket) socket.write(JSON.stringify({ type: 'error', msg: 'Ya está vinculado' }) + '\n');
    return;
  }
  const number = String(phone || '').trim().replace(/[^0-9]/g, '');
  if (!number) {
    if (socket) socket.write(JSON.stringify({ type: 'error', msg: 'Número vacío (usa código de país + número)' }) + '\n');
    return;
  }
  sock.requestPairingCode(number)
    .then((code) => {
      lastQr = null;
      lastPairing = code;
      currentState = 'pairing';
      broadcast({ type: 'pairing', code });
      if (socket) socket.write(JSON.stringify({ type: 'pairing', code }) + '\n');
      logLine('Código de vinculación: ' + code);
    })
    .catch((e) => {
      const m = e && e.message ? e.message : String(e);
      broadcast({ type: 'error', msg: 'pairing: ' + m });
      if (socket) socket.write(JSON.stringify({ type: 'error', msg: 'pairing: ' + m }) + '\n');
    });
}

function doSend(to, text, cb) {
  const jid = toJid(to);
  if (!sock || !jid || !text) {
    if (cb) cb(false, !sock ? 'no session' : (!jid ? 'destino inválido' : 'texto vacío'));
    return;
  }
  sock.sendMessage(jid, { text: String(text) })
    .then(() => { if (cb) cb(true, null); })
    .catch((e) => { if (cb) cb(false, e && e.message ? e.message : String(e)); });
}

function applyBot(jid, text) {
  if (!botConfig.enabled || !Array.isArray(botConfig.rules) || botConfig.rules.length === 0) return;
  const t = String(text || '').toLowerCase();
  for (const r of botConfig.rules) {
    if (!r || !r.keyword || !r.reply) continue;
    const kw = String(r.keyword).toLowerCase().trim();
    if (!kw) continue;
    const match = r.match === 'equals' ? t.trim() === kw : t.includes(kw);
    if (!match) continue;
    const reply = String(r.reply)
      .replace(/\{telefono\}/gi, jid.split('@')[0])
      .replace(/\{nombre\}/gi, jid.split('@')[0]);
    doSend(jid, reply, (ok, err) => {
      logLine('bot ' + (ok ? 'respondió' : 'falló') + ' a ' + jid + (err ? ' -> ' + err : ''));
    });
    if (r.stop !== false) break;
  }
}

// ---------------- HTTP ----------------
function json(res, code, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(code, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type, x-token',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS'
  });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve) => {
    let b = '';
    req.on('data', (d) => { b += d; if (b.length > 1e6) req.destroy(); });
    req.on('end', () => {
      try { resolve(JSON.parse(b || '{}')); } catch (e) { resolve({}); }
    });
  });
}

function authorized(req, url) {
  if (!API_TOKEN) return true;
  const header = req.headers['x-token'] || '';
  const q = url.searchParams.get('token') || '';
  return header === API_TOKEN || q === API_TOKEN;
}

const httpServer = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  if (req.method === 'OPTIONS') return json(res, 200, { ok: true });
  if (!authorized(req, url)) return json(res, 401, { ok: false, error: 'token inválido' });

  try {
    if (req.method === 'GET' && url.pathname === '/') {
      return json(res, 200, { ok: true, service: 'difusion-wa', state: currentState });
    }
    if (req.method === 'GET' && url.pathname === '/status') {
      return json(res, 200, {
        ok: true, state: currentState, qr: lastQr, pairing: lastPairing
      });
    }
    if (req.method === 'POST' && url.pathname === '/pair') {
      const body = await readBody(req);
      doPair(body.phone, null);
      return json(res, 200, { ok: true });
    }
    if (req.method === 'POST' && url.pathname === '/send') {
      const body = await readBody(req);
      return new Promise((resolve) => {
        doSend(body.to, body.text, (ok, err) => {
          json(res, ok ? 200 : 502, { ok, error: err || null });
          resolve();
        });
      });
    }
    if (req.method === 'GET' && url.pathname === '/messages') {
      const since = Number(url.searchParams.get('since') || 0);
      const list = messagesBuffer.filter((m) => m.ts > since);
      return json(res, 200, { ok: true, messages: list });
    }
    if (req.method === 'GET' && url.pathname === '/bot') {
      return json(res, 200, { ok: true, ...botConfig });
    }
    if (req.method === 'POST' && url.pathname === '/bot') {
      const body = await readBody(req);
      botConfig = {
        enabled: !!body.enabled,
        rules: Array.isArray(body.rules) ? body.rules.slice(0, 500) : []
      };
      logLine('bot actualizado: enabled=' + botConfig.enabled + ' rules=' + botConfig.rules.length);
      return json(res, 200, { ok: true, ...botConfig });
    }
    return json(res, 404, { ok: false, error: 'no encontrado' });
  } catch (e) {
    return json(res, 500, { ok: false, error: e && e.message ? e.message : String(e) });
  }
});

// ---------------- Baileys ----------------
function startBridge() {
  server.listen(0, '127.0.0.1', () => {
    const port = server.address().port;
    try { fs.writeFileSync(PORT_FILE, String(port)); } catch (e) {}
    logLine('TCP bridge en 127.0.0.1:' + port);
    broadcast({ type: 'log', level: 'info', msg: 'worker listo' });
    startBaileys();
  });
  httpServer.listen(HTTP_PORT, HTTP_HOST, () => {
    logLine('HTTP en http://' + HTTP_HOST + ':' + HTTP_PORT + (API_TOKEN ? ' (con token)' : ' (sin token)'));
  });
}

async function startBaileys() {
  if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null; }
  let auth;
  try {
    auth = await useMultiFileAuthState(AUTH_DIR);
  } catch (e) {
    logLine('Auth state fallo: ' + (e && e.message));
    broadcast({ type: 'error', msg: 'auth: ' + (e && e.message) });
    return;
  }

  sock = makeWASocket({
    auth: auth.state,
    browser: Browsers.windows('Difusion'),
    syncFullHistory: false,
    connectTimeoutMs: 30000,
    logger: pino({ level: 'info', base: undefined })
  });

  sock.ev.on('creds.update', auth.saveCreds);

  sock.ev.on('connection.update', (update) => {
    const { connection, lastDisconnect, qr } = update;
    if (qr) {
      lastQr = qr;
      broadcast({ type: 'qr', qr });
    }
    if (connection) {
      currentState = connection;
      let errMsg = null;
      let shouldReconnect = false;
      if (connection === 'close') {
        const err = (lastDisconnect && lastDisconnect.error) || null;
        const code = err && err.output ? err.output.statusCode : (err && err.statusCode);
        shouldReconnect = code !== DisconnectReason.loggedOut && code !== DisconnectReason.badSession;
        errMsg = code !== undefined ? `code ${code}` : (err ? String(err.message || err) : 'unknown');
        lastQr = null;
      }
      if (connection === 'open') { lastQr = null; lastPairing = null; }
      broadcast({ type: 'status', state: connection, err: errMsg, reconnect: shouldReconnect });
      logLine('connection.update: ' + JSON.stringify({ connection, err: errMsg, reconnect: shouldReconnect }));
      if (connection === 'close' && shouldReconnect) {
        reconnectTimer = setTimeout(() => { logLine('Reconectando...'); startBaileys(); }, 8000);
      }
    }
  });

  sock.ev.on('messages.upsert', (ev) => {
    if (ev.type !== 'notify') return;
    for (const m of ev.messages) {
      const key = m.key || {};
      if (!key.remoteJid) continue;
      const text = (m.message && (m.message.conversation || (m.message.extendedTextMessage && m.message.extendedTextMessage.text))) || null;
      if (text == null) continue;
      const fromMe = !!key.fromMe;
      const ts = (m.messageTimestamp ? Number(m.messageTimestamp) * 1000 : Date.now());
      broadcast({ type: 'message', jid: key.remoteJid, fromMe, text, ts });
      messagesBuffer.push({ jid: key.remoteJid, fromMe, text, ts });
      if (messagesBuffer.length > 500) messagesBuffer.shift();
      if (!fromMe) applyBot(key.remoteJid, text);
    }
  });
}

function doDisconnect(eraseAuth) {
  if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null; }
  currentState = 'idle';
  lastQr = null;
  lastPairing = null;
  broadcast({ type: 'status', state: 'idle' });
  if (sock) {
    try { if (eraseAuth) sock.logout(); else sock.end(undefined); } catch (e) {}
    sock = null;
  }
  if (eraseAuth) {
    try {
      const files = fs.readdirSync(AUTH_DIR);
      for (const f of files) fs.rmSync(path.join(AUTH_DIR, f), { recursive: true, force: true });
    } catch (e) {}
  }
}

process.on('uncaughtException', (e) => {
  logLine('Uncaught: ' + (e && e.stack ? e.stack : e));
  broadcast({ type: 'error', msg: 'uncaught: ' + (e && e.message) });
});
process.on('unhandledRejection', (e) => {
  logLine('UnhandledRejection: ' + (e && (e.stack || e.message) || e));
});

logLine('Worker iniciado. filesDir=' + FILES_DIR);

(async () => {
  const { default: makeWASocket2, useMultiFileAuthState: useMultiFileAuthState2, DisconnectReason: DisconnectReason2, Browsers: Browsers2 } =
    await import('@whiskeysockets/baileys');
  makeWASocket = makeWASocket2;
  useMultiFileAuthState = useMultiFileAuthState2;
  DisconnectReason = DisconnectReason2;
  Browsers = Browsers2;
  startBridge();
})().catch((e) => {
  logLine('Bootstrap fallo: ' + (e && (e.stack || e.message)));
  broadcast({ type: 'error', msg: 'bootstrap: ' + (e && e.message) });
  process.exit(1);
});
