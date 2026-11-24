#!/usr/bin/env node
'use strict';
/*
 * Conector entre la cola de notificaciones de Keoss y el worker de WhatsApp.
 *
 *   Keoss  →  GET /api/credit/notificaciones/trabajo?canal=whatsapp
 *   este   →  POST http://127.0.0.1:3001/send {to, text}
 *   Keoss  →  POST /api/credit/notificaciones/resultado {id, estado}
 *
 * Variables (archivo .env junto a este script o entorno):
 *   KEOSS_URL=https://www.keosspay.com
 *   KEOSS_TOKEN=<token del equipo en Keoss → Crédito → Notificaciones → Equipos>
 *   WA_URL=http://127.0.0.1:3001        (worker Baileys / open-wa)
 *   WA_TOKEN=                            (si el worker pide token)
 *   CANAL=whatsapp                       (whatsapp | sms | email)
 *   POLL=15                              (segundos entre consultas)
 *   DRY_RUN=1                            (no envía y no reporta: solo logs)
 *
 * Uso:
 *   node keoss-queue.js --check   → estado del worker y de la cola
 *   node keoss-queue.js --once    → procesa una sola vez
 *   node keoss-queue.js           → bucle continuo (dejar con pm2/systemd)
 */
const fs = require('fs');
const path = require('path');

// .env sencillo (sin dependencias)
function loadEnv() {
  const file = process.env.KEOSS_QUEUE_ENV || path.join(__dirname, '.env');
  try {
    if (!fs.existsSync(file)) return;
    for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
      const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/i);
      if (!m) continue;
      const key = m[1];
      let val = m[2].replace(/\s+#.*$/, '').trim();
      if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
        val = val.slice(1, -1);
      }
      if (process.env[key] === undefined) process.env[key] = val;
    }
  } catch (e) { /* sin .env */ }
}
loadEnv();

const CFG = {
  keoss: (process.env.KEOSS_URL || 'https://www.keosspay.com').replace(/\/+$/, ''),
  token: process.env.KEOSS_TOKEN || '',
  wa: (process.env.WA_URL || 'http://127.0.0.1:3001').replace(/\/+$/, ''),
  waToken: process.env.WA_TOKEN || '',
  canal: process.env.CANAL || 'whatsapp',
  poll: Math.max(5, Number(process.env.POLL || 15)),
  dry: process.env.DRY_RUN === '1',
  limit: Math.max(1, Math.min(50, Number(process.env.LIMIT || 10))),
};

const log = (...a) => console.log(new Date().toISOString().slice(0, 19).replace('T', ' '), ...a);

async function req(url, opts = {}, timeout = 20000) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeout);
  try {
    const res = await fetch(url, { ...opts, signal: ctrl.signal });
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (e) { data = text; }
    return { ok: res.ok, status: res.status, data };
  } catch (e) {
    return { ok: false, status: 0, data: null, error: e.message };
  } finally {
    clearTimeout(timer);
  }
}

function keoss(pathname, opts = {}) {
  return req(CFG.keoss + pathname, {
    ...opts,
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'X-Device-Token': CFG.token,
      ...(opts.headers || {}),
    },
  });
}

function wa(pathname, opts = {}) {
  const headers = { 'Content-Type': 'application/json', ...(opts.headers || {}) };
  if (CFG.waToken) headers['Authorization'] = 'Bearer ' + CFG.waToken;
  return req(CFG.wa + pathname, { ...opts, headers });
}

async function waState() {
  const r = await wa('/status');
  if (!r.ok) return { state: 'worker-offline', qr: null, error: r.error || r.status };
  return { state: (r.data && r.data.state) || 'unknown', qr: (r.data && r.data.qr) || null };
}

async function heartbeat() {
  if (!CFG.token) return;
  await keoss('/api/credit/notificaciones/dispositivo/latido', {
    method: 'POST',
    body: JSON.stringify({ usuario: process.env.EQUIPO_USUARIO || '' }),
  });
}

async function report(id, estado, error) {
  if (CFG.dry) { log(`[dry] resultado #${id} → ${estado} ${error || ''}`); return; }
  const r = await keoss('/api/credit/notificaciones/resultado', {
    method: 'POST',
    body: JSON.stringify({ id, estado, error: error || '' }),
  });
  if (!r.ok) log(`  ! no se pudo reportar #${id}: ${r.status} ${JSON.stringify(r.data)}`);
}

async function tick() {
  const st = await waState();
  if (st.state !== 'open') {
    log(`WhatsApp no conectado (${st.state})${st.qr ? ' · hay QR pendiente en el worker' : ''}; no se toman mensajes`);
    await heartbeat();
    return 0;
  }

  const r = await keoss(`/api/credit/notificaciones/trabajo?limit=${CFG.limit}&canal=${encodeURIComponent(CFG.canal)}`);
  if (!r.ok) {
    log(`No se pudo pedir trabajo: ${r.status} ${r.error || ''}`);
    return 0;
  }

  const jobs = (r.data && r.data.trabajos) || [];
  if (!jobs.length) return 0;

  log(`Trabajos recibidos: ${jobs.length}`);
  let sent = 0;
  for (const job of jobs) {
    const to = String(job.telefono || '').trim();
    const text = String(job.mensaje || '');
    if (!to || !text) {
      await report(job.id, 'fallido', 'sin teléfono o mensaje');
      log(`  #${job.id} fallido: datos incompletos`);
      continue;
    }

    if (CFG.dry) {
      log(`  [dry] #${job.id} → ${to}: ${text.slice(0, 70)}…`);
      continue;
    }

    const send = await wa('/send', { method: 'POST', body: JSON.stringify({ to, text }) }, 45000);
    if (send.ok && send.data && send.data.ok) {
      await report(job.id, 'enviado');
      sent++;
      log(`  #${job.id} enviado a ${to}`);
    } else {
      const err = (send.data && send.data.error) || send.error || ('HTTP ' + send.status);
      await report(job.id, 'fallido', String(err).slice(0, 200));
      log(`  #${job.id} fallido: ${err}`);
    }
  }

  await heartbeat();
  return sent;
}

async function check() {
  const st = await waState();
  log(`Worker WhatsApp: ${st.state}${st.qr ? ' (QR pendiente)' : ''}`);
  if (!CFG.token) {
    log('Falta KEOSS_TOKEN (Crédito → Notificaciones → Equipos → Copiar token)');
    return;
  }
  const lat = await keoss('/api/credit/notificaciones/dispositivo/latido', { method: 'POST', body: '{}' });
  log(`Keoss: ${lat.ok ? 'conectado, pendientes=' + JSON.stringify((lat.data || {}).pendientes) : 'error ' + lat.status}`);
}

(async () => {
  const args = process.argv.slice(2);
  log(`Keoss ${CFG.keoss} → worker ${CFG.wa} · canal ${CFG.canal}${CFG.dry ? ' · DRY RUN' : ''}`);

  if (args.includes('--check')) { await check(); return; }

  if (args.includes('--once')) {
    const n = await tick();
    log(`Listo: ${n} enviados`);
    return;
  }

  let stop = false;
  process.on('SIGINT', () => { stop = true; log('Deteniendo…'); });
  process.on('SIGTERM', () => { stop = true; log('Deteniendo…'); });

  while (!stop) {
    try { await tick(); } catch (e) { log('Error en el ciclo: ' + e.message); }
    const until = Date.now() + CFG.poll * 1000;
    while (!stop && Date.now() < until) await new Promise(r => setTimeout(r, 500));
  }
})();
