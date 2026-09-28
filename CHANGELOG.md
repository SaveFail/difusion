# Registro de cambios

## [1.2] — 2026-09-28

### Agregado
- **Categorías de contacto desde Google Drive.** La hoja puede traer columnas
  de categoría y cada contacto guarda tres valores (Room `version = 11`):
  - `gestion` ← **SEGUIMIENTO** o **TIPIFICACIÓN** (ej. NO CONTESTA, VOLVER A LLAMAR).
  - `estado` ← **STATUS** o **ESTADO** (ej. PROMESA DE PAGO, PAGO).
  - `medio` ← **MEDIO DE CONTACTO** (ej. LLAMADA, WHATSAPP).
- **Filtros por categoría en Mis Contactos.** Una fila de chips por dimensión
  (Tipificación, Estado y Medio de contacto) con los **nombres reales** de la
  hoja y su contador; los vacíos aparecen como *Sin gestionar*, *Sin estado* y
  *Sin medio*. Los tres filtros se combinan (Y) y el botón **Todos** selecciona
  solo los contactos visibles. Botón **Exportar filtrados**.
- **Ventana flotante de revisión al sincronizar** (`ImportPreviewOverlay`). Tras
  elegir la hoja se abre una ventana `TYPE_APPLICATION_OVERLAY` con los filtros
  de cada categoría y las filas con **todas las columnas** (Nombre, Cédula,
  Teléfono, Asignado a) y una **casilla por fila** (todas marcadas por defecto),
  con **Marcar todo / Nada** e **Importar (N)**. Solo se importan las filas
  marcadas y reemplazan la lista.
- **Exportación:** el Excel de contactos y la plantilla incluyen las columnas
  **Tipificación**, **Estado** y **Medio**.
- Los **filtros se ocultan** mientras hay contactos seleccionados, para dejar
  espacio visual; al limpiar la selección vuelven a mostrarse.

### Corregido
- **Categorías mostraban números en vez de nombres.** La sincronización leía
  como "Gestión" la columna **FECHA DE GESTIÓN**, que en el XLSX son fechas
  guardadas como **número de serie de Excel** (46252, 46279…). Ahora las
  columnas de categoría se detectan **por su nombre** y se **excluyen** los
  encabezados de fecha (`fecha`, `feccha`, `vencimiento`).

### Pruebas
- **Dispositivo (TECNO KM4k) con hoja real (pestaña `SEP 19-09`):** migración
  Room 10→11 correcta; la ventana de revisión mostró tipificaciones
  (NO CONTESTA, VOLVER A LLAMAR, PAGO EN SOPORTE), estados (ABONO, INCUMPLIDA,
  PAGO, PROMESA DE PAGO) y medios (LLAMADA, SMS, WHATSAPP); se importaron 223
  contactos de 9 605 filas (261 duplicados por cédula). Los filtros de Mis
  Contactos muestran los nombres y se ocultan al seleccionar.

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
