# Registro de cambios

## [1.8] — 2026-09-28

### Agregado / corregido
- **Llamadas válidas no se repiten.** Si una llamada fue **válida** (el cliente
  contestó), no se vuelve a marcar: la secuencia **salta automáticamente** a la
  siguiente llamada programada. Aplica a los dos modos:
  - **Por usuario:** tras una llamada válida se pasa al siguiente contacto sin
    repetir las veces restantes.
  - **Por cola:** en las rondas siguientes se **omiten** los contactos que ya
    tuvieron una llamada válida; la secuencia termina cuando no quedan pendientes.
- **Editor de mensajes:** el **nombre de la plantilla ya no queda oculto por el
  teclado**; la pantalla se ajusta (imePadding) y el campo se desplaza a la vista
  al enfocarlo.

## [1.7] — 2026-09-28

### Cambiado
- **Editor de mensajes:** el texto de ayuda de `{nombre}` queda **justo debajo
  de las plantillas y encima del cuadro del mensaje** (ya no debajo del cuadro).
- **Chat:** cada mensaje saliente muestra una **etiqueta de estado visible** en
  la esquina inferior derecha de la burbuja: **Enviado** (check), **Enviando**
  (reloj) o **No enviado** (X), con su propio color de fondo para que siempre se
  vea, aunque se envíen más mensajes.
- **Apariencia:** nuevos **colores configurables** para el estado de los
  mensajes (Enviado / Enviando / No enviado). El texto se ajusta a negro o
  blanco según el brillo del color elegido para que nunca se pierda.

## [1.6] — 2026-09-28

### Mejorado
- **Editor de mensajes:** el texto “Usa `{nombre}` para personalizar…” ahora
  aparece en la **sección del mensaje** (debajo del campo de texto) y ya no justo
  encima del nombre de la plantilla, para evitar confusión. La sección de
  plantillas queda arriba, con su ayuda justo debajo de las plantillas creadas.
- **Variables en el chat:** al enviar un mensaje desde un chat también se
  reemplazan `{nombre}` y `{telefono}` (antes solo en el envío masivo).

### Notas
- El **check de estado dentro de cada chat** (enviando/enviado/no enviado) se
  incluyó en la 1.5; esta versión lo conserva.

## [1.5] — 2026-09-28

### Corregido / mejorado
- **Estado de los mensajes:** las burbujas del chat (salientes) vuelven a
  mostrar el estado: **reloj** (enviando), **check** (enviado) o **X** (no
  enviado), junto a la hora. En la bandeja también se distingue “enviando” de un
  mensaje entrante (antes ambos se veían igual porque el estado 0 era ambiguo).
- **Botón de roles:** en *Mensajes y llamadas predeterminadas* el botón ahora es
  **siempre** “Hacer predeterminadas Mensajes y Llamadas” (antes cambiaba a
  “Abrir centro de llamadas” cuando ya lo eran).
- **Actualizaciones:** la tarjeta muestra la **versión instalada** y la
  **última versión del repositorio** (leída en vivo, no un texto fijo). El
  mensaje de descarga indica de qué versión a cuál se actualiza.

## [1.4] — 2026-09-28

### Agregado
- **Buscar actualizaciones dentro de la app** (Ajustes → *Actualizaciones*).
  Consulta la **última Release** de un repositorio público de descargas vía la
  **API de GitHub** (sin caché), compara versiones y, si hay una nueva,
  **descarga el APK** y abre el instalador del sistema: el usuario solo confirma
  con un toque.
  - Permiso `REQUEST_INSTALL_PACKAGES` y `FileProvider` para instalar.
  - Si falta “Instalar apps desconocidas”, abre los ajustes y reintenta al volver.
  - `service/UpdateManager.kt` centraliza la comprobación, descarga e instalación.
- **Repositorio público de descargas:** `SaveFail/lex-recover-releases` (solo el
  APK). El **código permanece privado**.

### Notas
- La actualización **no es silenciosa**: Android siempre pide confirmar la
  instalación (salvo Google Play o *Device Owner*). El APK debe estar firmado con
  la **misma clave** que la versión instalada.
- Se publica la **1.4** en el repositorio de descargas para que los equipos en
  1.3 puedan actualizarse.

## [1.3] — 2026-09-28

### Rendimiento y tamaño (equipos de bajos recursos)
- **APK de release: 20.8 MB → ~2.9 MB.** Se activó **R8** (`isMinifyEnabled` +
  `isShrinkResources`) con reglas ProGuard, y se quitaron dependencias no usadas
  (`opencsv`, `commons-csv`, `zxing`, `ui-tooling`).
- **Se eliminó Apache POI** (el mayor peso del APK):
  - Importar `.xlsx` locales usa ahora el **mismo lector en streaming** que Google
    Drive (copia a temporal y lee la primera hoja).
  - Nuevo **escritor XLSX propio** (`report/XlsxWriter.kt`): exportación de
    contactos, plantilla e historial sin POI.
  - **Nota:** ya no se leen archivos `.xls` antiguos (solo `.xlsx` y `.csv`).
- Se quitó `android:largeHeap="true"` (menos presión de memoria).
- **Bandeja SMS/MMS:** antes hacía **2 consultas a la base por cada mensaje** en
  cada arranque; ahora lee los ids existentes/purgados **una sola vez** e inserta
  en lote (mejora grande en equipos lentos).
- **Asignación de usuarios:** una sola sentencia SQL (antes cargaba todos los
  contactos y actualizaba uno por uno).
- **Mis Contactos:** los conteos de categorías y la lista de usuarios se calculan
  con `remember`; la ventana de revisión dibuja como máximo 300 filas.

### Permisos y roles (agiliza la configuración inicial)
- Nuevo botón **“Dar todos los permisos”**: pide de una vez los permisos de
  SMS, llamadas, estado del teléfono, micrófono y notificaciones.
- Nuevo botón **“Permisos especiales”**: encadena superposición (mostrar sobre
  otras apps) y pantalla completa para llamadas.
- El botón de “app de llamadas predeterminada” se transformó en **“Hacer
  predeterminadas Mensajes y Llamadas”**: solicita ambos roles seguidos.
- **Aclaración:** Android **no** permite un permiso especial para otorgar
  permisos; cada permiso lo concede el usuario (diálogo del sistema o pantalla de
  Ajustes). Solo un *Device Owner* (empresarial, por ADB/QR) podría aplicarlos por
  política.

### Plantillas de mensaje
- Al **seleccionar una plantilla existente** ya **no pide guardarla**: se resalta,
  se puede **enviar directamente** y el botón indica “ya guardada”. Solo si se
  **edita el texto** ofrece **“Guardar cambios”**, que **actualiza** la plantilla
  en lugar de duplicarla (`TemplateDao.getByName`/`updateBody`, upsert).

### Escáner QR para el enlace de Drive
- Botón de **cámara** a la derecha del campo del enlace: abre el **escáner QR**
  (Google Code Scanner de Play Services, **sin permiso de cámara**), lee el enlace
  del Drive compartido y **sincroniza de una vez**. Requiere Servicios de Google
  Play; en equipos sin GMS se pega el enlace a mano.

### Pruebas
- **Dispositivo (TECNO KM4k):** release de ~2.9 MB instalado y funcionando;
  migraciones Room, bandeja, importación/exportación con el escritor XLSX propio
  (prueba unitaria del formato incluida), botones de permisos y roles, selección
  de plantillas sin re-guardar y apertura del escáner QR verificados.

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
