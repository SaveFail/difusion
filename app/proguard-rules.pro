# Reglas de R8/ProGuard para el APK de release.
# Objetivo: APK pequeño sin romper nada por ofuscación.

# Anotaciones y firmas (Room y otras librerías las usan).
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Entidades y DAOs de Room: se accede a campos por nombre generado.
-keep class com.difusion.app.data.** { *; }

# Clases del sistema referenciadas desde el manifiesto (receptores/servicios):
# AGP ya las conserva, pero se refuerza por seguridad.
-keep class com.difusion.app.smsrole.** { *; }
-keep class com.difusion.app.service.DifusionInCallService { *; }
-keep class com.difusion.app.ui.CallActivity { *; }
-keep class com.difusion.app.ui.DialerActivity { *; }

# Corrutinas de Kotlin.
-dontwarn kotlinx.coroutines.**

# JavaMail (SMTP/IMAP) usa reflexión: conservar sus clases.
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-dontwarn org.apache.**
