# Correo (Gmail) dentro de Difusión

Difusión incluye un cliente de correo integrado que funciona **sin Google Cloud
y sin publicar en Play Store**. Usa la cuenta de Gmail del usuario por
**SMTP/IMAP** con una **contraseña de aplicación**.

## Qué puedes hacer

- **Ver carpetas:** Recibidos, **Todos** (All Mail), Enviados, Spam y Papelera.
- **Leer** un correo y **responder**.
- **Enviar** correos: uno suelto, **individual** o **masivo**.
- **Envío individual, correo por correo** (sin CC ni CCO): cada destinatario
  recibe su propio mensaje.
- **Correos precargados:** una lista que pegas una vez y queda guardada.
- **Envío en segundo plano** con **contador de progreso** y notificación
  (puedes cerrar la app y el envío continúa).
- **Plantillas** con asunto, reutilizables.

## Configuración (una sola vez por usuario)

1. En tu cuenta de Google activa la **verificación en 2 pasos**
   (Seguridad ▸ Verificación en 2 pasos). Es requisito de Google.
2. Entra a **https://myaccount.google.com/apppasswords** (la app tiene el botón
   **"Paso 1: Crear contraseña de aplicación"**). Crea una con el nombre
   *Difusión* y copia el código de **16 letras**.
3. En la app: **Mensaje ▸ Correo** (o **Ajustes ▸ Correo**):
   - Escribe tu **correo** y pega la **contraseña de aplicación**.
   - Toca **"Guardar y cargar"**.
4. Activa **IMAP** en Gmail: ⚙️ Configuración ▸ Reenvío y POP/IMAP ▸ Habilitar
   IMAP (la app tiene el botón **"Paso 2: Activar IMAP en Gmail"**).

> Gmail exige la **contraseña de aplicación** (no la contraseña normal). Si
> aparece `Application-specific password required`, es exactamente por eso.

## Cómo enviar

### Pestaña "Masivo" (opción principal dentro de Correo)
**Mensaje ▸ Correo ▸ Masivo** replica el envío masivo de SMS:
- **Destinatarios:** Seleccionados / Lista (correos precargados) / Individual.
- **Plantillas** (con Asunto), **Asunto** y **Mensaje**.
- **Programar** fecha y hora.
- **Enviar** con **contador de progreso en segundo plano**.

### Correo individual a los seleccionados
1. **Contactos** → marca personas (deben tener correo; se importa de la columna
   `CORREO` o `EMAIL`).
2. Botón **"Correo individual a N"**.
3. Escribe **un Asunto y un Mensaje** → **Enviar**.
   Cada contacto recibe su correo aparte (sin CC ni CCO).

### Desde Redactar (con más opciones)
**Mensaje ▸ Redactar ▸ Correo ▸ Destinatarios:**
- **Seleccionados** → individual a cada contacto marcado.
- **Lista** → los **correos precargados** (se guardan).
- **Individual** → un solo correo.

Puedes **programar** el envío (fecha y hora) y verlo en **Historial ▸ Programados**.

## Envío en segundo plano y velocidad

- El envío masivo corre en un **worker en primer plano** (WorkManager) con una
  notificación de progreso "N de M". Botón **"Segundo plano"** para seguir
  usando la app mientras envía.
- El motor de envío **reutiliza una sola conexión SMTP** para todos los correos,
  lo que lo hace mucho más rápido que abrir una conexión por cada mensaje.

## Alternativas (si no quieres usar contraseña de aplicación)

- **Puente con Google Apps Script** (`EmailSyncAppsScript.gs`): no requiere
  contraseña de aplicación; se despliega una vez y se autoriza con la cuenta.
  Configúralo en **Ajustes ▸ Correo ▸ Respaldo con Apps Script**.
- **Inicio de sesión nativo de Google** (API de Gmail): requiere crear un
  cliente OAuth en Google Cloud. La app ya trae el soporte; el Client ID se
  incrusta en `integraciones.properties`.

## Notas

- Límite de Gmail normal: ~500 correos/día.
- Los correos se envían desde tu propia cuenta (tu remitente real).
- La contraseña de aplicación se guarda solo en el dispositivo.
