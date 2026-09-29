# LEX RECOVER — Aplicación de Masivos

Aplicación Android (Kotlin + Jetpack Compose) para **gestión y envío masivo de
SMS**, **campañas de llamadas masivas** con grabación, e importación de
contactos desde Excel/CSV/Google Sheets. Funciona como app de **SMS** y de
**teléfono (marcador) predeterminada**, de modo que puede mostrar su propia
pantalla de llamada y controlar el audio durante la llamada.

- **Paquete:** `com.masstext.app`
- **Nombre visible:** LEX RECOVER
- **Versión:** 2.1 (`versionCode 12`)
- **minSdk:** 26 (Android 8) · **targetSdk/compileSdk:** 35 (Android 15)

---

## Tabla de contenido

1. [Características](#características)
2. [Requisitos](#requisitos)
3. [Compilar e instalar](#compilar-e-instalar)
4. [Firma del APK de release](#firma-del-apk-de-release)
5. [Primer arranque: permisos y roles](#primer-arranque-permisos-y-roles)
6. [Uso rápido](#uso-rápido)
7. [Estructura del proyecto](#estructura-del-proyecto)
8. [Documentación adicional](#documentación-adicional)
9. [Nota técnica: mensaje pregrabado en llamadas](#nota-técnica-mensaje-pregrabado-en-llamadas)
10. [Seguridad](#seguridad)

---

## Características

### Mensajería (SMS / MMS)
- Bandeja con conversaciones por hilo, no leídos y búsqueda.
- Envío masivo a los contactos seleccionados con **retardo configurable** entre
  mensajes, barra de progreso y servicio en primer plano (sigue enviando aunque
  cierres la pantalla).
- **Modo de envío** (Ajustes): **MODO SEGURO** usa bloques de 70-90 mensajes con
  descanso de 5 minutos (contador que arranca al enviar el **último** mensaje del
  bloque); **MODO DESATENDIDO** envía consecutivo sin bloques ni contador.
- Estados por mensaje (`ENVIANDO` → `ENVIADO` / `FALLIDO`) con código y motivo
  del fallo; reenvío de fallidos desde la bandeja o el historial.
- Plantillas de mensaje con variables `{nombre}` y `{telefono}`. Al **elegir una
  plantilla existente** no hay que volver a guardarla: se puede enviar directo y
  solo se pide guardar si se **edita** el texto (actualiza, no duplica).
- Papelera: mover, restaurar y vaciar conversaciones.

### Llamadas
- Sección de llamadas con **secuencia masiva**: marca uno a uno con retardo
  entre llamadas, **duración de timbrado** y **tope de duración** por llamada.
- Repetición de la cola **por cola** o **por usuario**. Las **llamadas válidas
  (contestadas) no se repiten**: se saltan automáticamente al avanzar (en ambos
  modos).
- Marcador propio, historial de llamadas y etiquetas.
- **Grabación de llamadas** (fuente `VOICE_COMMUNICATION`; si el equipo la
  bloquea, cae a micrófono) con verificación del archivo para no guardar audios
  corruptos.
- **Mensaje pregrabado automático** al contestar la llamada (ver
  [nota técnica](#nota-técnica-mensaje-pregrabado-en-llamadas)).
- **Pantalla única de llamada** (`CallActivity`) que se sobrepone sobre el
  bloqueo de pantalla y sobre cualquier app, entrante o saliente/en curso:
  Contestar / Buzón / Rechazar y, en llamada, colgar, silenciar, altavoz,
  espera, Bluetooth y teclado DTMF. Se abre sola al recibir o al marcar.
- El altavoz **solo** se activa al pulsar su botón. Al minimizar la pantalla
  queda la barra flotante para volver; dentro de la pantalla no se muestra.

### Contactos
- Alta manual y por importación de **Excel (.xlsx), CSV** y **Google Sheets**
  (enlace publicado como CSV/hoja).
- Asignación de contactos a **usuarios**, exportación por usuario y global.
- Reparación automática de números a los que les falta el `0` inicial.

### Sincronización desde Google Drive y categorías
- Se pega el enlace de la hoja (compartida como *Lector*); la app enumera sus
  pestañas y se elige cuál importar.
- También se puede **escanear un código QR**: botón de **cámara** en el campo del
  enlace (Google Code Scanner, sin permiso de cámara) que lee el enlace del Drive
  y **sincroniza de una vez**.
- Se importan solo los contactos cuyo **"Asignado a"** coincide con tu usuario
  (o todos si la hoja no tiene esa columna), sin duplicados por cédula.
- **Categorías por columna:** `SEGUIMIENTO`/`TIPIFICACIÓN` (tipificación),
  `STATUS`/`ESTADO` (estado) y `MEDIO DE CONTACTO` (medio). Se detectan por el
  nombre del encabezado y se ignoran las columnas de fecha.
- **Ventana flotante de revisión** antes de reemplazar la lista: filtros por
  categoría y todas las filas con sus columnas y casillas (todas marcadas); se
  elige qué importar.
- En **Mis Contactos**, tres filtros (Tipificación, Estado, Medio) con los
  nombres reales y su contador; los vacíos salen como *Sin gestionar*,
  *Sin estado* y *Sin medio*. Los filtros se combinan y **Todos** selecciona
  solo lo visible. Se ocultan al marcar contactos para dejar espacio.

### Actualizaciones
- **Ajustes → Actualizaciones → “Buscar actualizaciones”**: consulta la última
  Release del repositorio público
  [`SaveFail/lex-recover-releases`](https://github.com/SaveFail/lex-recover-releases/releases),
  descarga el APK nuevo y abre el instalador (solo confirmas). El **código sigue
  privado**; únicamente el APK se publica. Requiere la misma clave de firma.

### Historial, respaldo y apariencia
- Historial de envíos y llamadas con **exportación a Excel**.
- **Respaldo** a una carpeta elegida por el usuario.
- **Apariencia** configurable por **pestañas** (Tema, Colores, Texto, Fuente,
  Sonido, Vista, Guardar): color de acento, fondo, modo oscuro, fuente
  (incluida `.ttf/.otf`), escala de texto e iconos, forma, espaciado, colores del
  estado de los mensajes, **colores y forma de las burbujas del chat** y
  **mostrar/ocultar** estado, hora y contador de SMS. Exportar/importar.
- **Ajustes por pestañas** (Permisos, Drive, Roles, Llamadas, Apariencia, Envío,
  General) para tener la configuración ordenada.

---

## Requisitos

- **Android Studio** (recomendado) o **JDK 17** + Android SDK.
- **JDK 17** (el proyecto usa `sourceCompatibility = 17`).
- **Android SDK** con `compileSdk 35` y las *build-tools* correspondientes.
- Un teléfono Android 8.0+ con **telefonía** (para SMS y llamadas).

---

## Compilar e instalar

```bash
# APK de depuración
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk

# Instalar en un dispositivo conectado por USB (depuración USB activada)
./gradlew installDebug
# o
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Si Gradle no encuentra el SDK, crea `local.properties` en la raíz:

```properties
sdk.dir=/ruta/a/tu/Android/Sdk
```

> `local.properties` está en `.gitignore` (es específico de cada máquina).

---

## Firma del APK de release

La firma se lee de `keystore.properties` (en la raíz). **Ese archivo y el
`.jks` no se suben al repositorio** (están en `.gitignore`).

1. Copia la plantilla y rellena tus valores:

   ```bash
   cp keystore.properties.example keystore.properties
   ```

2. Genera tu keystore si no tienes uno:

   ```bash
   keytool -genkeypair -v -keystore masstext-release.jks \
     -alias masstext -keyalg RSA -keysize 2048 -validity 10000
   ```

3. Compila:

   ```bash
   ./gradlew assembleRelease
   # -> app/build/outputs/apk/release/app-release.apk
   ```

Si `keystore.properties` no existe, el APK de release se genera **sin firmar**.

---

## Primer arranque: permisos y roles

En **Ajustes** hay botones para dejarlo listo rápido:

1. **Dar todos los permisos:** pide de una vez SMS, llamadas, estado del
   teléfono, micrófono y notificaciones (Android 13+).
2. **Permisos especiales:** encadena **superposición** (mostrar sobre otras apps)
   y **pantalla completa** para las llamadas (Android 14+).
3. **Hacer predeterminadas Mensajes y Llamadas:** solicita los dos roles seguidos
   (app de SMS y app de teléfono). Necesario para enviar/recibir y para la
   pantalla de llamada propia.

> Android **no** tiene un permiso especial para otorgar permisos: cada uno lo
> concede el usuario en el diálogo del sistema o en la pantalla de Ajustes.

---

## Uso rápido

1. **Contactos → Importar** (Excel/CSV) o alta manual.
2. **Ajustes → Sincronizar desde Drive**: elige la pestaña del libro y en la
   **ventana flotante de revisión** marca (por categoría de gestión) qué
   contactos importar. La lista resultante reemplaza la de contactos a llamar.
3. En **Mis Contactos** filtra por **Tipificación**, **Estado** y **Medio**;
   selecciona contactos y asígnalos a un **usuario** si vas a repartir la carga.
4. **Editor**: escribe el mensaje (o usa una plantilla) y **Enviar**.
5. Para **llamadas**: Contactos → *Llamar seleccionados*, o la pestaña
   **Llamadas** para la secuencia masiva.
6. Configura retardos, timbrado y tope en **Ajustes**.
7. El **mensaje pregrabado** se graba/importa en Ajustes → *Mensaje* y se
   reproduce al contestar (si está activado).

---

## Estructura del proyecto

```
.
├── app/
│   ├── build.gradle.kts            # módulo app (deps, firma)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/masstext/app/
│       │   ├── MainActivity.kt     # navegación y pantallas principales
│       │   ├── data/               # Room: contactos, SMS, llamadas, plantillas
│       │   ├── import/Importer.kt  # lectura Excel/CSV (streaming, sin OOM)
│       │   ├── report/             # exportación a Excel
│       │   ├── service/            # SMS masivo, llamadas, grabación, audio
│       │   ├── smsrole/            # receptores de SMS/MMS (rol predeterminado)
│       │   ├── storage/            # respaldo/registros y usuarios
│       │   └── ui/                 # pantallas Compose
│       └── res/                    # recursos, temas, fuentes
├── gradle/                         # wrapper
├── build.gradle.kts                # plugins
├── settings.gradle.kts
├── gradle.properties
├── keystore.properties.example     # plantilla de firma
└── docs/                           # documentación detallada
```

Servicios clave (`app/src/main/java/com/masstext/app/service/`):

| Archivo | Función |
|---|---|
| `SmsSender.kt` / `SmsController.kt` / `SmsBatchTask.kt` | Envío masivo de SMS y progreso |
| `SmsSendService.kt` | Servicio en primer plano del envío |
| `CallSequencer.kt` | Secuencia de llamadas masivas |
| `MassTextInCallService.kt` | `InCallService`: pantalla/riel de llamada, mensaje y ruteo |
| `CallMonitor.kt` | Estado de la llamada y control de ruta (altavoz, BT) |
| `CallRecorder.kt` | Grabación de llamadas |
| `CallMessagePlayer.kt` | Reproduce el mensaje pregrabado en la ruta actual |
| `VoiceMessageStore.kt` | Graba/importa/almacena el mensaje pregrabado |
| `SheetPickerOverlay.kt` | Selector flotante de hojas de Google Sheets |
| `ImportPreviewOverlay.kt` | Ventana flotante de revisión al sincronizar (categorías + filas con casillas) |
| `Importer.kt` (`import/`) | Lectura de Excel/CSV en streaming |

---

## Documentación adicional

- [`docs/INSTALACION.md`](docs/INSTALACION.md) — instalación y roles paso a paso.
- [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md) — arquitectura y flujo interno.
- [`docs/ENVIO-SMS.md`](docs/ENVIO-SMS.md) — pipeline de envío masivo de SMS.
- [`docs/LLAMADAS-Y-MENSAJE.md`](docs/LLAMADAS-Y-MENSAJE.md) — llamadas masivas y
  mensaje pregrabado (incluye limitaciones del equipo).
- [`CHANGELOG.md`](CHANGELOG.md) — registro de cambios y pruebas realizadas.

---

## Comentarios y correcciones

Los reportes de correcciones se gestionan en el repositorio público
[`SaveFail/lex-recover-comentarios`](https://github.com/SaveFail/lex-recover-comentarios/issues)
(solo Issues; no contiene código ni acceso a la app).

---

## Rendimiento en equipos de bajos recursos

Pensado para equipos con poca RAM (4 GB) y poco almacenamiento:

- **APK de release ~2.9 MB** (R8 + `shrinkResources`).
- **Sin Apache POI**: Excel se lee y escribe en **streaming** (lector propio y
  escritor `XlsxWriter`), con bajo uso de memoria.
- **Bandeja**: sincronización con **una sola lectura** de ids y **inserción en
  lote** (antes eran 2 consultas por mensaje).
- Sin `largeHeap`; filtros y conteos calculados una vez por lista.

---

## Nota técnica: mensaje pregrabado en llamadas

El mensaje pregrabado se reproduce por la **ruta de audio real de la llamada**
(auricular, audífonos con cable/USB o Bluetooth). El **altavoz solo se activa
al pulsar su botón**.

**Limitación importante (Android):** en una llamada celular normal (GSM), el
sistema **no permite a una app inyectar audio en el canal de subida** (lo que
oye el interlocutor). El **cancelador de eco (AEC)** del teléfono elimina del
micrófono todo lo que reproduce el altavoz. Por eso, con este método, el
mensaje se escucha **en tu equipo** pero **no necesariamente en el del
cliente**. Las soluciones reales para que el cliente lo oiga requieren VoIP/SIP
o root, y quedan fuera del alcance de esta app. Ver
[`docs/LLAMADAS-Y-MENSAJE.md`](docs/LLAMADAS-Y-MENSAJE.md).

---

## Seguridad

- No se suben al repositorio: `keystore.properties`, `*.jks`, `local.properties`
  ni logs. Revísalo antes de cada push.
- El repositorio se recomienda **privado** (contiene lógica de negocio).
- Nunca compartas tu keystore ni sus contraseñas.

---

## Proyectos no incluidos

La carpeta local `_extras/` (paneles web, CRM, e-commerce) y `wa-worker/`
(worker de WhatsApp) son proyectos independientes y **no forman parte** de este
repositorio.
