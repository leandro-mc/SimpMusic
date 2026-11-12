# Beatland para Windows (escritorio)

Beatland de escritorio se instala con un archivo **`.msi`** (instalador normal de Windows). Funciona en **Windows 10/11 de 64 bits**.
Es **un instalador sin firmar** (no hay certificado de pago): Windows SmartScreen avisará una vez, ver el paso 4.

> **Estado honesto:** el agente de IA no puede compilar. Los instaladores `beatland-desktop-v1` a `v3` se compilaron, instalaron y actualizaron en el sitio;
> los cambios posteriores (D-039 y D-040) solo se comprueban cuando GitHub compila uno nuevo. Si falla, copia el texto del error (instrucciones más abajo) y se corrige.

## Opción A (recomendada): que GitHub lo compile, sin instalar nada en tu PC

El repositorio es público, así que las "Actions" de GitHub son gratis. Una máquina Windows de GitHub compila el instalador (15-40 min) y te lo deja para descargar.

**Una sola vez — activar las Actions de tu fork**
1. Entra a tu repositorio en GitHub → pestaña **Actions**.
2. Si ves el mensaje *"Workflows aren't being run on this forked repository"*, pulsa **"I understand my workflows, go ahead and enable them"**.

**Cada vez que quieras generar un instalador**
1. Repositorio → **Releases** → **Draft a new release** (a la derecha).
2. En **Choose a tag** escribe un nombre **que empiece por `beatland-desktop-`**, por ejemplo `beatland-desktop-v1` (la siguiente vez `-v2`, etc.) y elige **Create new tag … on publish**.
3. En **Target** elige la rama **`myapp`**. (Mientras la rama `feature/desktop-beatland-installer` no esté fusionada en `myapp`, elige **esa** rama: el workflow vive en ella. La etiqueta usa el contenido de la rama que elijas.)
4. Ponle un título (por ejemplo "Beatland desktop v1") y pulsa **Publish release**.
5. Ve a la pestaña **Actions**: verás **"Beatland - Windows installer (.msi)"** ejecutándose. Espera a que tenga el visto verde.
6. Vuelve a **Releases**: dentro de esa versión aparece el archivo **`Beatland-<versión>-windows-x64.msi`**. Descárgalo.
   (También queda como "Artifact" dentro de la ejecución, durante 90 días.)

**Si la ejecución sale en rojo ❌:** ábrela, entra al paso que falló (marcado en rojo), copia las últimas ~60 líneas del registro y pásalas. Con eso se corrige.

> **Si el paso que falla es el de Gradle y el error menciona `gradle-distributions` o `gradle-9.x-bin.zip`:** es la descarga de Gradle desde GitHub, que a veces falla.
> Desde D-042 un paso previo la hace con reintentos; si aun así cae, basta con **Re-run failed jobs**. Los errores de Gradle también quedan como anotación roja ("Gradle failed")
> en la página del run, además del registro.
>
> **Error `Checksum mismatch for mpv-natives-<plataforma>.tar.gz` (paso "Download the native player libraries"):** el proyecto original volvió a publicar ese archivo
> de libmpv bajo la misma etiqueta y la suma fijada en el código ya no coincide (pasó con `linux-x64` en la v4). **Nunca pegues el valor "actual" del error en el código**: eso
> desactiva la comprobación de integridad. La suma buena es la que fija el original en su rama `dev` (`composeApp/build.gradle.kts`, `mpvNativesChecksums`); si esa rama
> no la ha actualizado, avisa antes de aceptar nada. Desde D-040 el instalador de Windows solo descarga y verifica la rebanada `windows-x64`, así que un cambio en las demás
> plataformas ya no lo rompe.

> Alternativa por consola, si prefieres: `git tag beatland-desktop-v1 myapp` y luego `git push origin beatland-desktop-v1`.

## Instalar en tu PC

1. Haz doble clic en el `.msi`.
2. **SmartScreen:** si aparece *"Windows protegió su PC"*, pulsa **Más información → Ejecutar de todas formas**. Pasa porque el instalador no está firmado.
3. El asistente deja elegir la carpeta. **No pide permisos de administrador**: se instala solo para tu usuario.
4. Crea un acceso directo en el escritorio y una carpeta **Beatland** en el menú Inicio.
5. **Actualizar** a una versión nueva: ejecuta el `.msi` nuevo; reemplaza a la anterior sin desinstalar. Para que Windows lo acepte, cada instalador lleva una versión **mayor** que la anterior: `2.3.<n>`, donde `n` es el número de commits del proyecto (sube solo; D-037). Windows lo mostrará, por ejemplo, como `2.3.1660`; no es el número de SimpMusic aunque empiece igual. Además se usa un identificador fijo (`upgradeUuid`) que nunca debe cambiarse.
   - Si ves *"Ya está instalada otra versión de este producto"*, el instalador nuevo tenía la **misma** versión que el instalado (pasa si compilas dos veces el mismo commit, o con las primeras versiones `beatland-desktop-v1`/`v2`, que eran ambas `2.3.0`). Desinstala una vez y vuelve a instalar; a partir de ahí se actualiza en el sitio.
   - Un instalador **más antiguo** que el instalado tampoco se instala encima (Windows lo trata como un retroceso).
6. **Desinstalar:** Configuración → Aplicaciones → Beatland.

## Opción B: compilarlo tú en tu PC (solo si la A no te sirve)

Necesitas, una sola vez:
1. **JDK 21** (con `jpackage`): `winget install Microsoft.OpenJDK.21` y comprobar que `JAVA_HOME` apunta a él. (El JDK que trae Android Studio puede no incluir `jpackage`.)
2. **WiX Toolset 3.x**: `choco install wixtoolset` (o el instalador de https://github.com/wixtoolset/wix3/releases).
3. Tener el repositorio al día: `git checkout myapp`, `git pull`, `git submodule update --init --recursive`.

Después, en PowerShell, desde la carpeta del proyecto:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\beatland\build-windows-installer.ps1
```

Descarga las librerías del reproductor (libmpv), compila y deja el instalador en `desktopApp\build\compose\binaries\main\msi\`. El script revisa los requisitos y dice qué falta.

## Qué cambia respecto al SimpMusic de escritorio

- Nombre **Beatland** en la ventana, la bandeja del sistema, el ejecutable (`Beatland.exe`), el menú Inicio, el instalador y "Aplicaciones".
- Ícono Beatland en el ejecutable, la ventana, la bandeja y la pantalla de créditos (antes seguía el logo de SimpMusic ahí, también en Android).
- Funcionan en escritorio: selector de canciones para playlists, collage de miniaturas (en Windows lee las carátulas con Skia, incluidas las WebP; D-038), traducción de letras con IA fiable + regenerar, y el estilo de traducción.
- **No existe en escritorio:** la letra flotante (es una función solo de Android).

## Datos y cosas a saber

- **Carpeta de datos:** `C:\Users\<tu usuario>\.simpmusic`. Es la **misma** que usa el SimpMusic de escritorio original: si lo tienes instalado, no abras los dos a la vez y ten en cuenta que comparten base de datos y ajustes. (La ruta forma parte del submódulo `core`, que no se puede cambiar; ver D-003.)
- La app **no se actualiza sola** (el verificador de actualizaciones está desactivado, D-009): para actualizar, genera un instalador nuevo (Opción A) e instálalo encima.
- Las sesiones (YouTube, Last.fm) y los ajustes de escritorio son independientes de los de Android: hay que iniciar sesión y configurar la IA de nuevo.
- La primera vez Windows Defender puede escanear el instalador un rato; es normal.

## Sobre los workflows del repositorio original en tu fork

Cada vez que sincronizas `dev` con el original, GitHub ejecuta en tu fork los workflows de upstream (`android.yml`, `desktop-package.yml`…), que necesitan
secretos que tu fork no tiene y **fallarán** (no afecta a tu app). Para evitar el ruido: pestaña **Actions** → elige cada uno de esos workflows → menú **⋯ → Disable workflow**.
**No** desactives "Beatland - Windows installer (.msi)".

## Si algo falla al abrir

- **Ventana de error al arrancar:** ciérrala y vuelve a abrir la app. Una vez ocurrió un fallo intermitente de arranque (`Reading a state that was created after the snapshot was taken…`, dentro de la capa de avisos) que desapareció al reabrir; desde la versión `beatland-desktop-v2` esa capa se carga un instante después para evitarlo (D-036). Si reaparece, copia el texto completo del error y anota qué estabas haciendo.
- **Registro de la app:** exporta el registro desde la app y pásalo junto con el error. Es normal que contenga errores como: *canciones importadas de Spotify que no existen en YouTube Music*; un **429** del servicio de letras (límite temporal de peticiones); un **403** en una canción concreta que YouTube no deja reproducir; o un **503 "high demand"** de Gemini al traducir (el proveedor de IA saturado: reintenta más tarde).

## Si el collage de una playlist no aparece

Normalmente aparece **1-3 segundos** después de pulsar "Añadir N canciones" en el selector (hasta ~15 s si las canciones se añadieron por otro camino). Si pasa más tiempo, abre y cierra la Biblioteca para forzar una comprobación.
Si aun así no aparece, exporta el log de la app y busca las líneas `PlaylistCollage`:
`Building…` → `Collage of playlist N: 600x600, … bytes` → `Collage saved…` significan que se generó bien y el fallo estaría al **mostrarla**;
un aviso `not built` o `cannot be decoded` indica el motivo. Pasa esas líneas.

## Descargas masivas y congelaciones (D-048, D-049)

Las descargas van en una **cola de segundo plano de 2 canciones a la vez**: pulsar «descargar» en una playlist o un artista encola todo al instante y la app sigue fluida y reproduciendo.

**Prueba (con el instalador nuevo):**
1. Con música sonando, abre una playlist grande (~150 canciones) y pulsa descargar todas. Repite con el top de un artista (selección múltiple → Descargar).
2. Comprueba: la interfaz sigue fluida (puedes navegar, buscar, pausar, cambiar de canción); la música no se corta; el botón de descarga de la playlist y los iconos de cada canción avanzan; las canciones se van completando de 2 en 2.
3. Cancela: abre el menú de tres puntos de una canción que esté descargando y pulsa «Cancel download»; luego prueba Ajustes → «Limpiar caché de descargas» (quita **todas** las descargas, también las que esperan en la cola). Las canciones vuelven a «no descargadas» y la cola se vacía.
4. Sal de la app con normalidad (bandeja → Salir). Vuelve a abrirla: ninguna canción debe quedar «descargando» sin descargarse.
5. Con descargas en curso, sal de la pantalla de la playlist y entra en otra: las descargas siguen.

**Si algo se congela otra vez**, exporta el log de la app (pantalla de registro) y pasa las líneas con las etiquetas **`UiStall`** (qué hacía cada hilo mientras la ventana no respondía) y **`DownloadQueue`** (qué hacía la cola). El log está en `C:\Users\<tu usuario>\.simpmusic\logs\app.log`.

**Nota:** si cancelas una descarga que ya estaba en curso, los avisos de Windows por lote («descarga iniciada/terminada») dejan de salir hasta reiniciar la app (limitación de `core`); las descargas funcionan igual.

## Lista de comprobación tras instalar

1. La app abre y dice **Beatland** (ventana, bandeja, menú Inicio).
2. El ícono es el de las notas con la onda, no el de SimpMusic (ejecutable, ventana, pantalla de créditos).
3. Reproduce una canción (confirma que libmpv funciona).
4. Crea una playlist y añade canciones con el selector.
5. Cierra sesión de todo lo demás y vuelve a abrir: los ajustes persisten.
6. Descarga una playlist grande (~150 canciones): la interfaz sigue fluida y la música no se corta (ver «Descargas masivas y congelaciones»).
