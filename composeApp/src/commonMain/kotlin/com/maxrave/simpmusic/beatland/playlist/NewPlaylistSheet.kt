package com.maxrave.simpmusic.beatland.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.maxrave.simpmusic.ui.component.EndOfModalBottomSheet
import com.maxrave.simpmusic.ui.icon.Close
import com.maxrave.simpmusic.ui.icon.PlaylistAdd
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.bl_newpl_clear
import simpmusic.composeapp.generated.resources.bl_newpl_title
import simpmusic.composeapp.generated.resources.create
import simpmusic.composeapp.generated.resources.create_playlist_description
import simpmusic.composeapp.generated.resources.playlist_name
import simpmusic.composeapp.generated.resources.playlist_name_cannot_be_empty

/** A playlist name longer than this is not accepted by the field. */
private const val MAX_NAME_LENGTH = 100

/**
 * Beatland fork (D-053): the sheet that names a new local playlist.
 *
 * It replaces the old outlined text field with a flat "Create" text button by the same pieces the rest of
 * the app is made of: the sheet shell of the Create sheet, the rounded filled field of the search boxes
 * (tinted fill, leading icon, clear button) and the primary pill button of the song picker.
 *
 * Built for a keyboard as much as for a finger: the field is focused as soon as the sheet opens, Enter
 * creates, an empty name is explained under the field instead of in a toast, and a second Enter or click
 * while the playlist is being created is ignored, so a quick double click can never create two.
 *
 * [initialName] pre-fills the field (the text typed in the add-to-playlist search box, for instance).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPlaylistSheet(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    initialName: String = "",
) {
    val scheme = MaterialTheme.colorScheme
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }
    var field by remember {
        val start = initialName.take(MAX_NAME_LENGTH)
        mutableStateOf(TextFieldValue(start, TextRange(start.length)))
    }
    var focused by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }

    fun submit() {
        if (submitted) return
        val name = field.text.trim()
        if (name.isEmpty()) {
            showError = true
            return
        }
        submitted = true
        onCreate(name)
        coroutineScope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (ignored: IllegalStateException) {
            // The field was not attached yet; the user can still click it.
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = Color.Transparent,
        dragHandle = null,
        scrimColor = Color.Black.copy(alpha = .5f),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
            colors = CardDefaults.cardColors().copy(containerColor = scheme.surfaceContainerHigh),
        ) {
            Column(
                // The sheet is handed no insets, so nothing else lifts the field above the keyboard.
                // Navigation bars are left out: EndOfModalBottomSheet already pads for them.
                modifier = Modifier.windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
            ) {
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .width(60.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(scheme.outline),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = SimpIcons.PlaylistAdd,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.bl_newpl_title),
                            style = typo().titleMedium,
                            color = scheme.onSurface,
                        )
                        Text(
                            text = stringResource(Res.string.create_playlist_description),
                            style = typo().bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                val borderColor = if (showError) scheme.error else scheme.primary
                BasicTextField(
                    value = field,
                    onValueChange = {
                        if (it.text.length <= MAX_NAME_LENGTH) {
                            field = it
                            showError = false
                        }
                    },
                    singleLine = true,
                    textStyle = typo().bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(scheme.onSurface.copy(alpha = 0.08f))
                            .then(
                                if (focused || showError) {
                                    Modifier.border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
                                } else {
                                    Modifier
                                },
                            ).focusRequester(focusRequester)
                            .onFocusChanged { focused = it.isFocused },
                    decorationBox = { innerTextField ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width(16.dp))
                            Icon(
                                imageVector = SimpIcons.PlaylistAdd,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (field.text.isEmpty()) {
                                    Text(
                                        text = stringResource(Res.string.playlist_name),
                                        style = typo().bodyLarge,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                            if (field.text.isEmpty()) {
                                Spacer(modifier = Modifier.width(16.dp))
                            } else {
                                IconButton(onClick = { field = TextFieldValue("") }) {
                                    Icon(
                                        imageVector = SimpIcons.Close,
                                        contentDescription = stringResource(Res.string.bl_newpl_clear),
                                        tint = scheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    },
                )
                if (showError) {
                    Text(
                        text = stringResource(Res.string.playlist_name_cannot_be_empty),
                        style = typo().bodySmall,
                        color = scheme.error,
                        modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = 6.dp),
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = { submit() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = scheme.primary,
                            contentColor = scheme.onPrimary,
                        ),
                ) {
                    Text(
                        text = stringResource(Res.string.create),
                        style = typo().labelSmall,
                        color = scheme.onPrimary,
                    )
                }
                EndOfModalBottomSheet()
            }
        }
    }
}
