# Respaldos (backups)

Cada carpeta aquí es una **copia congelada** del código fuente de una versión
estable ("punto base" / *core*). Sirven de respaldo por si una actualización
futura introduce problemas: se puede volver a esta versión.

## Versiones

- **`v2.5/`** — Versión **2.5** (`versionCode 16`). **Punto base actual.**
  Contiene todo el código fuente (Kotlin/Compose), recursos, configuración de
  Gradle, `.github` y documentación de esa versión.
  **No** incluye claves de firma (`keystore.properties`, `*.jks`),
  `local.properties` ni artefactos de compilación (`build/`).

## Cómo restaurar

```bash
# Copiar el contenido del respaldo sobre la raíz del proyecto
cp -r backup/v2.5/. .        # (desde la raíz del repo)

# Compilar el APK de release (requiere keystore.properties para firmar)
./gradlew assembleRelease
```

## Cómo crear un nuevo respaldo

```bash
# Desde la raíz del proyecto, con el árbol de trabajo limpio y la versión ya commiteada
mkdir -p backup/vX.Y
git archive HEAD | tar -x -C backup/vX.Y
```

> `git archive` solo incluye archivos versionados, por lo que excluye
> automáticamente claves, `local.properties` y carpetas de compilación.
