package com.maxrave.simpmusic.beatland.collage

import coil3.Uri
import coil3.map.Mapper
import coil3.request.Options
import java.net.URI

/**
 * Beatland fork (D-044): lets Coil load a `file:/C:/...` thumbnail on Windows.
 *
 * Coil 3.6.3 cannot read that form. Its URI parser, on a string like `file:/C:/Users/me/a.jpg` (one slash, what
 * `java.io.File.toURI()` produces on Windows), takes the colon of the drive letter for a second scheme separator and
 * ends up with the scheme `file:/C`. `FileUriFetcher` only accepts the scheme `file`, so the request dies with
 * "IllegalStateException: Unable to create a fetcher that supports: file:/C:/Users/...", the grid falls back to the
 * default tile, and neither the automatic playlist collage nor a cover picked by hand ever shows.
 *
 * This mapper runs before Coil's own `StringMapper` and, for exactly that shape of string, builds the [Uri] directly
 * (scheme `file`, path `/C:/Users/me/a.jpg`), which Coil then turns into the path `C:\Users\me\a.jpg`. The path is
 * decoded with [java.net.URI], so names with spaces or accents (`%20`, `%C3%A9`) keep working.
 * Everything else (https urls, `file:///C:/...`, Linux and macOS paths) returns null and is left to Coil untouched.
 *
 * Registered in the desktop `ImageLoader` (DesktopApp.kt). Remove it when the Coil version in use parses this form.
 */
class DriveLetterFileUriMapper : Mapper<String, Uri> {
    override fun map(
        data: String,
        options: Options,
    ): Uri? {
        if (!data.startsWith("file:/", ignoreCase = true)) return null
        val path = runCatching { URI(data).path }.getOrNull() ?: return null
        if (!DRIVE_PATH.matches(path)) return null
        return Uri(scheme = "file", path = path)
    }

    private companion object {
        /** An absolute path that starts with a drive letter: `/C:/...`. */
        val DRIVE_PATH = Regex("^/[A-Za-z]:/.+")
    }
}
