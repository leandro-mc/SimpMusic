package com.maxrave.simpmusic.beatland.collage

import com.maxrave.logger.Logger

/**
 * Beatland fork (D-043): says in the app log how the cover of a LOCAL playlist (a `file:` thumbnail, such as the
 * automatic collage) was drawn by the grid, which is the one step the collage manager's own log cannot see.
 *
 * On Windows the manager reported "Collage saved" for every playlist while the Library still showed the default tile, so
 * the question is no longer whether the picture exists but whether the image loader could open and decode it. Each
 * outcome is written once per playlist and thumbnail, at INFO level (the log screen's default filter hides warnings),
 * under the same `PlaylistCollage` tag as the rest of the feature.
 *
 * Only `file:` thumbnails are reported; YouTube covers (https) are not this feature's business.
 */
internal object PlaylistCoverDiagnostics {
    private const val TAG = "PlaylistCollage"
    private val reported = HashSet<String>()

    fun loaded(
        playlistId: Long,
        thumbnail: String?,
        width: Int,
        height: Int,
    ) {
        if (thumbnail?.startsWith("file:") != true) return
        if (!reported.add("$playlistId|$thumbnail|ok")) return
        Logger.i(TAG, "Cover of playlist $playlistId LOADED by the grid: ${width}x$height ($thumbnail)")
    }

    fun failed(
        playlistId: Long,
        thumbnail: String?,
        error: Throwable,
    ) {
        if (thumbnail?.startsWith("file:") != true) return
        if (!reported.add("$playlistId|$thumbnail|err")) return
        Logger.i(
            TAG,
            "Cover of playlist $playlistId FAILED in the grid: ${error::class.simpleName}: ${error.message} " +
                "| uri=$thumbnail | ${describeLocalFile(thumbnail)}",
        )
    }
}
