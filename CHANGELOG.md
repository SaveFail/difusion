# Registro de cambios

## [1.1] — 2026-09-24

### Corregido
- **Pantalla de llamada al recibir llamadas:** se corrigió el fallo por el cual,
  al recibir una llamada, **no se podía contestar**. La causa era que se pasaba
  una constante de **ruta de audio** (`ROUTE_WIRED_OR_EARPIECE`) en el parámetro
  que `Call.answer()` espera como **estado de video**. Ahora se usa
  `VideoProfile.STATE_AUDIO_ONLY` (`CallMonitor.answerIncomingCall`).
- **Contador del envío masivo:** el contador del bloque ahora arranca al enviar
  el **último** mensaje del bloque, no con el primero.
- **Marcador:** se eliminó el "mini panel" de llamada de la sección de marcado
  (era inútil: no permitía colgar ni usar funciones); ahora siempre muestra el
  teclado normal.

### Agregado
- **Pantalla única de llamada (`CallActivity`):** se sobrepone sobre el bloqueo
  de pantalla y sobre cualquier app, para llamadas **entrantes** y
  **salientes/en curso**. Incluye Contestar / Buzón / Rechazar y, en llamada,
  colgar, silenciar, altavoz, espera, Bluetooth y teclado DTMF. Se abre
  **automáticamente** al recibir y al marcar.
- **Modos de control interno** (Ajustes → **Modo de envío**): **MODO SEGURO**
  (bloques de 70-90 mensajes con descanso de 5 minutos y contador) y **MODO
  DESATENDIDO** (envío consecutivo, sin bloques ni contador).
- La barra flotante de llamada se **oculta** dentro de la pantalla de llamada y
  vuelve al minimizar; la notificación abre la pantalla de llamada.

### Pruebas
- **Prueba realizada en dispositivo** (Huawei ELA-LX3) tras instalar el APK:
  se verificó la apertura automática de la pantalla de llamada al recibir y al
  marcar, y la **corrección del fallo que impedía contestar** las llamadas
  entrantes.

## [1.0] — Versión inicial

- Envío masivo de SMS con servicio en primer plano y auditoría por fila.
- Secuencia de llamadas masivas con grabación y mensaje pregrabado.
- Importación de Excel/CSV/Google Sheets en streaming.
- Contactos con asignación a usuarios, historial, respaldo y apariencia.
- App de SMS y teléfono predeterminada (`InCallService`).
