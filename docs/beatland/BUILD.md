# Cómo compila el dueño

El agente **no puede compilar**. El dueño compila y pega los errores.

- Android Studio: panel *Build Variants* → módulo `androidApp` → **`beatlandDebug`** (pruebas) o **`beatlandRelease`** (uso diario). Nunca `official*`: no tiene ventana flotante ni el nombre Beatland. No uses el módulo `vivoAndroidApp`.
- Cómo comprobar que instalaste el correcto: *Información de la app* debe mostrar el paquete `com.maxrave.simpmusic.beatland` (en debug termina en `.dev`), y en Ajustes la fila de versión termina en `-beatland`.
- En *Generate Signed App Bundle / APK* elige solo `beatland` + `release`.
- Línea de comandos: `./gradlew :androidApp:assembleBeatlandDebug` / `:androidApp:assembleBeatlandRelease`.
- Usar `isFullBuild=false` (sin Firebase/Sentry de upstream; D-016).
- La APK sale en `androidApp/build/outputs/apk/beatland/<tipo>/`.
- `build_and_sign_apk.sh` y el CI de upstream asumen rutas sin flavor y **no están adaptados**; no los uses para Beatland.
- Escritorio / Windows: ver **`DESKTOP.md`** (instalador `.msi` con el script de PowerShell en tu PC). El nombre sale de `beatland.properties`.
- Tras clonar: `git submodule update --init`.
- **Comprobar en tu PC (lo normal, D-054):** el agente no compila ni usa GitHub Actions para probar; tú compilas aquí. Desde la carpeta del proyecto, en PowerShell (si el nombre de una tarea no existe, `.\gradlew.bat :desktopApp:tasks` o `:composeApp:tasks` las lista):
  - **Escritorio, solo comprobar que compila** (rápido, sin instalador): `.\gradlew.bat :composeApp:compileKotlinJvm -PisFullBuild=false --no-configuration-cache`
  - **Escritorio, ejecutar la app desde el código:** una vez `.\gradlew.bat :composeApp:mpvSetupAll -PisFullBuild=false -PmpvSlices=windows-x64 --no-configuration-cache` (descarga libmpv) y después `.\gradlew.bat :desktopApp:run -PisFullBuild=false --no-configuration-cache`.
  - **Escritorio, instalador `.msi`:** `powershell -ExecutionPolicy Bypass -File scripts\beatland\build-windows-installer.ps1` (ver `DESKTOP.md`).
  - **Android:** `.\gradlew.bat :androidApp:assembleBeatlandDebug -PisFullBuild=false`, o *Run* en Android Studio con la variante `beatlandDebug`. Si compilas con el valor por defecto de Android Studio (`isFullBuild=true`), también debe compilar: añade `-PisFullBuild=true` y repite.
  - **Pruebas de la app (opcional):** `.\gradlew.bat :composeApp:jvmTest -PisFullBuild=false --no-configuration-cache`.
  - Si falla, copia las últimas ~60 líneas del error y pásalas; con eso se corrige.
- **GitHub Actions (excepcional, D-054):** los workflows están deshabilitados y solo se usan cuando el dueño lo autoriza, como máximo una vez por semana (ver `RULES.md`, «CI»). `beatland-android-check.yml` compila `beatlandDebug` (con `isFullBuild` `false` y `true`) y deja la APK como artefacto; `beatland-windows-installer.yml` deja el `.msi` en una *release*.
