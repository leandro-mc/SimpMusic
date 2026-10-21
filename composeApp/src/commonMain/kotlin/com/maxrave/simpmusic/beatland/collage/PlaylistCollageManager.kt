package com.maxrave.simpmusic.beatland.collage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.maxrave.domain.data.entities.LocalPlaylistEntity
import com.maxrave.domain.repository.LocalPlaylistRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.expect.ui.persistPickedImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.lastOrNull
import org.koin.compose.koinInject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Beatland fork feature (D-027): keeps a collage of up to four of its songs' covers as the thumbnail of every
 * LOCAL playlist that has no cover of its own, and refreshes it whenever the first songs change.
 *
 * HOW IT WATCHES: the playlist queries of `core` are one-shot (`getAllLocalPlaylists()` reads the table once and
 * completes), so there is nothing to subscribe to. Instead it re-reads the playlists every [POLL_MS] (one cheap
 * query) and only does real work for a playlist whose first songs changed. A change must stay unchanged for one
 * more poll before a collage is built, so adding many songs in a row builds one collage, not one per song.
 * When a collage is written it announces it through [PlaylistCollageEvents] so the open screens reload it.
 *
 * What it never touches (the user's choice always wins):
 *  - a playlist with a cover the user picked (`cover_*.jpg`) or any other non-collage thumbnail;
 *  - a playlist synced with YouTube (saving a cover on those uploads it).
 * To get an automatic collage back after setting a cover, the playlist's cover has to be cleared.
 */
class PlaylistCollageManager(
    private val localPlaylistRepository: LocalPlaylistRepository,
    private val songRepository: SongRepository,
) {
    private class State {
        var signature: String = ""
        var settled: Boolean = false
        var handled: Boolean = false
        var retryInPolls: Int = 0
    }

    private val states = HashMap<Long, State>()

    suspend fun run() {
        while (true) {
            try {
                pass()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(TAG, "Collage pass failed: ${e.message}")
            }
            delay(POLL_MS)
        }
    }

    private suspend fun pass() {
        val playlists = localPlaylistRepository.getAllLocalPlaylists().first()
        val liveIds = playlists.mapTo(HashSet()) { it.id }
        states.keys.retainAll(liveIds)
        playlists.forEach { playlist ->
            try {
                refresh(playlist)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(TAG, "Collage for playlist ${playlist.id} failed: ${e.message}")
            }
        }
    }

    private suspend fun refresh(playlist: LocalPlaylistEntity) {
        if (!playlist.youtubePlaylistId.isNullOrBlank()) return
        val current = playlist.thumbnail
        val ours = current?.contains(COLLAGE_MARKER) == true
        if (!(current.isNullOrBlank() || ours)) {
            states.remove(playlist.id) // the user (or the source) already chose a cover
            return
        }

        val ids = playlist.tracks.orEmpty().filter { it.isNotBlank() }.take(CANDIDATES)
        if (ids.isEmpty()) return
        val signature = ids.joinToString(",")

        val state = states.getOrPut(playlist.id) { State() }
        if (state.signature != signature) {
            // The songs changed (or first sight): let a burst of additions finish before building anything.
            state.signature = signature
            state.settled = false
            state.handled = false
            state.retryInPolls = 0
            return
        }
        state.settled = true
        if (state.handled) return
        if (state.retryInPolls > 0) {
            state.retryInPolls--
            return
        }

        val byId = songRepository.getSongsByListVideoId(ids).first().associateBy { it.videoId }
        val urls =
            ids
                .mapNotNull { byId[it]?.thumbnails }
                .filter { it.startsWith("http") }
                .distinct()
                .take(MAX_IMAGES)
        if (urls.isEmpty()) {
            state.handled = true
            return
        }

        // Named after the content: Coil caches by url, so a new set of covers must be a new file name.
        val fileName = "collage_${playlist.id}_${urls.joinToString("|").hashCode().toUInt()}.jpg"
        if (current != null && current.endsWith(fileName)) {
            state.handled = true
            return
        }

        val uri =
            composePlaylistCollage(urls)?.let { persistPickedImage(it, fileName) }
        if (uri == null) {
            state.retryInPolls = RETRY_POLLS // no connection or a bad image: try again in a while
            return
        }
        localPlaylistRepository.updateThumbnailLocalPlaylist(playlist.id, uri, "").lastOrNull()
        if (ours && current != null) deleteCollageFile(current)
        state.handled = true
        PlaylistCollageEvents.notifyUpdated(playlist.id)
    }

    private companion object {
        const val TAG = "PlaylistCollage"
        const val COLLAGE_MARKER = "/collage_"
        const val POLL_MS = 3_000L
        const val RETRY_POLLS = 10
        const val CANDIDATES = 12
        const val MAX_IMAGES = 4
    }
}

/** Starts the [PlaylistCollageManager] for as long as the app UI is on screen. Place once, high in the tree. */
@Composable
fun PlaylistCollageEffect(
    localPlaylistRepository: LocalPlaylistRepository = koinInject(),
    songRepository: SongRepository = koinInject(),
) {
    LaunchedEffect(Unit) {
        PlaylistCollageManager(localPlaylistRepository, songRepository).run()
    }
}
