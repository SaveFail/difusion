# Instalación y configuración

## 1. Compilar

```bash
./gradlew assembleDebug          # APK de prueba
./gradlew assembleRelease        # APK firmado (requiere keystore.properties)
```

Salidas:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

## 2. Instalar en el teléfono

**Opción A — por USB (depuración):**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Opción B — directo en el teléfono:**
Copia el `.apk` al teléfono, ábrelo y permite "instalar apps de origen
desconocido" cuando lo pida.

## 3. Roles y permisos (obligatorio)

En **Ajustes** de la app:

1. **App de SMS predeterminada** → *Hacer app de SMS predeterminada*.
   Sin esto no se pueden enviar/recibir SMS ni leer la bandeja.
2. **App de llamadas predeterminada** → *App de llamadas predeterminada*.
   Habilita la pantalla de llamada propia y el riel de controles (silenciar,
   altavoz, espera, teclado, colgar, Bluetooth).
3. **Permisos** (el sistema los pedirá o ve a Ajustes del sistema → Apps →
   LEX RECOVER → Permisos):
   - SMS (enviar/recibir/leer)
   - Teléfono y estado de la llamada
   - Contactos
   - Micrófono (para grabar el mensaje pregrabado y las llamadas)
   - **Mostrar sobre otras apps** (overlay de llamada entrante)
   - Notificaciones (Android 13+)

## 4. Importar contactos

- **Excel (.xlsx)** o **CSV** desde el botón *Importar* en Contactos.
- **Google Sheets**: comparte la hoja como *Lector* y pega el enlace en
  Ajustes → *Enlace de tu hoja*. Luego *Sincronizar*; aparece un selector
  flotante con las hojas disponibles; elige una y pulsa *Finalizar*.

## 5. Configuración de llamadas

En **Ajustes**:

- **Retardo entre mensajes** (segundos).
- **Retardo entre llamadas** (segundos).
- **Duración del timbrado** (segundos).
- **Tope de duración de llamada** (0 = sin límite).
- **Repetición**: por cola o por usuario.
- **Grabación de llamadas**: activar/desactivar.
- **Mensaje pregrabado**: activar, *Grabar mensaje*, *Elegir audio* o *Borrar*.

## 6. Solución de problemas

| Síntoma | Causa / solución |
|---|---|
| No envía SMS | Falta el rol de SMS predeterminado o el permiso de enviar SMS. |
| No aparece la pantalla de llamada | Falta el rol de teléfono predeterminado. |
| No graba llamadas | El fabricante bloquea la captura; revisa el registro en Grabaciones. |
| No se importan números | Verifica que la columna de teléfono exista en la hoja. |
| El APK de release no firma | Falta `keystore.properties` o el `.jks`. |
