package com.maxrave.simpmusic.beatland.collage

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withTimeoutOrNull

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

    // A conflated channel instead of a SharedFlow: a request that arrives while the manager is busy is KEPT and the next
    // wait returns at once. The SharedFlow version dropped requests that came when nobody was waiting, so on Windows the
    // collage waited for the next scheduled check (about 15 s) instead of appearing right after adding songs.
    private val wakeChannel = Channel<Boolean>(Channel.CONFLATED)

    /**
     * Ask the collage manager to look at the playlists now instead of waiting for its next scheduled check.
     * [songsAreFinal] = true when every song of the change is already in the playlist (the song picker, once it has
     * added them all), so the manager can build the collage at once instead of waiting for further changes.
     */
    fun requestCheck(songsAreFinal: Boolean = true) {
        wakeChannel.trySend(songsAreFinal)
    }

    /** Waits up to [timeoutMs] for [requestCheck]. Returns its `songsAreFinal` flag, or false when it just timed out. */
    suspend fun awaitCheckRequest(timeoutMs: Long): Boolean = withTimeoutOrNull(timeoutMs) { wakeChannel.receive() } ?: false
}
