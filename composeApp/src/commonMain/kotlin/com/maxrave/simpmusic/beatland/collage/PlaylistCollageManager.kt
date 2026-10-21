package com.maxrave.simpmusic.beatland.collage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.maxrave.domain.data.entities.LocalPlaylistEntity
import com.maxrave.domain.repository.LocalPlaylistRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.expect.ui.persistPickedImage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.lastOrNull
import org.koin.compose.koinInject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Beatland fork feature (D-027): keeps a collage of up to four of its songs' covers as the thumbnail of every
 * LOCAL playlist that has no cover of its own, and refreshes it whenever the first songs change.
 *
 * It watches the playlist table instead of hooking each place that adds or removes songs, so it also covers
 * adding from the song menu, the picker, removals, imports and playlists that already existed.
 *
 * What it never touches (the user's choice always wins):
 *  - a playlist with a cover the user picked (`cover_*.jpg`) or any other non-collage thumbnail;
 *  - a playlist synced with YouTube (saving a cover on those uploads it).
 * To get an automatic collage back after setting a cover, the playlist's cover has to be cleared.
 */
@OptIn(FlowPreview::class)
class PlaylistCollageManager(
    private val localPlaylistRepository: LocalPlaylistRepository,
    private val songRepository: SongRepository,
) {
    // playlist id -> file name we already tried this session, so a failing download is not retried on every change.
    private val attempted = HashMap<Long, String>()

    suspend fun run() {
        localPlaylistRepository
            .getAllLocalPlaylists()
            .debounce(DEBOUNCE_MS)
            .collect { playlists ->
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
    }

    private suspend fun refresh(playlist: LocalPlaylistEntity) {
        if (!playlist.youtubePlaylistId.isNullOrBlank()) return
        val current = playlist.thumbnail
        val ours = current?.contains(COLLAGE_MARKER) == true
        if (!(current.isNullOrBlank() || ours)) return // the user (or the source) already chose a cover

        val ids = playlist.tracks.orEmpty().filter { it.isNotBlank() }.take(CANDIDATES)
        if (ids.isEmpty()) return
        val byId = songRepository.getSongsByListVideoId(ids).first().associateBy { it.videoId }
        val urls =
            ids
                .mapNotNull { byId[it]?.thumbnails }
                .filter { it.startsWith("http") }
                .distinct()
                .take(MAX_IMAGES)
        if (urls.isEmpty()) return

        // Named after the content: Coil caches by url, so a new set of covers must be a new file name.
        val fileName = "collage_${playlist.id}_${urls.joinToString("|").hashCode().toUInt()}.jpg"
        if (current != null && current.endsWith(fileName)) return
        if (attempted[playlist.id] == fileName) return
        attempted[playlist.id] = fileName

        val bytes = composePlaylistCollage(urls) ?: return
        val uri = persistPickedImage(bytes, fileName) ?: return
        localPlaylistRepository.updateThumbnailLocalPlaylist(playlist.id, uri, "").lastOrNull()
        if (ours && current != null) deleteCollageFile(current)
    }

    private companion object {
        const val TAG = "PlaylistCollage"
        const val COLLAGE_MARKER = "/collage_"
        const val DEBOUNCE_MS = 2_000L
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
