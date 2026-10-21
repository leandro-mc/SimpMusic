package com.maxrave.simpmusic.beatland.picker

import androidx.lifecycle.viewModelScope
import com.maxrave.common.Config
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.mediaservice.handler.PlayerEvent
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.repository.LocalPlaylistRepository
import com.maxrave.domain.repository.SearchRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.collectResource
import com.maxrave.domain.utils.toSongEntity
import com.maxrave.domain.utils.toTrack
import com.maxrave.simpmusic.brand.Brand
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.bl_picker_added

/** Where the picker takes its default list from while the search box is empty. */
enum class PickerSource { RECENT, MOST_PLAYED, LIKED }

/**
 * Beatland fork feature: after creating a playlist (or from an existing one) the user picks songs from
 * their history / most played / liked list, or searches, instead of hunting for each song through its
 * artist or album page. See docs/beatland/FEATURES.md.
 *
 * Everything is a [SongEntity]: library rows already are, and online results are converted with the same
 * mappers the search screen uses. `addTrackToLocalPlaylist` inserts a song that is not in the database yet,
 * so search results can be added directly.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class PlaylistSongPickerViewModel(
    private val songRepository: SongRepository,
    private val searchRepository: SearchRepository,
    private val localPlaylistRepository: LocalPlaylistRepository,
) : BaseViewModel() {
    data class OnlineState(
        val items: List<SongEntity> = emptyList(),
        val loading: Boolean = false,
        val error: Boolean = false,
    )

    data class UiState(
        val source: PickerSource = PickerSource.RECENT,
        val query: String = "",
        /** Default list for [source] while the query is blank; local matches while searching. */
        val library: List<SongEntity> = emptyList(),
        val online: OnlineState = OnlineState(),
        val selected: List<SongEntity> = emptyList(),
        val alreadyInPlaylist: Set<String> = emptySet(),
        val isAdding: Boolean = false,
    ) {
        val isSearching: Boolean get() = query.isNotBlank()
        val selectedIds: Set<String> get() = selected.mapTo(HashSet()) { it.videoId }
    }

    private var playlistId: Long = NO_PLAYLIST
    private var playlistTitle: String = ""

    private val source = MutableStateFlow(PickerSource.RECENT)
    private val query = MutableStateFlow("")
    private val selected = MutableStateFlow<List<SongEntity>>(emptyList())
    private val alreadyIn = MutableStateFlow<Set<String>>(emptySet())
    private val adding = MutableStateFlow(false)

    private val allSongs: Flow<List<SongEntity>> =
        songRepository
            .getAllSongs(LOCAL_LIMIT)
            .map { list -> list.filterNot { it.inLibrary == Config.REMOVED_SONG_DATE_TIME } }
    private val mostPlayed: Flow<List<SongEntity>> = songRepository.getMostPlayedSongs()
    private val liked: Flow<List<SongEntity>> = songRepository.getLikedSongs()

    private val libraryFlow: Flow<Triple<PickerSource, String, List<SongEntity>>> =
        combine(allSongs, mostPlayed, liked, source, query) { all, most, likedSongs, src, q ->
            val trimmed = q.trim()
            val list =
                if (trimmed.isNotEmpty()) {
                    all.filter { it.matches(trimmed) }.take(SEARCH_LOCAL_RESULTS)
                } else {
                    when (src) {
                        PickerSource.RECENT -> all.take(RECENT_COUNT)
                        PickerSource.MOST_PLAYED -> most
                        PickerSource.LIKED -> likedSongs
                    }
                }
            Triple(src, q, list)
        }

    private val onlineFlow: Flow<OnlineState> =
        query
            .debounce(SEARCH_DEBOUNCE_MS)
            .map { it.trim() }
            .distinctUntilChanged()
            .flatMapLatest { q ->
                if (q.length < MIN_ONLINE_QUERY) {
                    flowOf(OnlineState())
                } else {
                    searchRepository
                        .getSearchDataSong(q)
                        .map { result ->
                            when (result) {
                                is Resource.Success ->
                                    OnlineState(
                                        items =
                                            result.data
                                                .orEmpty()
                                                .mapNotNull { runCatching { it.toTrack().toSongEntity() }.getOrNull() }
                                                .filter { it.videoId.isNotBlank() }
                                                .distinctBy { it.videoId },
                                    )
                                is Resource.Error -> OnlineState(error = true)
                                else -> OnlineState(loading = true)
                            }
                        }.onStart { emit(OnlineState(loading = true)) }
                }
            }
            // Without this the combined UI state would wait for the first debounce tick (400 ms) to appear.
            .onStart { emit(OnlineState()) }

    val uiState: StateFlow<UiState> =
        combine(libraryFlow, onlineFlow, selected, alreadyIn, adding) { lib, online, sel, already, isAdding ->
            UiState(
                source = lib.first,
                query = lib.second,
                library = lib.third,
                // Not searching (or the query changed and the debounce has not fired yet): hide stale results.
                online = if (lib.second.isBlank()) OnlineState() else online,
                selected = sel,
                alreadyInPlaylist = already,
                isAdding = isAdding,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /** Call when the picker opens for [id]: resets the previous session and reads what is already in it. */
    fun start(
        id: Long,
        title: String = "",
    ) {
        if (playlistId == id) return
        playlistId = id
        playlistTitle = title
        source.value = PickerSource.RECENT
        query.value = ""
        selected.value = emptyList()
        alreadyIn.value = emptySet()
        adding.value = false
        viewModelScope.launch {
            alreadyIn.value = localPlaylistRepository.getListTrackVideoId(id).toSet()
        }
    }

    /** Forgets the session so the next [start] for the same playlist begins clean. */
    fun reset() {
        playlistId = NO_PLAYLIST
        selected.value = emptyList()
        query.value = ""
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSource(value: PickerSource) {
        source.value = value
    }

    fun toggle(song: SongEntity) {
        if (song.videoId.isBlank() || alreadyIn.value.contains(song.videoId)) return
        val current = selected.value
        selected.value =
            if (current.any { it.videoId == song.videoId }) {
                current.filterNot { it.videoId == song.videoId }
            } else {
                current + song
            }
    }

    /** Selects every song in [songs] that is not already picked or in the playlist — or clears them if all are. */
    fun toggleAll(songs: List<SongEntity>) {
        val candidates = songs.filterNot { alreadyIn.value.contains(it.videoId) || it.videoId.isBlank() }.distinctBy { it.videoId }
        if (candidates.isEmpty()) return
        val pickedIds = selected.value.mapTo(HashSet()) { it.videoId }
        selected.value =
            if (candidates.all { pickedIds.contains(it.videoId) }) {
                val ids = candidates.mapTo(HashSet()) { it.videoId }
                selected.value.filterNot { ids.contains(it.videoId) }
            } else {
                selected.value + candidates.filterNot { pickedIds.contains(it.videoId) }
            }
    }

    fun clearSelection() {
        selected.value = emptyList()
    }

    /**
     * Plays [song] right away (or pauses it if it is the one playing) so the user can decide before adding it.
     * Same sequence the search screen uses for a tapped song: it replaces the current queue, exactly like
     * tapping the song anywhere else in the app.
     */
    fun togglePreview(song: SongEntity) {
        if (song.videoId.isBlank()) return
        if (nowPlayingVideoId.value == song.videoId) {
            viewModelScope.launch { mediaPlayerHandler.onPlayerEvent(PlayerEvent.PlayPause) }
            return
        }
        val track = song.toTrack()
        setQueueData(
            QueueData.Data(
                listTracks = arrayListOf(track),
                firstPlayedTrack = track,
                playlistId = "RDAMVM${track.videoId}",
                playlistName = playlistTitle.ifBlank { Brand.NAME },
                playlistType = PlaylistType.RADIO,
                continuation = null,
            ),
        )
        loadMediaItem(track, Config.SONG_CLICK)
    }

    /** Adds every picked song, one after the other, then reports how many made it in. */
    fun confirm(onDone: (added: Int) -> Unit) {
        val id = playlistId
        if (id == NO_PLAYLIST || adding.value) return
        val toAdd = selected.value.filterNot { alreadyIn.value.contains(it.videoId) }
        if (toAdd.isEmpty()) {
            onDone(0)
            return
        }
        viewModelScope.launch {
            adding.value = true
            var added = 0
            toAdd.forEach { song ->
                var ok = false
                localPlaylistRepository
                    .addTrackToLocalPlaylist(
                        id = id,
                        song = song,
                        successMessage = "",
                        updatedYtMessage = "",
                        errorMessage = "",
                    ).collectResource(
                        onSuccess = { ok = true },
                        onError = { log("Could not add ${song.videoId}: $it") },
                    )
                if (ok) added++
            }
            adding.value = false
            alreadyIn.value = alreadyIn.value + toAdd.map { it.videoId }
            selected.value = emptyList()
            makeToast(
                org.jetbrains.compose.resources
                    .getString(Res.string.bl_picker_added, added.toString()),
            )
            onDone(added)
        }
    }

    private fun SongEntity.matches(q: String): Boolean =
        title.contains(q, ignoreCase = true) ||
            (artistName?.any { it.contains(q, ignoreCase = true) } == true) ||
            (albumName?.contains(q, ignoreCase = true) == true)

    companion object {
        private const val NO_PLAYLIST = -1L
        private const val LOCAL_LIMIT = 5000
        private const val RECENT_COUNT = 200
        private const val SEARCH_LOCAL_RESULTS = 100
        private const val SEARCH_DEBOUNCE_MS = 400L
        private const val MIN_ONLINE_QUERY = 2
    }
}
