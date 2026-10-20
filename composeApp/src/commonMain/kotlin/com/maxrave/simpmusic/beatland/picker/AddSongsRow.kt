package com.maxrave.simpmusic.beatland.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.maxrave.simpmusic.ui.icon.Add
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.LocalForceDarkText
import com.maxrave.simpmusic.ui.theme.typo
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.bl_add_songs
import simpmusic.composeapp.generated.resources.bl_add_songs_hint

/**
 * Beatland fork feature: the entry point to [PlaylistSongPickerSheet] from inside a local playlist.
 * It sits above the track list, so it is there for empty playlists too.
 *
 * It is laid out and coloured exactly like a song row of that list (same paddings, 48dp rounded
 * thumbnail slot, title/subtitle styles and the same forced-dark text colours the playlist screen uses
 * over its gradient), so it reads as one more row rather than as a foreign widget.
 */
@Composable
fun AddSongsRow(onClick: () -> Unit) {
    val forceDark = LocalForceDarkText.current
    val contentColor = if (forceDark) Color.White else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (forceDark) Color(0xC4FFFFFF) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp, horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(contentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SimpIcons.Add,
                contentDescription = null,
                tint = contentColor,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(Res.string.bl_add_songs),
                style = typo().titleSmall,
                color = contentColor,
            )
            Text(
                text = stringResource(Res.string.bl_add_songs_hint),
                style = typo().bodySmall,
                color = subtitleColor,
            )
        }
    }
}
