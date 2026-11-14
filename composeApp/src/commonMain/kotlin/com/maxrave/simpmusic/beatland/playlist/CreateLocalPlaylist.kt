package com.maxrave.simpmusic.beatland.playlist

import com.maxrave.domain.data.entities.LocalPlaylistEntity
import com.maxrave.domain.repository.LocalPlaylistRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.lastOrNull
import org.jetbrains.compose.resources.getString
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.added_local_playlist

/**
 * Beatland fork (D-053): creates an empty local playlist named [title] and returns it, or null if it
 * could not be found afterwards. Used by "Add to playlist → New playlist", which has to add the song to
 * the playlist it has just created.
 *
 * `insertLocalPlaylist` only reports a message, not the new row, so the playlist is found by diffing the
 * list before and after, exactly as `SharedViewModel.createLocalPlaylistAndPickSongs` does.
 */
suspend fun LocalPlaylistRepository.createLocalPlaylistAndGet(title: String): LocalPlaylistEntity? {
    val before = getAllLocalPlaylists().first().mapTo(HashSet()) { it.id }
    insertLocalPlaylist(LocalPlaylistEntity(title = title), getString(Res.string.added_local_playlist)).lastOrNull()
    return getAllLocalPlaylists().first().firstOrNull { it.id !in before }
}
