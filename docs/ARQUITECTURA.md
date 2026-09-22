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

- `Contact` / `ContactDao` — contactos y su `assignment` (usuario).
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

## Importación

`import/Importer.kt` lee **Excel y CSV en streaming** (`workbook.xml` +
`worksheets/*.xml` con `XmlPullParser`) para no cargar todo el archivo en
memoria (evita `OutOfMemoryError` con hojas grandes). Normaliza teléfonos y
deduplica.

## Roles del sistema

- **SMS predeterminado** (`smsrole/`): `SmsReceiver`, `MmsReceiver`,
  `HeadlessSmsSendService`, `SmsSentReceiver`.
- **Teléfono predeterminado**: `MassTextInCallService` (declarado en el
  manifiesto con `BIND_INCALL_SERVICE`).

## Concurrencia

Se usa `StateFlow` para el estado observable y corrutinas/`Executors` para el
trabajo pesado, con el objetivo de **no bloquear el hilo principal** durante
llamadas o envíos (causa histórica de ANR).
