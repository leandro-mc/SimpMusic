# Cómo compila el dueño

El agente **no puede compilar**. El dueño compila y pega los errores.

- Android Studio: variante **`beatlandDebug`** (pruebas) o **`beatlandRelease`** (uso diario).
- Línea de comandos: `./gradlew :androidApp:assembleBeatlandDebug` / `:androidApp:assembleBeatlandRelease`.
- Usar `isFullBuild=false` (sin Firebase/Sentry de upstream; D-016).
- La APK sale en `androidApp/build/outputs/apk/beatland/<tipo>/`.
- `build_and_sign_apk.sh` y el CI de upstream asumen rutas sin flavor y **no están adaptados**; no los uses para Beatland.
- Escritorio: ver `CLAUDE.md` del original; el nombre del paquete sale de `beatland.properties`.
- Tras clonar: `git submodule update --init`.
