import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// La firma del release se lee de keystore.properties (que NO se sube al repo).
// Si ese archivo no existe, el APK release se compila sin firmar.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// Configuración incrustada (para que los usuarios NO tengan que configurar nada).
// Se lee de integraciones.properties (local).
val integracionesFile = rootProject.file("integraciones.properties")
val integraciones: Properties = Properties().apply {
    if (integracionesFile.exists()) {
        integracionesFile.inputStream().use { load(it) }
    }
}
val gmailClientId: String = integraciones.getProperty("oauthClientId", "")
val emailBridgeUrl: String = integraciones.getProperty("emailBridgeUrl", "")
val emailBridgeToken: String = integraciones.getProperty("emailBridgeToken", "")
val driveBridgeUrl: String = integraciones.getProperty("driveBridgeUrl", "")
val driveBridgeToken: String = integraciones.getProperty("driveBridgeToken", "")
val smtpUser: String = integraciones.getProperty("smtpUser", "")
val smtpAppPassword: String = integraciones.getProperty("smtpAppPassword", "")

android {
    namespace = "com.difusion.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.difusion.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 27
        versionName = "4.4"
        buildConfigField("String", "GMAIL_OAUTH_CLIENT_ID", "\"$gmailClientId\"")
        buildConfigField("String", "EMAIL_BRIDGE_URL", "\"$emailBridgeUrl\"")
        buildConfigField("String", "EMAIL_BRIDGE_TOKEN", "\"$emailBridgeToken\"")
        buildConfigField("String", "DRIVE_BRIDGE_URL", "\"$driveBridgeUrl\"")
        buildConfigField("String", "DRIVE_BRIDGE_TOKEN", "\"$driveBridgeToken\"")
        buildConfigField("String", "SMTP_USER", "\"$smtpUser\"")
        buildConfigField("String", "SMTP_APP_PASSWORD", "\"$smtpAppPassword\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 elimina código y recursos no usados: APK mucho más pequeño y
            // arranque más ligero en equipos de bajos recursos.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/DEPENDENCIES.md"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Excel (.xlsx) se lee y escribe con nuestro propio lector/escritor en
    // streaming: no se usa Apache POI ni CSV extra (menos APK y menos memoria).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Sincronización con Google Sheets (Apps Script) y envío de correo masivo.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Envíos programados en segundo plano.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Inicio de sesión con Google y autorización de Gmail (Identity Services).
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Envío y lectura de correo por SMTP/IMAP (Gmail con contraseña de aplicación).
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    // Escáner de QR con la cámara. Google Code Scanner lo provee Play Services
    // (no requiere permiso de cámara y casi no aumenta el APK).
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    testImplementation("junit:junit:4.13.2")
}
