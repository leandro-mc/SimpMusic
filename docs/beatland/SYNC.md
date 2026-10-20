# Sincronizar `myapp` con `dev` (upstream)

Cada cierto tiempo se traen las novedades del proyecto original. **Nunca** se hace directamente en `myapp`.

## Pasos

1. **Dueño:** en GitHub, "Sync fork" sobre la rama `dev` (debe ser fast-forward; `dev` nunca tiene commits propios).
   Si `dev` se desvió, avisar: algo se commiteó donde no debía.
2. Crear la rama de integración desde `myapp`:
   `git checkout myapp && git checkout -b sync/dev-AAAA-MM-DD`
3. Traer `dev`: `git merge dev` (merge, no rebase: conserva el historial y facilita revertir).
4. Resolver conflictos con esta tabla (y con `DECISIONS.md`):

| Archivo en conflicto | Qué hacer |
|---|---|
| `composeResources/values*/*.xml` | Tomar la versión de upstream y volver a correr `apply_brand.py` (paso 6). |
| `conveyor.conf`, `desktopApp/build.gradle.kts` | Tomar upstream y reaplicar los puntos de `RULES.md` → "Mantener siempre". |
| `androidApp/build.gradle.kts` | Conservar los bloques `productFlavors`, `IS_PERSONAL_FORK` y `ignoredFlavors … "beatland"`. |
| `MainActivity.kt` | Conservar `!BuildConfig.IS_PERSONAL_FORK` en `checkOfficialBuild`. |
| `SettingScreen.kt` | Conservar el bloque `floating_lyrics` y el `if (ForkPolicy.UPSTREAM_UPDATE_CHECK_ENABLED)` que envuelve los ajustes de actualización; si upstream cambió el patrón de `SettingGroup`/`SettingItem`, adaptar. |
| `HomeScreen.kt`, `SharedViewModel.kt` | Conservar las guardas de `ForkPolicy` (promos y `checkForUpdate`). Si upstream añade un diálogo promocional nuevo, protegerlo igual. |
| `build.gradle.kts` (raíz) | Conservar el bloque `lintVital` dentro de `subprojects` (D-022). |
| `ViewModelModule.kt`, `LibraryViewModel.kt`, `LibraryScreen.kt`, `LocalPlaylistScreen.kt`, `SongSelectionState.kt` | Conservar los ganchos del selector de playlists (marcados `BEATLAND (D-023/D-024)`); ver `FEATURES.md`. |
| `gradle/libs.versions.toml` | Conservar AGP `9.3.0` (D-019). |
| `vivoAndroidApp/build.gradle.kts` | Conservar `IS_PERSONAL_FORK` (D-018). |
| `CLAUDE.md` | Tomar upstream y volver a poner la línea de cabecera que apunta a `docs/beatland/README.md`. |
| Cualquier otro | Preferir upstream y reaplicar solo el cambio listado en `RULES.md`/`FEATURES.md`. |

5. Si upstream **re-introduce algo que decidimos no traer** (ver `RULES.md` → "Nunca traer"), descartarlo y anotarlo.
6. Reaplicar la marca: `python3 scripts/beatland/apply_brand.py` y luego `python3 scripts/beatland/apply_brand.py --check`.
   Corregir los literales Kotlin que reporte con `Brand.NAME`.
7. Validar estáticamente que la ventana flotante sigue compilando contra upstream: revisar que existan y no hayan cambiado de firma
   los símbolos listados en `FEATURES.md` → "Dependencias de upstream".
8. Registrar en `DECISIONS.md` cualquier decisión nueva, actualizar `CHANGES.md` si cambió algún archivo propio o tocado, y anotar en `SYNC_LOG.md` la fecha y los conflictos resueltos.
9. **Pedir al dueño que compile** `beatlandDebug`. Si hay errores, él los pasa y se corrigen en la misma rama.
10. Cuando compile, merge de `sync/dev-AAAA-MM-DD` a `myapp` (PR o `--no-ff`). Después borrar la rama de sync.
11. Las ramas `feature/*` abiertas se actualizan con `git merge myapp`.

## Si upstream toca el submódulo `core`

El puntero `core` se actualiza solo con el merge. Tras el merge: `git submodule update --init`. Nunca commitear dentro de `core/`.
