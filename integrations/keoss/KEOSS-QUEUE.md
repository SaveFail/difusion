# Conector Keoss ↔ worker WhatsApp

`keoss-queue.js` toma la cola de **Crédito → Notificaciones** de Keoss y la envía
por el worker de WhatsApp (`wa-worker/main.js`, Baileys). No requiere la app Android.

## 1. Vincular el equipo en Keoss

Panel → **Crédito → Notificaciones → Equipos → Agregar equipo** → **Copiar** el token.

## 2. Configurar

Crea `integrations/keoss/.env`:

```
KEOSS_URL=https://www.keosspay.com
KEOSS_TOKEN=<token del equipo>
WA_URL=http://127.0.0.1:3001
WA_TOKEN=
CANAL=whatsapp
POLL=15
```

El worker de WhatsApp debe estar corriendo y con la sesión abierta:

```
node wa-worker/main.js  # expone http://127.0.0.1:3001
```

La primera vez hay que escanear el QR (`GET /status` devuelve `qr`) o usar
`POST /pair {phone}` para el código de vinculación.

## 3. Probar

```
node integrations/keoss/keoss-queue.js --check   # estado del worker + pendientes en Keoss
node integrations/keoss/keoss-queue.js --once    # procesa un lote
DRY_RUN=1 node integrations/keoss/keoss-queue.js --once
```

## 4. Dejarlo corriendo

Con systemd, pm2 o `nohup`:

```
nohup node integrations/keoss/keoss-queue.js >> integrations/keoss/keoss-queue.log 2>&1 &
```

## Cómo funciona

1. Comprueba `/status` del worker; si WhatsApp no está `open`, no toma mensajes.
2. `GET /api/credit/notificaciones/trabajo?canal=whatsapp` (Keoss) → trabajos.
3. `POST /send {to, text}` al worker (Baileys).
4. `POST /api/credit/notificaciones/resultado {id, estado}` → la cola queda
   `enviado` o `fallido` con el motivo.
5. Latido cada ciclo para que el panel muestre el equipo conectado.

Los mensajes con canal `sms` o `email` no los toma este conector (`CANAL`).
