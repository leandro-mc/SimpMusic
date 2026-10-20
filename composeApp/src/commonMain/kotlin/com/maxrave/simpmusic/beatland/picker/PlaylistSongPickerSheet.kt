package com.maxrave.simpmusic.beatland.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.simpmusic.ui.component.rememberHolderPainter
import com.maxrave.simpmusic.ui.icon.Close
import com.maxrave.simpmusic.ui.icon.Search
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.bl_picker_add_count
import simpmusic.composeapp.generated.resources.bl_picker_add_none
import simpmusic.composeapp.generated.resources.bl_picker_adding
import simpmusic.composeapp.generated.resources.bl_picker_already_in
import simpmusic.composeapp.generated.resources.bl_picker_clear
import simpmusic.composeapp.generated.resources.bl_picker_clear_search
import simpmusic.composeapp.generated.resources.bl_picker_done
import simpmusic.composeapp.generated.resources.bl_picker_empty
import simpmusic.composeapp.generated.resources.bl_picker_in_library
import simpmusic.composeapp.generated.resources.bl_picker_no_results
import simpmusic.composeapp.generated.resources.bl_picker_online
import simpmusic.composeapp.generated.resources.bl_picker_search_error
import simpmusic.composeapp.generated.resources.bl_picker_search_hint
import simpmusic.composeapp.generated.resources.bl_picker_searching
import simpmusic.composeapp.generated.resources.bl_picker_select_all
import simpmusic.composeapp.generated.resources.bl_picker_skip
import simpmusic.composeapp.generated.resources.bl_picker_source_liked
import simpmusic.composeapp.generated.resources.bl_picker_source_most_played
import simpmusic.composeapp.generated.resources.bl_picker_source_recent
import simpmusic.composeapp.generated.resources.bl_picker_title

/**
 * Beatland fork feature: full-height sheet to fill a playlist without leaving it. Shown right after a
 * playlist is created and from the "Add songs" row of a local playlist.
 *
 * Pick from Recent / Most played / Liked, or type to search your library and YouTube Music at once.
 * There is no selection cap here (the cap in the normal multi-select only guards bulk actions that
 * fire network requests; see docs/beatland/DECISIONS.md D-023).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSongPickerSheet(
    playlistId: Long,
    playlistTitle: String,
    onDismiss: () -> Unit,
    viewModel: PlaylistSongPickerViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(playlistId) { viewModel.start(playlistId) }

    fun close() {
        viewModel.reset()
        onDismiss()
    }

    val selectedIds = state.selectedIds
    val visibleSongs =
        remember(state.library, state.online.items, state.isSearching) {
            if (state.isSearching) {
                (state.library + state.online.items).distinctBy { it.videoId }
            } else {
                state.library
            }
        }

    ModalBottomSheet(
        onDismissRequest = { close() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.94f),
        ) {
            // Header
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.bl_picker_title, playlistTitle),
                    style = typo().titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { close() }) {
                    Text(
                        text =
                            stringResource(
                                if (state.selected.isEmpty()) Res.string.bl_picker_skip else Res.string.bl_picker_done,
                            ),
                    )
                }
            }
            // Search
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    viewModel.setQuery(it)
                },
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.bl_picker_search_hint)) },
                leadingIcon = { Icon(imageVector = SimpIcons.Search, contentDescription = null) },
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                text = ""
                                viewModel.setQuery("")
                            },
                        ) {
                            Icon(
                                imageVector = SimpIcons.Close,
                                contentDescription = stringResource(Res.string.bl_picker_clear_search),
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            // Source chips + select all
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!state.isSearching) {
                        PickerSource.entries.forEach { src ->
                            FilterChip(
                                selected = state.source == src,
                                onClick = { viewModel.setSource(src) },
                                label = {
                                    Text(
                                        stringResource(
                                            when (src) {
                                                PickerSource.RECENT -> Res.string.bl_picker_source_recent
                                                PickerSource.MOST_PLAYED -> Res.string.bl_picker_source_most_played
                                                PickerSource.LIKED -> Res.string.bl_picker_source_liked
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
                if (visibleSongs.isNotEmpty()) {
                    TextButton(onClick = { viewModel.toggleAll(visibleSongs) }) {
                        Text(stringResource(Res.string.bl_picker_select_all))
                    }
                }
            }
            HorizontalDivider()
            // List
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                if (!state.isSearching) {
                    if (state.library.isEmpty()) {
                        item(key = "empty") { InfoText(stringResource(Res.string.bl_picker_empty)) }
                    }
                    items(state.library, key = { "lib_" + it.videoId }) { song ->
                        PickerSongRow(
                            song = song,
                            checked = selectedIds.contains(song.videoId),
                            inPlaylist = state.alreadyInPlaylist.contains(song.videoId),
                            onClick = { viewModel.toggle(song) },
                        )
                    }
                } else {
                    if (state.library.isNotEmpty()) {
                        item(key = "hdr_lib") { SectionHeader(stringResource(Res.string.bl_picker_in_library)) }
                        items(state.library, key = { "lib_" + it.videoId }) { song ->
                            PickerSongRow(
                                song = song,
                                checked = selectedIds.contains(song.videoId),
                                inPlaylist = state.alreadyInPlaylist.contains(song.videoId),
                                onClick = { viewModel.toggle(song) },
                            )
                        }
                    }
                    item(key = "hdr_online") { SectionHeader(stringResource(Res.string.bl_picker_online)) }
                    val libraryIds = state.library.mapTo(HashSet()) { it.videoId }
                    val onlineOnly = state.online.items.filterNot { libraryIds.contains(it.videoId) }
                    when {
                        state.online.loading -> {
                            item(key = "online_loading") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(Res.string.bl_picker_searching),
                                        style = typo().bodyMedium,
                                    )
                                }
                            }
                        }
                        state.online.error -> {
                            item(key = "online_error") { InfoText(stringResource(Res.string.bl_picker_search_error)) }
                        }
                        onlineOnly.isEmpty() && state.library.isEmpty() -> {
                            item(key = "online_empty") { InfoText(stringResource(Res.string.bl_picker_no_results)) }
                        }
                        else -> {
                            items(onlineOnly, key = { "on_" + it.videoId }) { song ->
                                PickerSongRow(
                                    song = song,
                                    checked = selectedIds.contains(song.videoId),
                                    inPlaylist = state.alreadyInPlaylist.contains(song.videoId),
                                    onClick = { viewModel.toggle(song) },
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            // Confirm bar
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { viewModel.confirm { close() } },
                    enabled = state.selected.isNotEmpty() && !state.isAdding,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    if (state.isAdding) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(stringResource(Res.string.bl_picker_adding))
                    } else if (state.selected.isEmpty()) {
                        Text(stringResource(Res.string.bl_picker_add_none))
                    } else {
                        Text(stringResource(Res.string.bl_picker_add_count, state.selected.size.toString()))
                    }
                }
                if (state.selected.isNotEmpty() && !state.isAdding) {
                    TextButton(onClick = { viewModel.clearSelection() }) {
                        Text(stringResource(Res.string.bl_picker_clear))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = typo().titleSmall,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun InfoText(text: String) {
    Text(
        text = text,
        style = typo().bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
    )
}

@Composable
private fun PickerSongRow(
    song: SongEntity,
    checked: Boolean,
    inPlaylist: Boolean,
    onClick: () -> Unit,
) {
    val subtitle =
        listOfNotNull(
            song.artistName?.filter { it.isNotBlank() }?.joinToString(", ")?.takeIf { it.isNotBlank() },
            song.duration.takeIf { it.isNotBlank() },
        ).joinToString(" • ")
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !inPlaylist, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked || inPlaylist,
            onCheckedChange = null,
            enabled = !inPlaylist,
        )
        Spacer(modifier = Modifier.width(12.dp))
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalPlatformContext.current)
                    .data(song.thumbnails)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .diskCacheKey(song.thumbnails)
                    .crossfade(true)
                    .build(),
            placeholder = rememberHolderPainter(),
            error = rememberHolderPainter(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = typo().titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = typo().bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (inPlaylist) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(Res.string.bl_picker_already_in),
                style = typo().labelSmall,
            )
        }
    }
}
