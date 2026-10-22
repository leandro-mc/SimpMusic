package com.maxrave.simpmusic.beatland.picker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.simpmusic.ui.component.Chip
import com.maxrave.simpmusic.ui.component.SongFullWidthItems
import com.maxrave.simpmusic.ui.component.rememberSurfaceDarkColors
import com.maxrave.simpmusic.ui.icon.Close
import com.maxrave.simpmusic.ui.icon.Pause
import com.maxrave.simpmusic.ui.icon.PlayArrow
import com.maxrave.simpmusic.ui.icon.Search
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.bl_picker_add_count
import simpmusic.composeapp.generated.resources.bl_picker_adding
import simpmusic.composeapp.generated.resources.bl_picker_already_in
import simpmusic.composeapp.generated.resources.bl_picker_clear
import simpmusic.composeapp.generated.resources.bl_picker_clear_search
import simpmusic.composeapp.generated.resources.bl_picker_empty
import simpmusic.composeapp.generated.resources.bl_picker_in_library
import simpmusic.composeapp.generated.resources.bl_picker_no_results
import simpmusic.composeapp.generated.resources.bl_picker_online
import simpmusic.composeapp.generated.resources.bl_picker_pause
import simpmusic.composeapp.generated.resources.bl_picker_preview
import simpmusic.composeapp.generated.resources.bl_picker_search_error
import simpmusic.composeapp.generated.resources.bl_picker_search_hint
import simpmusic.composeapp.generated.resources.bl_picker_searching
import simpmusic.composeapp.generated.resources.bl_picker_select_all
import simpmusic.composeapp.generated.resources.bl_picker_source_liked
import simpmusic.composeapp.generated.resources.bl_picker_source_most_played
import simpmusic.composeapp.generated.resources.bl_picker_source_recent
import simpmusic.composeapp.generated.resources.bl_picker_title

/**
 * Beatland fork feature: full-height sheet to fill a playlist without leaving it. Shown right after a
 * playlist is created and from the "Add songs" row of a local playlist.
 *
 * UX rule (docs/beatland/RULES.md): this screen is built ONLY from the app's own pieces so it reads as
 * part of it — the sheet shell of `SelectedSongsBottomSheet`/the add-to-playlist sheet, the pill search
 * field of the latter, [Chip], [SongFullWidthItems] in selection mode (its circle indicator and row tint)
 * and [rememberSurfaceDarkColors] for every surface/text colour. No ad-hoc colours.
 *
 * Pick from Recent / Most played / Liked, or type to search your library and YouTube Music at once.
 * There is no selection cap here (D-023/D-024).
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
    val colors = rememberSurfaceDarkColors()

    val playingId by viewModel.nowPlayingVideoId.collectAsStateWithLifecycle()

    LaunchedEffect(playlistId) { viewModel.start(playlistId, playlistTitle) }

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
        containerColor = Color.Transparent,
        contentColor = Color.Transparent,
        dragHandle = null,
        scrimColor = Color.Black.copy(alpha = .5f),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Card(
            modifier =
                Modifier
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                    .fillMaxWidth()
                    .fillMaxHeight(),
            shape = BottomSheetDefaults.ExpandedShape,
            colors = CardDefaults.cardColors().copy(containerColor = colors.container),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        // The sheet is handed no insets: lift the list above the keyboard (nav bars are
                        // handled right below) — same as the add-to-playlist sheet.
                        .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
                        .navigationBarsPadding(),
            ) {
                Spacer(modifier = Modifier.height(5.dp))
                Card(
                    modifier = Modifier.width(60.dp).height(4.dp),
                    colors = CardDefaults.cardColors().copy(containerColor = colors.handle),
                    shape = RoundedCornerShape(50),
                ) {}
                Spacer(modifier = Modifier.height(5.dp))
                // Title + close
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.bl_picker_title, playlistTitle),
                        style = typo().titleMedium,
                        color = colors.content,
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { close() }) {
                        Icon(
                            imageVector = SimpIcons.Close,
                            contentDescription = null,
                            tint = colors.subtitle,
                        )
                    }
                }
                // Search: the app's pill field
                BasicTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        viewModel.setQuery(it)
                    },
                    singleLine = true,
                    textStyle = typo().labelSmall.copy(color = colors.content),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 4.dp)
                            .height(40.dp)
                            .clip(CircleShape)
                            .background(colors.content.copy(alpha = 0.08f)),
                    decorationBox = { innerTextField ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Icon(
                                imageVector = SimpIcons.Search,
                                contentDescription = null,
                                tint = colors.subtitle,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (text.isEmpty()) {
                                    Text(
                                        text = stringResource(Res.string.bl_picker_search_hint),
                                        style = typo().labelSmall,
                                        color = colors.subtitle,
                                    )
                                }
                                innerTextField()
                            }
                            if (text.isEmpty()) {
                                Spacer(modifier = Modifier.width(12.dp))
                            } else {
                                IconButton(
                                    onClick = {
                                        text = ""
                                        viewModel.setQuery("")
                                    },
                                ) {
                                    Icon(
                                        imageVector = SimpIcons.Close,
                                        contentDescription = stringResource(Res.string.bl_picker_clear_search),
                                        tint = colors.subtitle,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    },
                )
                // Source chips (the app's own Chip) + select all
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!state.isSearching) {
                            PickerSource.entries.forEach { src ->
                                Chip(
                                    isSelected = state.source == src,
                                    text =
                                        stringResource(
                                            when (src) {
                                                PickerSource.RECENT -> Res.string.bl_picker_source_recent
                                                PickerSource.MOST_PLAYED -> Res.string.bl_picker_source_most_played
                                                PickerSource.LIKED -> Res.string.bl_picker_source_liked
                                            },
                                        ),
                                    onClick = { viewModel.setSource(src) },
                                )
                            }
                        }
                    }
                    if (visibleSongs.isNotEmpty()) {
                        TextButton(onClick = { viewModel.toggleAll(visibleSongs) }) {
                            Text(
                                text = stringResource(Res.string.bl_picker_select_all),
                                style = typo().labelSmall,
                                color = colors.content,
                            )
                        }
                    }
                }
                // List
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    if (!state.isSearching) {
                        if (state.library.isEmpty()) {
                            item(key = "empty") { InfoText(stringResource(Res.string.bl_picker_empty)) }
                        }
                        items(state.library, key = { "lib_" + it.videoId }) { song ->
                            PickerRow(song, selectedIds, state.alreadyInPlaylist, playingId, viewModel)
                        }
                    } else {
                        if (state.library.isNotEmpty()) {
                            item(key = "hdr_lib") { SectionHeader(stringResource(Res.string.bl_picker_in_library)) }
                            items(state.library, key = { "lib_" + it.videoId }) { song ->
                                PickerRow(song, selectedIds, state.alreadyInPlaylist, playingId, viewModel)
                            }
                        }
                        item(key = "hdr_online") { SectionHeader(stringResource(Res.string.bl_picker_online)) }
                        val libraryIds = state.library.mapTo(HashSet()) { it.videoId }
                        val onlineOnly = state.online.items.filterNot { libraryIds.contains(it.videoId) }
                        when {
                            state.online.loading -> {
                                item(key = "online_loading") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = stringResource(Res.string.bl_picker_searching),
                                            style = typo().bodySmall,
                                            color = colors.subtitle,
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
                                    PickerRow(song, selectedIds, state.alreadyInPlaylist, playingId, viewModel)
                                }
                            }
                        }
                    }
                }
                // Confirm bar: only exists while something is selected, so there is never a disabled
                // (washed-out) button on screen.
                AnimatedVisibility(visible = state.selected.isNotEmpty() || state.isAdding) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { viewModel.confirm { close() } },
                            enabled = !state.isAdding,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                        ) {
                            if (state.isAdding) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Text(
                                text =
                                    if (state.isAdding) {
                                        stringResource(Res.string.bl_picker_adding)
                                    } else {
                                        stringResource(Res.string.bl_picker_add_count, state.selected.size.toString())
                                    },
                                style = typo().labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                        if (!state.isAdding) {
                            TextButton(onClick = { viewModel.clearSelection() }) {
                                Text(
                                    text = stringResource(Res.string.bl_picker_clear),
                                    style = typo().labelSmall,
                                    color = colors.content,
                                )
                            }
                        }
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
        color = rememberSurfaceDarkColors().content,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun InfoText(text: String) {
    Text(
        text = text,
        style = typo().bodySmall,
        color = rememberSurfaceDarkColors().subtitle,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
    )
}

/**
 * One song, drawn by the app's own [SongFullWidthItems] in selection mode (circle indicator + row tint).
 * The preview button sits at the far RIGHT edge — opposite the selection circle on the left, which is
 * where a tap means "pick" — so a quick tap on the row can never start playback by mistake.
 */
@Composable
private fun PickerRow(
    song: SongEntity,
    selectedIds: Set<String>,
    alreadyInPlaylist: Set<String>,
    playingId: String,
    viewModel: PlaylistSongPickerViewModel,
) {
    val inPlaylist = alreadyInPlaylist.contains(song.videoId)
    val isPlayingThis = playingId == song.videoId
    val colors = rememberSurfaceDarkColors()
    val right: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (inPlaylist) {
                Text(
                    text = stringResource(Res.string.bl_picker_already_in),
                    style = typo().labelSmall,
                    color = colors.subtitle,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            PreviewButton(playing = isPlayingThis, onClick = { viewModel.togglePreview(song) })
        }
    }
    SongFullWidthItems(
        songEntity = song,
        isPlaying = isPlayingThis,
        selectionMode = true,
        isSelected = inPlaylist || selectedIds.contains(song.videoId),
        onSelectToggle = { if (!inPlaylist) viewModel.toggle(song) },
        modifier = Modifier.fillMaxWidth().alpha(if (inPlaylist) 0.55f else 1f),
        rightView = right,
    )
}

/** Small round outlined play button; filled with the app's primary pair while its song is playing. */
@Composable
private fun PreviewButton(
    playing: Boolean,
    onClick: () -> Unit,
) {
    val colors = rememberSurfaceDarkColors()
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .padding(start = 4.dp, end = 2.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(if (playing) scheme.primary else Color.Transparent)
                .border(1.dp, if (playing) scheme.primary else colors.subtitle.copy(alpha = 0.5f), CircleShape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (playing) SimpIcons.Pause else SimpIcons.PlayArrow,
            contentDescription =
                stringResource(if (playing) Res.string.bl_picker_pause else Res.string.bl_picker_preview),
            tint = if (playing) scheme.onPrimary else colors.content,
            modifier = Modifier.size(20.dp),
        )
    }
}
