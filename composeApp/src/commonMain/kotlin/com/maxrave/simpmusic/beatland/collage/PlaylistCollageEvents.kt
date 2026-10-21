package com.maxrave.simpmusic.beatland.collage

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Beatland fork (D-027): tells the screens that a playlist's collage thumbnail was just written.
 *
 * Why this exists: the playlist queries of the `core` repository are one-shot (`getAllLocalPlaylists()` reads the
 * table once and completes), so neither the playlist screen nor the library list can "just observe the database".
 * They listen to this instead and reload their thumbnail.
 */
object PlaylistCollageEvents {
    private val _updated = MutableSharedFlow<Long>(extraBufferCapacity = 32)

    /** Emits the id of each playlist whose thumbnail changed. */
    val updated: SharedFlow<Long> = _updated

    fun notifyUpdated(playlistId: Long) {
        _updated.tryEmit(playlistId)
    }
}
