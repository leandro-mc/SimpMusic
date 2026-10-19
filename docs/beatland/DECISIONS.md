# Registro de decisiones

Formato: `D-0xx` — estado — decisión — motivo. **Consulta este archivo antes de resolver conflictos de merge.**
Los IDs se citan en comentarios de código y en `scripts/beatland/apply_brand.py`: no los renumeres.

| ID | Estado | Decisión | Motivo |
|---|---|---|---|
| D-001 | Vigente | Modelo de ramas: `dev` espejo, `myapp` control, `feature/*` desde `myapp`, `sync/dev-*` para integrar upstream. | Poder recibir novedades de upstream sin perder la personalización. |
| D-002 | Vigente | Dos flavors Android: `official` (sin cambios) y `beatland` (`applicationIdSuffix = ".beatland"`, nombre/ícono propios). Cambios propios por source set de flavor. | Instalación en paralelo y mínimo diff sobre archivos de upstream. |
| D-003 | Vigente | Identificadores internos NO se renombran: paquete `com.maxrave.simpmusic`, claves de recursos (`simpmusic_lyrics`…), esquemas de URL, carpeta `.simpmusic`, nodo `Preferences "SimpMusic/MiniPlayer"`, User-Agent `SimpMusic`, nombre de archivos de backup `simpmusic_backup_*.zip`, `app.fsname`/`app.rdns-name` de Conveyor. | Renombrarlos rompería datos, deep links y haría imposible el merge. Solo cambia lo que el usuario ve. |
| D-004 | Vigente | Nada de morado ni íconos de IA en lo personalizado. La ventana flotante usa azul (`0xFF3B82F6`) y fondo azul marino (`0xFF0E1420`). | Preferencia del dueño. |
| D-005 | Vigente | El nombre se parametriza en `beatland.properties` y se propaga con `scripts/beatland/apply_brand.py` (strings de todos los idiomas, `Brand.kt`, `app_name.xml`, `conveyor.conf`); Gradle de escritorio lo lee directo. | Cambiar el nombre es editar una línea y correr un script; el script se re-ejecuta tras cada sync. |
| D-006 | Vigente | Se conserva "SimpMusic Lyrics" (la base de letras comunitaria de upstream) sin renombrar. | Es un servicio de terceros; renombrarlo atribuiría mal la fuente. Confirmado por el dueño. |
| D-007 | Vigente (implementada) | Los diálogos promocionales del autor (reseña/"star en GitHub", blog, kotlin-footguns) están **desactivados** con `ForkPolicy.UPSTREAM_PROMOS_ENABLED = false` (guardas en `HomeScreen.kt`). Sus textos (`blog_promo_message`, `footguns_promo_message`, `enjoying_simpmusic`, `if_you_enjoy_using_simpmusic_star_simpmusic_on_github_or_leave_a_review_on`) se dejan intactos y protegidos en `apply_brand.py`: son código muerto. | Hablan en nombre del autor original y enlazan a upstream. Decisión del dueño. |
| D-008 | Vigente | NO se integra `RetryingDataStore` ni el cambio a `DataStoreManagerImpl.jvm.kt` del zip Sonara. | Upstream ya corrigió el crash de Windows de forma distinta (`ReadsWaitForWritesStorage`); además vive en el submódulo `core`. |
| D-009 | Vigente (implementada) | El verificador de actualizaciones está **desactivado** con `ForkPolicy.UPSTREAM_UPDATE_CHECK_ENABLED = false`: `SharedViewModel.checkForUpdate()` sale de inmediato y los 3 ajustes de actualización (auto-check, canal, comprobar ahora) se ocultan en `SettingScreen.kt`. La etiqueta "SimpMusic GitHub Release" queda en ese código inerte. | Apunta a los releases de upstream y ofrecería instalar SimpMusic en vez de Beatland. Decisión del dueño. |
| D-010 | Vigente | En el flavor `beatland` se omite `checkOfficialBuild` mediante `BuildConfig.IS_PERSONAL_FORK` (`true` solo en `beatland`). | Upstream bloquea en release cualquier `applicationId` fuera de `Config.OFFICIAL_PACKAGE_NAMES` (en `core`, no editable). |
| D-011 | Vigente | `app.vcs-url` de Conveyor apunta a `https://github.com/leandro-mc/SimpMusic`. | Es el código fuente real del fork (GPL). Confirmado por el dueño. |
| D-012 | Vigente | `mpvSetupAll` y `writeConveyorConfig` se marcan `notCompatibleWithConfigurationCache`. | Sus `doLast` usan referencias del script; upstream lo esquiva con `--no-configuration-cache` en CI. |
| D-013 | Vigente | No se porta el bucle tolerante a fallos de `mpvSetupAll` ni el checksum linux-x64 del zip. | Upstream ya tiene un checksum más nuevo; si vuelve a fallar una slice, reevaluar. |
| D-014 | Vigente | "SimpMusic Charts"/"Chart" se renombra a "Beatland Charts". | Es el nombre de una función de la app, no de un servicio ajeno. Confirmado por el dueño. |
| D-015 | Vigente | Idioma: docs en español; código, comentarios y commits en inglés; conversación con el dueño en español. | Preferencia del dueño. |
| D-016 | Vigente | Sentry se ignora en el flavor `beatland` (`ignoredFlavors`). Beatland se compila con `isFullBuild=false`. | Sin Firebase/Sentry propios; evita instrumentación inyectada y que el cleanup FOSS dependa de nombres de variante sin flavor. |
| D-017 | Reemplazada por D-020 | Ícono temporal de Sonara (onda en forma de "S"). | — |
| D-018 | Vigente | `vivoAndroidApp` también define `buildConfigField IS_PERSONAL_FORK = false`. | `vivoAndroidApp` comparte `MainActivity.kt` con `androidApp`, que ahora lee `BuildConfig.IS_PERSONAL_FORK`; sin el campo no compila. (Lo añadió el dueño.) |
| D-019 | Vigente | Versión del Android Gradle Plugin fijada en `9.3.0` (`gradle/libs.versions.toml`, antes `9.4.1`). | Compatibilidad con el Android Studio del dueño. Si un sync con `dev` la sube otra vez, **conservar 9.3.0** hasta que el IDE soporte la nueva. (Lo hizo el dueño.) |
| D-020 | Vigente | Ícono Beatland v2: una "b" minúscula blanca que funciona como nota/vinilo (tallo + aro) sobre degradado azul vivo con luz suave, viñeta y sombra. Generado por `scripts/beatland/generate_icon.py` (vectores adaptativos + monocromo + PNG/ICO/ICNS). Paletas alternativas sin morado: `--palette sunset` (naranja/rojo) y `--palette ink` (negro y dorado). | Aspecto moderno/premium y legible a 40 px. Para cambiarlo, editar el script y volver a ejecutarlo; no editar los PNG a mano. Reemplaza el monograma "B" dorado inicial. |
| D-021 | Vigente | El flavor `beatland` es el predeterminado (`isDefault = true`). | Evitar que el IDE seleccione `official` (que no muestra la ventana flotante ni el nombre Beatland). |

## Plantilla para nuevas decisiones

`| D-0NN | Vigente | <qué se decidió> | <por qué> |` — y si reemplaza una anterior, marca la vieja como `Reemplazada por D-0NN`.
