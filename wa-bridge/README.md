# Difusión · Puente de WhatsApp (open source, sin API de Meta)

Worker en **Node.js + Baileys** que vincula un número de WhatsApp y expone una
**API HTTP** para que la app Difusión envíe mensajes y ejecute el **bot** de
auto-respuesta.

> ⚠️ Baileys usa el protocolo de WhatsApp Web (no oficial). Úsalo con tu propia
> cuenta y respetando los términos de WhatsApp. No es la API oficial de Meta.

## Requisitos

- PC o servidor con **Node.js 18+** (recomendado 20).
- Mismo WiFi que el teléfono (o IP pública/DDNS si es un servidor).

## Instalar y ejecutar

```bash
cd wa-bridge
npm install
WA_HTTP_PORT=3001 WA_TOKEN=mi-token-secreto node main.js
```

- `WA_HTTP_PORT` (por defecto `3001`)
- `WA_HTTP_HOST` (por defecto `0.0.0.0`)
- `WA_TOKEN` (recomendado; la app lo envía en `x-token`)

Las credenciales de la sesión quedan en `wa-auth/` (en el directorio actual).
Si borras esa carpeta, hay que vincular de nuevo.

## Vincular el número (una vez)

1. Con el worker corriendo, en la app: **Ajustes ▸ Bot**.
2. Pon la **URL** del worker, p. ej. `http://192.168.1.10:3001` (la IP de la PC).
3. Pon el **Token** (el mismo `WA_TOKEN`).
4. Escribe tu **número** (con código de país, p. ej. `584241234567`) y toca
   **Vincular**.
5. La app te mostrará un **código de 8 dígitos**. En el teléfono:
   **WhatsApp ▸ Dispositivos vinculados ▸ Vincular dispositivo ▸ Vincular con
   número de teléfono** e ingresa el código.
6. Cuando el estado sea `open`, la app ya puede enviar.

> También se puede escanear un QR: `GET /status` devuelve `qr` (texto) si está
> disponible; puedes renderizarlo con cualquier librería de QR.

## API HTTP

Todas las rutas aceptan el token por cabecera `x-token` o `?token=`.

| Método | Ruta | Cuerpo | Descripción |
|---|---|---|---|
| GET | `/` | — | Comprobación (`ok`) |
| GET | `/status` | — | `{state, qr, pairing}` |
| POST | `/pair` | `{phone}` | Solicita código de vinculación |
| POST | `/send` | `{to, text}` | Envía un WhatsApp (`to` = teléfono o JID) |
| GET | `/messages?since=<ms>` | — | Mensajes entrantes recientes |
| GET | `/bot` | — | `{enabled, rules}` |
| POST | `/bot` | `{enabled, rules}` | Configura el bot |

### Reglas del bot (con varias acciones)

```json
{
  "enabled": true,
  "rules": [
    {
      "keyword": "pago",
      "match": "contains",
      "caseSensitive": false,
      "stop": true,
      "actions": [
        { "type": "reply", "text": "Hola {nombre}, ¿cuál es tu cédula?" },
        { "type": "wait",  "seconds": 5 },
        { "type": "reply", "text": "Gracias. Un asesor te atenderá." },
        { "type": "http",  "url": "https://tu-sistema/api/wa", "method": "POST" }
      ]
    }
  ]
}
```

- `match`: `contains` (por defecto), `equals` o `startsWith`.
- `caseSensitive`: distingue mayúsculas/minúsculas.
- `stop`: si es `true`, deja de evaluar reglas tras ejecutar la secuencia.
- Acciones:
  - **`reply`** → envía un texto (admite `{nombre}` y `{telefono}`).
  - **`wait`** → espera N segundos (1–3600) antes de la siguiente acción.
  - **`http`** → llama una URL (POST con `{jid, phone, name}` o GET).
- El formato antiguo `{ "keyword": "...", "reply": "..." }` sigue funcionando.

La app **sincroniza** estas reglas desde **Ajustes ▸ Bot**, así que normalmente
no hace falta llamar esta API a mano.

## Mensajes programados

La app programa los envíos (varios, sin límite) en su base de datos y, cuando
llega la hora, llama a `POST /send` por cada destinatario. El worker debe estar
**corriendo y vinculado** en ese momento.

## Notas

- Si el worker se cae, la sesión se reconecta sola (excepto si se cierra sesión
  desde el teléfono).
- Evita enviar demasiado rápido: WhatsApp puede limitar o bloquear la cuenta.
- Para producción, protege el puerto con `WA_TOKEN` y firewall/HTTPS.
