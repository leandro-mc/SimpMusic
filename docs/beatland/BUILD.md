# Cómo compila el dueño

El agente **no puede compilar**. El dueño compila y pega los errores.

- Android Studio: panel *Build Variants* → módulo `androidApp` → **`beatlandDebug`** (pruebas) o **`beatlandRelease`** (uso diario). Nunca `official*`: no tiene ventana flotante ni el nombre Beatland. No uses el módulo `vivoAndroidApp`.
- Cómo comprobar que instalaste el correcto: *Información de la app* debe mostrar el paquete `com.maxrave.simpmusic.beatland` (en debug termina en `.dev`), y en Ajustes la fila de versión termina en `-beatland`.
- En *Generate Signed App Bundle / APK* elige solo `beatland` + `release`.
- Línea de comandos: `./gradlew :androidApp:assembleBeatlandDebug` / `:androidApp:assembleBeatlandRelease`.
- Usar `isFullBuild=false` (sin Firebase/Sentry de upstream; D-016).
- La APK sale en `androidApp/build/outputs/apk/beatland/<tipo>/`.
- `build_and_sign_apk.sh` y el CI de upstream asumen rutas sin flavor y **no están adaptados**; no los uses para Beatland.
- Escritorio: ver `CLAUDE.md` del original; el nombre del paquete sale de `beatland.properties`.
- Tras clonar: `git submodule update --init`.
