# Pipeline de envío masivo de SMS

*(Documentado desde el código en disco, no de memoria)*

## 1. Núcleo: `SmsSender.kt` (service/)

Flujo de un lote completo:

1. **`sendBatch(contacts, template, delayBetweenMessages)`** recorre la lista.
2. Por cada contacto:
   - Normaliza el teléfono con `Importer.normalizePhone`.
   - Rellena la plantilla reemplazando `{nombre}` / `{telefono}`.
   - Guarda una fila en la BD local (*respaldo*) con estado `SENDING`.
   - Envía con `SmsManager` (divide en partes si es >1 SMS, PendingIntent por fila).
   - **Respeta el delay entre mensajes** (nunca 2 a la vez).
3. Acumula `sent`/`failed` y `progress` (StateFlow → barra en la UI).
4. Soporta cancelación (`isCancelled`) y devuelve
   `Result(sent, failed, cancelled, firstRowId, lastRowId)`.

## 2. Estados por fila (`data/SmsStatus.kt`)

- `0 SENDING` → `1 SENT` / `2 FAILED` (vía receiver de `ACTION_SENT`).
- Auditoría exacta: quién salió / quién falló, con `errorCode` + `errorLabel`.

## 3. Soporte de envío

- **`sendSingle`** → bandeja, un mensaje puntual.
- **`resend(phone, message, rowId)`** → reenvía un fallo reutilizando la misma
  fila (limpia la causa previa y vuelve a marcar `SENDING`).
- **`SmsController.kt`** es un singleton con `SmsSender` compartido (app +
  servicio ven el mismo progreso).
- **`failureBreakdown()` / `failuresByLabel()`** → agrupa causas de fallo por
  lote y para el Historial.

## 4. Servicio en primer plano

- `SmsSendService.kt` mantiene el envío en segundo plano (tipo `dataSync`) para
  que continúe aunque se cierre la pantalla.

## 5. Modo de envío (MODO SEGURO / MODO DESATENDIDO)

En **Ajustes → Modo de envío** hay un interruptor:

- **MODO SEGURO** (por defecto): envía por **bloques** de 70-90 mensajes y, al
  enviar el **último** mensaje de cada bloque, arranca un **contador** de 5
  minutos (descanso) antes de seguir con el bloque siguiente. El contador
  **no** arranca con el primer mensaje del bloque.
- **MODO DESATENDIDO**: envía **consecutivo**, sin bloques ni contador; solo
  respeta la pausa entre mensajes configurada.

La elección se guarda en preferencias (`safe_mode`) y se aplica al lote en
`SmsBatchTask.PendingBatch.safeMode` → `SmsSender.sendBatch(..., safeMode)`.

## 6. Dependencias del proyecto

Lo que ya existe y se usa: `SmsManager`/`Telephony`, Room (`smsMessageDao`,
`SmsMessage`, `PurgedSms`, `ThreadResolver`), StateFlow, archivos de importador y
plantillas.

> **WhatsApp fue descartado definitivamente: no forma parte de este pipeline.**
