# Registro de cambios

## [4.6] — 2026-10-06

### Agregado
- **Botones de fecha en la gestión.** En el diálogo de gestión ahora hay
  **"Fecha actual"** (pone la fecha de hoy) y **"Fecha futura"** (abre el
  selector para elegir fecha y hora futuras). El campo de fecha sigue siendo
  editable a mano.

### Cambiado
- La versión sube a **4.6 (`versionCode 29`)**.

## [4.5] — 2026-10-05

### Agregado / Mejorado
- **Escritura de gestiones a Drive más simple:** basta con **pegar la URL** del
  Web App (`DriveSyncAppsScript.gs`) y queda **activada sola**; nuevo botón
  **"Probar conexión"**. Cada **tipificación (SEGUIMIENTO)**, **STATUS**,
  **MEDIO DE CONTACTO** y **FECHA DE GESTION** se escriben automáticamente en la
  hoja al gestionar, llamar o enviar SMS.
- Sincronización de importación de Drive en **1 toque** (enlace + última hoja,
  opción sin revisión).

### Cambiado
- La versión sube a **4.5 (`versionCode 28`)**.

## [4.4] — 2026-10-05

### Agregado
- **Sincronización de Drive en 1 toque.** Nuevo botón **"Sincronizar rápido
  (1 toque)"** en **Ajustes ▸ Drive**: usa el enlace guardado y la **última
  hoja** que importaste, sin tener que elegir hoja cada vez. Con la casilla
  **"Sincronización rápida"** puedes además **importar sin la ventana de
  revisión**. Se recuerda la última hoja usada automáticamente.
- **Mensajes de voz:** forzar el altavoz a nivel de llamada (Telecom), subir el
  volumen al máximo y desilenciar el micrófono para que el interlocutor oiga el
  audio.

### Cambiado
- La versión sube a **4.4 (`versionCode 27`)**.

## [4.3] — 2026-10-05

### Agregado / Reescrito
- **Mensajes de voz pregrabados (reescritos por completo).** Ahora hay una
  **biblioteca de audios**: puedes **grabar varios**, **importarlos** y elegir
  con un toque cuál usar. El **seleccionado** cumple la lógica pedida:
  **al contestar la llamada se reproduce el audio y, al terminar, la llamada
  se cuelga automáticamente**.
  - Funciona en la **secuencia de llamadas** (marcado masivo) y en la
    **pantalla de llamada (Telecom)**, sin duplicarse.
  - Durante el mensaje se **activa el altavoz** para que el interlocutor lo
    escuche, y se limpia al terminar.
  - La clave de llamada se reinicia al colgar, así cada llamada vuelve a sonar.

### Cambiado
- La versión sube a **4.3 (`versionCode 26`)**.

## [4.2] — 2026-10-05

### Agregado
- **Sección Correo con pestaña "Masivo" como opción principal.** Dentro de
  **Mensaje ▸ Correo** ahora hay dos pestañas: **Masivo** (por defecto) y
  **Bandeja**. La pestaña **Masivo** replica el flujo del envío masivo de SMS:
  **Destinatarios** (Seleccionados / Lista de correos precargados / Individual),
  **Plantillas** (con Asunto), **Asunto**, **Mensaje**, **Programar** fecha y
  hora, y botón de envío con **contador de progreso en segundo plano**.
- **Programar correos** desde el botón **"Correo individual a N"** de Contactos.
- Los servicios de **envío de correos**, **envío de SMS** y **llamadas**
  funcionan de forma **independiente y simultánea** en segundo plano.

### Cambiado
- La versión sube a **4.2 (`versionCode 25`)**.

## [4.1] — 2026-10-05

### Agregado
- **Cliente de correo (Gmail) integrado en la sección Mensaje**, sin Google
  Cloud y sin Play Store, usando **SMTP/IMAP con contraseña de aplicación**.
  - Carpetas **Recibidos**, **Todos** (All Mail), **Enviados**, **Spam** y
    **Papelera**; leer y responder.
  - **Envío individual, correo por correo** (sin CC ni CCO) y **masivo** con
    **correos precargados**.
  - **Envío en segundo plano** con **contador de progreso** y notificación
    (worker en primer plano); botón **"Segundo plano"**.
  - Motor de envío **optimizado**: una sola conexión SMTP para todos los
    correos (mucho más rápido).
  - **Plantillas de correo con Asunto**.
- **Gestión del cliente desde la llamada y el chat** con **desplegables**
  (Seguimiento, Status, Medio) y **fecha de gestión**.
- **Marcado automático**: `MEDIO = LLAMADA` al llamar, `NO CONTESTA` si no
  contesta, `MEDIO = SMS` + `Enviado por SMS` al enviar.
- **Escritura a Google Sheets** de las gestiones vía Apps Script, y respaldo
  con Apps Script para el correo.
- **Exportación** con el formato estándar de Drive (`ID CUOTA, NOMBRES,
  CEDULA, MONTO, Telefono, SEGUIMIENTO, STATUS, EJECUTIVO, MEDIO DE CONTACTO,
  FECHA DE GESTION`) y **fecha de gestión en el nombre del archivo**.
- **Envíos programados** (SMS y correo) con **histórico de destinatarios**.
- Documentación en **`docs/CORREO-GMAIL.md`**.

### Cambiado
- Base de datos a la **versión 15** (correo en contactos, envíos programados,
  asunto de plantillas).
- La versión sube a **4.1 (`versionCode 24`)**.

## [4.0] — 2026-10-02

### Cambiado
- **Rebranding interno completo: el paquete pasa a `com.difusion.app`.** Se
  renombran las carpetas, los `import`, el `namespace`/`applicationId`, el tema
  (`Theme.DifusionApp`), el `InCallService` (`DifusionInCallService`), la base de
  datos (`difusion.db`), las preferencias (`difusion_prefs`) y los canales de
  notificación. Todo el código queda coherente con el nombre **Difusión**.
  ⚠️ Al cambiar el identificador, esta versión se instala como una **app nueva**:
  no actualiza por encima de la versión anterior ni conserva sus datos.
- **Ícono nuevo:** se reemplaza el ícono de la app (que aún mostraba la marca
  anterior) por un diseño representativo de mensajería y llamadas masivas
  (teléfono con ondas de difusión), sobre el azul de marca `#000EAD` con acento
  cian `#02FBFF`.
- Se elimina por completo cualquier referencia a nombres de marca anteriores en
  archivos, documentación e historial del repositorio.

## [3.1] — 2026-10-01

### Cambiado
- **Se eliminaron las últimas claves internas con el nombre anterior** en
  Apariencia: el identificador del tema, la clave de la fuente y el nombre del
  archivo de apariencia exportado. La apariencia guardada **se conserva**: los
  valores antiguos se migran automáticamente a los nuevos al abrir la app.

## [3.0] — 2026-10-01

### Cambiado
- **Nuevo nombre: Difusión.** Se elimina por completo la marca anterior de la
  app, los textos, los avisos, la notificación, los Términos y la documentación.
  La firma **no cambia**, así que la app se actualiza sin perder datos.
- El repositorio pasa a ser **público** (código + APK), con licencia **MIT**.
- El actualizador apunta al nuevo repositorio de releases
  (`SaveFail/difusion-releases`).

### Agregado
- **Donación con Binance Pay:** botón **“Apoyar el proyecto”** en
  **Ajustes → General**, junto a “Acerca de”. Abre un diálogo con el **QR** (sin
  nombre de usuario) y un botón **“Abrir Binance”** que va directo a la billetera
  (con respaldo a la tienda/web) más “Copiar enlace”.
- El README público incluye una **banda grande de donación** y el QR.

## [2.9] — 2026-09-28

### Agregado
- **Conteo en los filtros de la revisión de importación:** cada chip muestra
  ahora **cuántos contactos** tiene esa opción (p. ej. `NO CONTESTA (56)`,
  `Todos (223)`, `Sin gestionar (163)`), tanto en Tipificación como en Estado y
  Medio de contacto. En Mis Contactos ya se mostraba el conteo.

## [2.8] — 2026-09-28

### Corregido / cambiado
- **Sincronización con Drive:** el selector de hoja y la revisión de importación
  ahora son **diálogos dentro de la app** (`SheetPickerDialog` e
  `ImportReviewDialog`) en lugar de ventanas flotantes del sistema. Así **siempre
  aparecen**, sin depender del permiso de superposición (que era lo que hacía que
  “no saliera la ventana”).
- La **revisión conserva el sistema de categorías y filtros** (Tipificación,
  Estado, Medio), casillas por fila, Marcar todo / Nada e Importar (N).
- Al finalizar, el selector de hoja se cierra mientras se descarga y se abre la
  revisión.

## [2.7] — 2026-09-28

### Cambiado (legal)
- **Términos y Condiciones reforzados y de alcance internacional** (versión 2; al
  subir la versión, la app los vuelve a pedir). Ahora incluyen:
  - Naturaleza de la app (herramienta/medio; no presta telecomunicaciones ni
    controla el contenido).
  - Responsabilidad exclusiva del usuario (consentimiento, cumplimiento legal,
    no spam, identificación, opt-out, consentimiento para grabar/mensaje
    automático, datos).
  - **Regiones y normativas aplicables** citadas de forma genérica (protección de
    datos: RGPD/LGPD y equivalentes; anti-spam: CAN-SPAM/TCPA/CASL y similares;
    telecomunicaciones y cobranza) **sin atarlo a un país**.
  - **Sin garantías** (“tal cual”), **limitación de responsabilidad**,
    **indemnización** a favor de los autores y **ley aplicable** con reserva de
    normas imperativas.

## [2.6] — 2026-09-28

### Agregado (legal / responsabilidad)
- **Términos y Condiciones en el primer arranque** (`TermsScreen`): pantalla que
  se muestra la primera vez y exige aceptar para usar la app. Indica que el
  **usuario es el único responsable** del uso (consentimiento, leyes de
  telecomunicaciones/datos/cobranza, no spam), la obligación de **avisar y
  obtener consentimiento para grabar** o usar mensaje automático, y una
  **exención de responsabilidad**: la app se distribuye “tal cual”, como medio o
  facilidad de contacto, **sin garantía de funcionamiento** y sin responsabilidad
  de los autores por daños, sanciones o mal uso. La aceptación se guarda
  (versión 1) y se puede **consultar luego** en Ajustes → General.
- **Aviso de “llamada siendo grabada”** durante la grabación (Ajustes → General →
  ver términos; y en los controles de llamada), recordando avisar al interlocutor.
- **Sugerencia de opt-out** en el editor de mensajes, con botón **“+ Opt-out”**
  que agrega “Responde STOP para no recibir más mensajes”.

## [2.5] — 2026-09-28

### Corregido
- **Envío masivo pedía confirmación por cada SMS** (“permitir/no permitir”) aunque
  la app fuera la predeterminada. Ocurrió desde 2.4 al enviar con un `SmsManager`
  por suscripción. Ahora se usa el **SmsManager predeterminado** salvo que haya
  **2+ SIM** y elijas **explícitamente** una distinta a la suscripción de SMS por
  defecto. Así se recupera el envío masivo normal (sin confirmación por mensaje)
  y el selector de SIM sigue funcionando cuando de verdad se necesita.
- El indicador del botón de SIM muestra ahora la **SIM realmente usada** (la de
  la suscripción de SMS por defecto) cuando no se ha elegido otra.

## [2.4] — 2026-09-28

### Agregado
- **Selector de SIM para SMS**: botón pequeño con forma de SIM en el **envío
  masivo** (editor) y **dentro de cada chat**, para elegir con qué SIM enviar.
  Aparece **aunque solo haya una SIM** (muestra cuál es). La elección se guarda y
  la usan el envío masivo y el chat.
- **Indicador de SIM en llamadas**: el marcador muestra la SIM **aunque solo haya
  una activa** (antes solo aparecía con 2 SIM).
- **Acerca de**: descripción corta y precisa de la app + detalle en un diálogo.
- **Apariencia → Guardar**: botón **“Guardar cambios”** que confirma y deja
  guardada la apariencia en el teléfono.

### Notas
- El envío por SIM usa `SmsManager` de la suscripción elegida; si no está
  disponible, cae a la SIM predeterminada.

## [2.3] — 2026-09-28

### Agregado
- **Ajustes → Permisos → "Abrir permisos del sistema (restringidos)"**: abre la
  pantalla de permisos de la app en Ajustes del sistema, donde se activan los
  **permisos restringidos** (MIUI/HyperOS, "Permitir ajustes restringidos" en
  Android 13+, etc.). Intenta primero los editores de permisos del fabricante
  (MIUI/EMUI/Samsung) y, si no, la ficha de la app.
  - **Nota:** Android **no** permite que la app active esos permisos por código
    (protección del sistema); el botón lleva en un toque al lugar correcto.

## [2.2] — 2026-09-28

### Cambiado (organización de Ajustes)
- **Permisos** ahora incluye también **Mensajes y llamadas predeterminadas**
  (antes en la pestaña Roles, que se eliminó).
- **Nueva pestaña exclusiva “Actualizaciones”.**
- **Drive** se mantiene igual (enlace, sincronización y usuario).
- **Llamadas** ahora incluye **Repetición de llamadas**, los **retardos**
  (pausa, timbre y límite) y el **mensaje automático al contestar** (además de
  Bluetooth, barra flotante y grabación).
- **Envío** queda con el **Modo de envío** y el **retardo entre mensajes**.
- **General** ahora tiene más: **Corregir números**, **Restablecer apariencia** y
  **Acerca de** (versión instalada y de repositorio).

Pestañas finales: **Permisos · Actualizaciones · Drive · Llamadas · Apariencia ·
Envío · General**.

## [2.1] — 2026-09-28

### Corregido
- **Ajustes:** la tarjeta **"Contactos de la lista a llamar"** aparecía en todas
  las pestañas (se dibujaba fuera del contenedor de pestañas porque la columna de
  scroll se cerraba antes de tiempo). Ahora queda **solo en la pestaña Drive**.
- **Ajustes:** el contenido de cada pestaña **vuelve a hacer scroll**
  correctamente (contenedor con altura acotada); antes se desbordaba y no se veía
  el resto.

## [2.0] — 2026-09-28

### Agregado
- **Ajustes por pestañas** para ordenar todo lo que estaba disperso:
  **Permisos, Drive, Roles, Llamadas, Apariencia, Envío y General**. La barra de
  pestañas queda fija arriba; solo el contenido cambia.
- **Personalización ampliada (Apariencia → Vista):**
  - **Color de las burbujas** del chat (enviados y recibidos), con opción
    *Auto* (sigue el color de la app).
  - **Forma de las burbujas**: redondeadas, pastilla o rectas.
  - **Mostrar/ocultar**: estado de envío, hora de cada mensaje y contador de SMS.
  - El texto de las burbujas pasa a negro/blanco según el brillo del color.

### Quitado
- La pestaña **Marca** (nombre/emoji de la app): no cambiaba el nombre ni el
  icono reales del lanzador (Android no lo permite), así que se eliminó para no
  confundir. Se retiró también del modelo de tema.

## [1.9] — 2026-09-28

### Agregado
- **Apariencia por pestañas** (Ajustes → Apariencia): ahora se divide en
  **Tema, Marca, Colores, Texto, Fuente, Sonido, Vista y Guardar** para no
  mezclar tantas opciones y evitar confusiones.
- **Marca (nombre e icono de la app):** nueva pestaña para escribir el **nombre a
  mostrar** y elegir un **logo (emoji)** con vista previa. El nombre y el logo se
  muestran dentro de la app (encabezado de Mensajes) y en la vista previa.
  - **Nota:** Android **no** permite cambiar el nombre ni el icono del
    **lanzador** a valores libres (solo con alias predefinidos); por eso se aplica
    dentro de la app. Queda documentado en la propia pantalla.
- **Colores configurables del estado de los mensajes** (de 1.7) quedan en la
  pestaña *Colores*.

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
- **Repositorio público de descargas:** `SaveFail/difusion-releases` (solo el
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
