# Arquitectura

Aplicación Android en **Kotlin + Jetpack Compose**, con **Room** para
persistencia y **servicios en primer plano** para trabajos largos (envío masivo
de SMS, grabación de llamadas).

## Capas

```
UI (Compose)          ui/
   │  observa StateFlow
ViewModel             ui/MainViewModel.kt
   │  llama a
Datos (Room)          data/  (AppDatabase, DAOs, entidades)
   │
Servicios             service/  (SMS, llamadas, grabación, audio)
   │
Rol del sistema       smsrole/ (SMS/MMS) + InCallService (telecom)
```

## Navegación

`MainActivity.kt` monta un `NavHost` con las pestañas inferiores:

| Ruta | Pantalla |
|---|---|
| `inbox` | Bandeja de conversaciones (`InboxScreen`) |
| `calls` | Llamadas / secuencia masiva (`CallsSection`) |
| `contacts` | Contactos e importación (`ContactsScreen`) |
| `editor` | Editor de mensajes y plantillas (`MessageEditorScreen`) |
| `history` | Historial de envíos y llamadas (`HistoryScreen`) |
| `recordings` | Grabaciones (`RecordingsTab`) |
| `settings` | Ajustes (`SettingsScreen`) |

Pantallas modales: Apariencia, Usuarios, Papelera, conversación, previsualización
de importación, progreso de envío y centro de llamadas.

## Persistencia (Room)

`data/AppDatabase.kt` define las entidades y DAOs:

- `Contact` / `ContactDao` — contactos, su `assignment` (usuario) y las
  categorías `gestion` (tipificación), `estado` y `medio` (de la hoja de Drive).
- `SmsMessage` / `SmsMessageDao` — mensajes locales.
- `SendRecord` / `SendDao` — registros de envío (auditoría).
- `CallRecord` / `CallDao` — registros de llamada y etiquetas.
- `MessageTemplate` / `TemplateDao` — plantillas.
- `PurgedSms` — SMS purgados.
- `ThreadResolver` — resuelve hilos de conversación.

Almacenamiento auxiliar: `storage/UserStore.kt` (usuarios) y
`storage/RecordStore.kt` (respaldo a carpeta elegida).

## Servicios

- **`SmsSendService`** — envío masivo en primer plano (`dataSync`).
- **`SmsController`** — singleton compartido entre UI y servicio.
- **`CallSequencer`** — orquesta la secuencia de llamadas masivas (timbrado,
  tope, repetición).
- **`MassTextInCallService`** — `InCallService` de Telecom: recibe la llamada,
  muestra overlay, controla ruta de audio y dispara la grabación y el mensaje.
- **`CallRecordingService`** — grabación en primer plano (`microphone`).
- **`CallMonitor`** — estado de la llamada y control de ruta (`setAudioRoute`).
- **`CallRecorder`** — MediaRecorder, verificación del archivo y respaldo.
- **`VoiceMessageStore` / `CallMessagePlayer`** — mensaje pregrabado.
- **`SheetPickerOverlay`** — ventana flotante para elegir hoja de Google Sheets.
- **`ImportPreviewOverlay`** — ventana flotante de revisión antes de importar:
  filtros por categoría (tipificación, estado, medio) y filas con casillas.

## Importación

`import/Importer.kt` lee **Excel y CSV en streaming** (`workbook.xml` +
`worksheets/*.xml` con `XmlPullParser`) para no cargar todo el archivo en
memoria (evita `OutOfMemoryError` con hojas grandes). Normaliza teléfonos y
deduplica.

Las columnas de categoría se detectan por su encabezado:
`SEGUIMIENTO`/`TIPIFICACIÓN` → `gestion`, `STATUS`/`ESTADO` → `estado`,
`MEDIO DE CONTACTO` → `medio`; los encabezados de **fecha** se excluyen para no
confundir "FECHA DE GESTIÓN" (fecha serial de Excel) con una categoría. Al
sincronizar, `MainViewModel` filtra por usuario, deduplica por cédula y publica
las filas en `drivePreviewRows`; la confirmación (`commitDriveImport`) reemplaza
la lista de contactos con las filas marcadas.

## Exportación

`report/XlsxWriter.kt` genera el `.xlsx` (ZIP + XML OOXML con celdas `inlineStr`)
**sin Apache POI**. `report/ReportExporter.kt` arma las filas de contactos,
plantilla e historial. Al no incluir POI, el APK y el uso de memoria bajan mucho
(importación y exportación en streaming).

## Permisos y roles

`MainActivity` centraliza:
- **Todos los permisos** de runtime en una sola petición
  (`RequestMultiplePermissions`).
- **Permisos especiales** encadenados: superposición
  (`ACTION_MANAGE_OVERLAY_PERMISSION`) y pantalla completa
  (`ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`, Android 14+).
- **Roles predeterminados** encadenados (SMS y Teléfono) vía `RoleManager`.
- **Escáner QR** del enlace de Drive con Google Code Scanner
  (`GmsBarcodeScanning`, sin permiso de cámara).

## Actualizaciones

`service/UpdateManager.kt` consulta la **última Release** del repositorio público
de descargas (`SaveFail/difusion-releases`) mediante la **API de GitHub** (sin
caché), compara la versión con `BuildConfig.VERSION_NAME` y, si es más nueva,
descarga el APK a la caché y lo instala con `FileProvider` +
`Intent.ACTION_VIEW` (`REQUEST_INSTALL_PACKAGES`). El usuario confirma la
instalación; el código del proyecto permanece privado.

## Roles del sistema

- **SMS predeterminado** (`smsrole/`): `SmsReceiver`, `MmsReceiver`,
  `HeadlessSmsSendService`, `SmsSentReceiver`.
- **Teléfono predeterminado**: `MassTextInCallService` (declarado en el
  manifiesto con `BIND_INCALL_SERVICE`).

## Concurrencia

Se usa `StateFlow` para el estado observable y corrutinas/`Executors` para el
trabajo pesado, con el objetivo de **no bloquear el hilo principal** durante
llamadas o envíos (causa histórica de ANR).
