# Beatland para Windows (escritorio)

Beatland de escritorio se instala con un archivo **`.msi`** (instalador normal de Windows). Funciona en **Windows 10/11 de 64 bits**.
Es **un instalador sin firmar** (no hay certificado de pago): Windows SmartScreen avisará una vez, ver el paso 4.

> **Estado honesto:** la versión de escritorio de este fork **todavía no se ha compilado nunca** con los cambios de Beatland (el agente de IA no puede
> compilar). Es probable que la primera vez aparezca algún error de compilación. Si pasa, copia el texto del error (instrucciones más abajo) y se corrige.

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

> Alternativa por consola, si prefieres: `git tag beatland-desktop-v1 myapp` y luego `git push origin beatland-desktop-v1`.

## Instalar en tu PC

1. Haz doble clic en el `.msi`.
2. **SmartScreen:** si aparece *"Windows protegió su PC"*, pulsa **Más información → Ejecutar de todas formas**. Pasa porque el instalador no está firmado.
3. El asistente deja elegir la carpeta. **No pide permisos de administrador**: se instala solo para tu usuario.
4. Crea un acceso directo en el escritorio y una carpeta **Beatland** en el menú Inicio.
5. **Actualizar** a una versión nueva: ejecuta el `.msi` nuevo; reemplaza a la anterior (usa un identificador fijo, `upgradeUuid`, nunca debe cambiarse).
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
- Funcionan en escritorio: selector de canciones para playlists, collage de miniaturas, traducción de letras con IA fiable + regenerar, y el estilo de traducción.
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

## Lista de comprobación tras instalar

1. La app abre y dice **Beatland** (ventana, bandeja, menú Inicio).
2. El ícono es el de las notas con la onda, no el de SimpMusic (ejecutable, ventana, pantalla de créditos).
3. Reproduce una canción (confirma que libmpv funciona).
4. Crea una playlist y añade canciones con el selector.
5. Cierra sesión de todo lo demás y vuelve a abrir: los ajustes persisten.
