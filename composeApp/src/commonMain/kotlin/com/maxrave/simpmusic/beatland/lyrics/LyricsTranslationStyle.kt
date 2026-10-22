package com.maxrave.simpmusic.beatland.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.maxrave.simpmusic.ui.theme.seed
import com.maxrave.simpmusic.ui.theme.typo

/**
 * Beatland fork (D-030): how the TRANSLATION line of a lyric looks, in one place for every lyrics view.
 *
 * Color: the app's own `seed` sky blue, softened a little towards white. It is clearly a different color from the
 * white original, calm on the dark album gradients, and it comes from the theme instead of the pure yellow /
 * mustard of the original app. To try another tone, change [BeatlandTranslationColor] only.
 *
 * Type: the SAME family and style as the original lyric line (the headline style), a notch lighter and
 * about 0.65-0.7 of its size, so it reads as the same voice speaking quieter.
 */
internal val BeatlandTranslationColor: Color = lerp(seed, Color.White, 0.2f)

/** The translation of a line that is not being sung right now. */
internal val BeatlandTranslationDimColor: Color = BeatlandTranslationColor.copy(alpha = 0.38f)

/** Classic lyrics (original is 20-23sp bold): translation 15sp. Apple Music style (original 28sp): pass 19sp. */
@Composable
internal fun beatlandTranslationStyle(fontSize: TextUnit = 15.sp): TextStyle =
    typo().headlineMedium.copy(
        fontSize = fontSize,
        lineHeight = fontSize * 1.4f,
        fontWeight = FontWeight.SemiBold,
    )

/** Translation under the artwork (Spotify / M3 Expressive / Apple idle lines): original is 13sp, this is 12sp. */
@Composable
internal fun beatlandSubtitleTranslationStyle(): TextStyle = typo().bodyMedium.copy(fontSize = 12.sp)
