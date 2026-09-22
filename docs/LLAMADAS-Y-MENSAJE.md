# Llamadas masivas y mensaje pregrabado

## 1. Secuencia de llamadas masivas

La pestaña **Llamadas** (o *Llamar seleccionados* desde Contactos) inicia
`CallSequencer`:

1. Toma los contactos seleccionados y marca uno por uno.
2. Espera **retardo entre llamadas** (Ajustes).
3. Timbrado con **duración máxima** configurable; si nadie contesta, cuelga y
   pasa al siguiente.
4. Si contestan, arranca el **tope de duración** (0 = sin límite) y la
   **grabación** (si está activada).
5. Al colgar, registra el resultado y continúa.
6. Al terminar, puede **repetir** la cola (*por cola* o *por usuario*).

La app debe ser **teléfono predeterminado** para que Telecom le entregue el
estado real de la llamada (`InCallService`).

## 2. Riel de controles de llamada

`CallControls.kt` / `CallRail` ofrece, durante la llamada:

- **Silenciar** micrófono.
- **Altavoz** (`toggleSpeaker`) — *único* botón que activa el altavoz.
- **Espera** (hold).
- **Teclado** (DTMF).
- **Bluetooth** (si hay dispositivo conectado).
- **Colgar**.

En modo secuencia masiva el riel se muestra sobre la lista para no perder el
listado.

## 3. Grabación de llamadas

`CallRecorder` intenta primero la fuente `VOICE_COMMUNICATION` (captura directa
de la llamada). Si el fabricante la bloquea, cae a `MIC`. Al guardar, **verifica
el archivo** con `MediaMetadataRetriever`; si no es reproducible (duración
nula), lo descarta y lo informa. Las grabaciones quedan en la pestaña
**Grabaciones**.

## 4. Mensaje pregrabado automático

- Se graba o importa en **Ajustes → Mensaje** (`VoiceMessageStore`).
- Se reproduce al **contestar** la llamada, desde `MassTextInCallService`
  (`STATE_ACTIVE`), con `CallMessagePlayer`.
- El audio usa `USAGE_VOICE_COMMUNICATION`, por lo que sale por la **ruta real
  de la llamada**: auricular, **audífonos con cable/USB** o Bluetooth. El
  **altavoz solo se activa al pulsar su botón**.
- Al terminar el audio, la llamada se **corta automáticamente**.

### Limitación real de Android (léela)

En una **llamada celular normal (GSM)**:

1. Android **bloquea a las apps normales** escribir en el **canal de subida**
   (uplink) de la llamada; el audio que reproduce la app no llega al
   interlocutor por sí solo.
2. El **cancelador de eco (AEC)** del teléfono elimina del micrófono todo lo que
   reproduce el altavoz (lo interpreta como eco).

Consecuencia: el mensaje se escucha **en tu equipo** (y por la ruta que elijas),
pero **no de forma garantizada en el teléfono del cliente**.

Para que el cliente **sí** oiga un mensaje grabado de forma fiable, las vías
reales son **VoIP/SIP** (el servidor/proveedor reproduce el audio dentro de la
llamada) o **root** (inyección directa al uplink). Ambas quedan **fuera del
alcance** de esta app, que funciona con la llamada celular del propio teléfono.

> No se recomienda root: no elimina el AEC (vive en el módem/HAL de audio),
> rompe la seguridad del equipo y no es replicable en otros teléfonos.

## 5. Detección de dispositivos de audio

`CallMonitor.hasWiredHeadset()` y `CallMonitor.hasExternalAudio()` detectan
audífonos con cable/USB o Bluetooth para que la app **nunca** active el altavoz
solo cuando hay un dispositivo externo conectado.
