package com.maxrave.simpmusic.floatinglyrics

/*
 * ─────────────────────────────────────────────────────────────────────────
 *  BEATLAND — Floating Lyrics window (personal fork feature)
 * ─────────────────────────────────────────────────────────────────────────
 * Everything in the `com.maxrave.simpmusic.floatinglyrics` package is NEW code, added on top of
 * stock SimpMusic. It does not modify any existing file. See docs/beatland/FEATURES.md.
 *
 * Three window states, Musixmatch-style: BUBBLE (small always-on-top circle), MINI (a narrow bar
 * for one-handed multitasking) and EXPANDED (the full scrollable card). Visuals lean on the app's
 * own design language rather than inventing a new one: the background gradient uses the same
 * `rememberNowPlayingGlowTint` + `angledGradientBackground` recipe Home/Now-Playing already use to
 * tint themselves from the current artwork, and the lyrics themselves are rendered by the app's
 * own `LyricsView` / `RichSyncLyricsLineItem` — this file only supplies the chrome around them.
 */

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maxrave.domain.data.model.streams.TimeLine
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.simpmusic.brand.Brand
import com.maxrave.simpmusic.extension.ParsedRichSyncLine
import com.maxrave.simpmusic.extension.angledGradientBackground
import com.maxrave.simpmusic.extension.parseRichSyncWords
import com.maxrave.simpmusic.extension.rgbFactor
import com.maxrave.simpmusic.ui.component.LyricsView
import com.maxrave.simpmusic.ui.component.RichSyncLyricsLineItem
import com.maxrave.simpmusic.ui.component.rememberNowPlayingGlowTint
import com.maxrave.simpmusic.ui.icon.Album
import com.maxrave.simpmusic.ui.icon.Close
import com.maxrave.simpmusic.ui.icon.Delete
import com.maxrave.simpmusic.ui.icon.DragHandle
import com.maxrave.simpmusic.ui.icon.Fullscreen
import com.maxrave.simpmusic.ui.icon.FullscreenExit
import com.maxrave.simpmusic.ui.icon.Settings
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.AppTheme
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.NowPlayingScreenData
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.maxrave.simpmusic.viewModel.UIEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector

/** The three window states, matching Musixmatch's own floating bubble. */
enum class FloatingLyricsMode {
    BUBBLE,
    MINI,
    EXPANDED,
}

object FloatingLyricsSizes {
    val bubbleSize = 60.dp
    val miniWidth = 300.dp

    /** Real mini height is dynamic (content-driven) — this is only used for the service's
     *  center-anchored resize math when switching modes, not a hard constraint on the content. */
    val miniApproxHeight = 108.dp
    val expandedWidth = 340.dp
    val expandedHeight = 480.dp

    /** The "drag here to remove" target shown at the bottom of the screen while dragging the bubble. */
    val trashZoneSize = 64.dp
}

private val CardShape = RoundedCornerShape(26.dp)
private val AccentFallback = Color(0xFF3B82F6) // blue; used when no artwork tint has resolved yet

/**
 * Root content hosted inside [FloatingLyricsService]'s window. Wrapped in the app's own
 * [AppTheme] (forced dark — a floating bubble sits over arbitrary third-party apps, so it must
 * stay legible regardless of the host app's own background).
 */
@Composable
fun FloatingLyricsOverlayRoot(
    sharedViewModel: SharedViewModel,
    mode: FloatingLyricsMode,
    opacity: Float,
    locked: Boolean,
    autoCollapseWhenPaused: Boolean,
    dismissZoneActive: Boolean,
    onRequestMinimize: () -> Unit,
    onRequestExpand: () -> Unit,
    onRequestCollapseToMini: () -> Unit,
    onRequestRestoreFromBubble: () -> Unit,
    onBubbleDrag: (dxPx: Float, dyPx: Float) -> Unit,
    onBubbleDragEnd: () -> Unit,
    onFreeDrag: (dxPx: Float, dyPx: Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onLockedChange: (Boolean) -> Unit,
    onAutoCollapseChange: (Boolean) -> Unit,
) {
    AppTheme(themeMode = DataStoreManager.THEME_MODE_DARK) {
        val screenData by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
        val timeline by sharedViewModel.timeline.collectAsStateWithLifecycle()
        val controllerState by sharedViewModel.controllerState.collectAsStateWithLifecycle()
        val tint = rememberNowPlayingGlowTint(screenData.thumbnailURL)

        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                if (targetState == FloatingLyricsMode.EXPANDED) {
                    // Entering EXPANDED means LyricsView is composing for the first time -- real
                    // work (scroll state, rich-sync timers, romanization) that no animation can
                    // stay smooth alongside, so this one direction snaps instead of easing.
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (fadeIn(tween(200)) + scaleIn(initialScale = 0.85f, animationSpec = tween(200)))
                        .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 0.85f, animationSpec = tween(140)))
                }
            },
            label = "floating_lyrics_mode",
        ) { current ->
            when (current) {
                FloatingLyricsMode.BUBBLE -> {
                    BubbleContent(
                        screenData = screenData,
                        tint = tint,
                        isPlaying = controllerState.isPlaying,
                        dismissZoneActive = dismissZoneActive,
                        onTap = onRequestRestoreFromBubble,
                        onDrag = onBubbleDrag,
                        onDragEnd = onBubbleDragEnd,
                    )
                }
                FloatingLyricsMode.MINI -> {
                    FloatingCard(tint = tint, opacity = opacity, width = FloatingLyricsSizes.miniWidth) {
                        MiniLyricsContent(
                            screenData = screenData,
                            timeline = timeline,
                            locked = locked,
                            onDrag = onFreeDrag,
                            onExpand = onRequestExpand,
                            onMinimize = onRequestMinimize,
                        )
                    }
                }
                FloatingLyricsMode.EXPANDED -> {
                    FloatingCard(tint = tint, opacity = opacity, width = FloatingLyricsSizes.expandedWidth) {
                        ExpandedLyricsContent(
                            sharedViewModel = sharedViewModel,
                            screenData = screenData,
                            timeline = timeline,
                            locked = locked,
                            opacity = opacity,
                            autoCollapseWhenPaused = autoCollapseWhenPaused,
                            onDrag = onFreeDrag,
                            onCollapse = onRequestCollapseToMini,
                            onMinimize = onRequestMinimize,
                            onOpacityChange = onOpacityChange,
                            onLockedChange = onLockedChange,
                            onAutoCollapseChange = onAutoCollapseChange,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The shared "chrome" for MINI and EXPANDED: a rounded card with a soft artwork-tinted gradient
 * (falling back to a fixed blue accent while no tint has resolved), a faint border, and a real
 * shadow — the same layered-elevation look the app's own sheets use, instead of a flat fill.
 */
@Composable
private fun FloatingCard(
    tint: Color?,
    opacity: Float,
    width: Dp,
    content: @Composable () -> Unit,
) {
    val accent = tint ?: AccentFallback
    val topColor by animateColorAsState(accent.rgbFactor(0.62f), tween(500), label = "cardTopColor")
    val baseDark = Color(0xFF0E1420) // dark navy, no purple tint (Beatland rule: no purple)

    Surface(
        modifier = Modifier.width(width),
        shape = CardShape,
        color = Color.Transparent,
        shadowElevation = 14.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier =
                Modifier
                    .clip(CardShape)
                    .angledGradientBackground(listOf(topColor.copy(alpha = opacity), baseDark.copy(alpha = opacity)), 115f)
                    .border(1.dp, Color.White.copy(alpha = 0.10f), CardShape),
        ) {
            content()
        }
    }
}

// ─────────────────────────────────────────── BUBBLE ───────────────────────────────────────────

@Composable
private fun BubbleContent(
    screenData: NowPlayingScreenData,
    tint: Color?,
    isPlaying: Boolean,
    dismissZoneActive: Boolean,
    onTap: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val accent = tint ?: AccentFallback
    val dismissColor = Color(0xFFE53935)
    val ringColor by animateColorAsState(if (dismissZoneActive) dismissColor else accent, tween(180), label = "bubbleRing")
    val scale by animateFloatAsState(if (dismissZoneActive) 1.22f else 1f, tween(180), label = "bubbleScale")

    val infinite = rememberInfiniteTransition(label = "bubblePulse")
    val pulse by
        infinite.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.55f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
            label = "pulseAlpha",
        )

    Box(
        modifier = Modifier.size(FloatingLyricsSizes.bubbleSize + 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isPlaying) {
            Box(
                Modifier
                    .size(FloatingLyricsSizes.bubbleSize + 14.dp)
                    .clip(CircleShape)
                    .background(ringColor.copy(alpha = pulse)),
            )
        }
        Box(
            modifier =
                Modifier
                    .size(FloatingLyricsSizes.bubbleSize)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(angledBubbleBrushColors(accent))
                    .border(2.dp, ringColor, CircleShape)
                    // Two independent detectors on purpose: detectDragGestures never calls ANY of
                    // its callbacks for a plain tap (it only engages once the touch moves past the
                    // slop threshold), so a tap needs its own detectTapGestures — it naturally
                    // backs off on real drags because the move events end up consumed below.
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, amount ->
                                change.consume()
                                onDrag(amount.x, amount.y)
                            },
                            onDragEnd = onDragEnd,
                        )
                    }.pointerInput(Unit) {
                        detectTapGestures(onTap = { onTap() })
                    },
            contentAlignment = Alignment.Center,
        ) {
            val thumbnailURL = screenData.thumbnailURL
            if (!thumbnailURL.isNullOrBlank()) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalPlatformContext.current)
                            .data(thumbnailURL)
                            .crossfade(250)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .diskCacheKey(thumbnailURL)
                            .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Icon(
                    imageVector = SimpIcons.Album,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

private fun angledBubbleBrushColors(accent: Color) =
    Brush.linearGradient(listOf(accent.rgbFactor(0.85f), accent.rgbFactor(0.35f)))

/**
 * The "drag here to remove" target. Lives in its own tiny overlay window (see
 * [FloatingLyricsService]'s `trashZone*` members) shown only while the bubble is being dragged,
 * so it never competes for space or touches with the bubble/card window itself. Wrapped in its
 * own [AppTheme] since it's a fully independent Compose root, same as [FloatingLyricsOverlayRoot].
 */
@Composable
fun TrashZoneRoot(active: Boolean) {
    AppTheme(themeMode = DataStoreManager.THEME_MODE_DARK) {
        val dismissColor = Color(0xFFE53935)
        val size by animateDpAsState(if (active) FloatingLyricsSizes.trashZoneSize + 16.dp else FloatingLyricsSizes.trashZoneSize, tween(150), label = "trashSize")
        val bg by animateColorAsState(if (active) dismissColor.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.55f), tween(150), label = "trashBg")
        val iconTint by animateColorAsState(if (active) Color.White else Color.White.copy(alpha = 0.75f), tween(150), label = "trashIcon")

        Box(
            modifier =
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(bg)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SimpIcons.Delete,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

// ──────────────────────────────────────────── MINI ────────────────────────────────────────────

@Composable
private fun MiniLyricsContent(
    screenData: NowPlayingScreenData,
    timeline: TimeLine,
    locked: Boolean,
    onDrag: (Float, Float) -> Unit,
    onExpand: () -> Unit,
    onMinimize: () -> Unit,
) {
    val lyricsData = screenData.lyricsData
    val currentLine =
        remember(lyricsData, timeline.current) {
            lyricsData?.lyrics?.lines?.let { lines ->
                lines.lastOrNull { line -> (line.startTimeMs.toLongOrNull() ?: Long.MAX_VALUE) <= timeline.current }
                    ?: lines.firstOrNull()
            }
        }
    val translatedLine =
        remember(lyricsData, currentLine) {
            val index = lyricsData?.lyrics?.lines?.indexOf(currentLine) ?: -1
            if (index >= 0) lyricsData?.translatedLyrics?.first?.lines?.getOrNull(index)?.words else null
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (locked) {
                        Modifier
                    } else {
                        Modifier.pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            }
                        }
                    },
                ).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        // Slim control row — no title here on purpose: MINI is meant to be a compact companion
        // while multitasking, so the vertical space goes to the lyric line instead.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = SimpIcons.DragHandle,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.30f),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.weight(1f))
            MiniIconButton(icon = SimpIcons.Fullscreen, contentDescription = "Expand", onClick = onExpand)
            Spacer(Modifier.width(4.dp))
            MiniIconButton(icon = SimpIcons.Close, contentDescription = "Minimize", onClick = onMinimize)
        }
        Spacer(Modifier.height(2.dp))
        // The lyric itself: allowed to wrap up to 2 lines (translation too) instead of the old
        // single-line + marquee, which is exactly what was clipping longer verses.
        if (currentLine == null) {
            Text(
                text = screenData.nowPlayingTitle.ifBlank { Brand.NAME },
                style = typo().bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else if (lyricsData?.lyrics?.syncType == "RICH_SYNCED") {
            val parsed: ParsedRichSyncLine? =
                remember(currentLine) {
                    parseRichSyncWords(currentLine.words, currentLine.startTimeMs, currentLine.endTimeMs)
                }
            if (parsed != null) {
                RichSyncLyricsLineItem(
                    parsedLine = parsed,
                    translatedWords = translatedLine,
                    currentTimeMs = timeline.current,
                    isCurrent = true,
                    customFontSize = typo().titleMedium.fontSize,
                    customPadding = 0.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                MiniPlainLine(currentLine.words, translatedLine)
            }
        } else {
            MiniPlainLine(currentLine.words, translatedLine)
        }
    }
}

@Composable
private fun MiniIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(24.dp)) {
            Icon(icon, contentDescription = contentDescription, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun MiniPlainLine(
    words: String,
    translated: String?,
) {
    Text(
        text = words,
        style = typo().titleMedium,
        fontWeight = FontWeight.Medium,
        color = Color.White,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
    if (!translated.isNullOrBlank()) {
        Spacer(Modifier.height(2.dp))
        Text(
            text = translated,
            style = typo().bodyMedium,
            color = Color(0xFFFFD54F).copy(alpha = 0.9f), // same warm yellow the app uses for translations
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────────────────────── EXPANDED ─────────────────────────────────────────

@Composable
private fun ExpandedLyricsContent(
    sharedViewModel: SharedViewModel,
    screenData: NowPlayingScreenData,
    timeline: TimeLine,
    locked: Boolean,
    opacity: Float,
    autoCollapseWhenPaused: Boolean,
    onDrag: (Float, Float) -> Unit,
    onCollapse: () -> Unit,
    onMinimize: () -> Unit,
    onOpacityChange: (Float) -> Unit,
    onLockedChange: (Boolean) -> Unit,
    onAutoCollapseChange: (Boolean) -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().height(FloatingLyricsSizes.expandedHeight)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (locked) {
                            Modifier
                        } else {
                            Modifier.pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.x, dragAmount.y)
                                }
                            }
                        },
                    ).padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkThumbnail(screenData)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                MarqueeTitle(text = screenData.nowPlayingTitle.ifBlank { Brand.NAME })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (screenData.artistName.isNotBlank()) {
                        Text(
                            text = screenData.artistName,
                            style = typo().bodySmall,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    SyncBadge(screenData.lyricsData?.lyrics?.syncType)
                }
            }
            Spacer(Modifier.width(4.dp))
            MiniIconButton(icon = SimpIcons.Settings, contentDescription = "Settings") { showSettings = !showSettings }
            Spacer(Modifier.width(4.dp))
            MiniIconButton(icon = SimpIcons.FullscreenExit, contentDescription = "Collapse", onClick = onCollapse)
            Spacer(Modifier.width(4.dp))
            MiniIconButton(icon = SimpIcons.Close, contentDescription = "Minimize", onClick = onMinimize)
        }

        AnimatedContent(targetState = showSettings, modifier = Modifier.weight(1f), label = "expanded_body") { settingsShown ->
            if (settingsShown) {
                SettingsPanel(
                    opacity = opacity,
                    locked = locked,
                    autoCollapseWhenPaused = autoCollapseWhenPaused,
                    onOpacityChange = onOpacityChange,
                    onLockedChange = onLockedChange,
                    onAutoCollapseChange = onAutoCollapseChange,
                )
            } else {
                val lyricsData = screenData.lyricsData
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    if (lyricsData == null || lyricsData.lyrics.error || lyricsData.lyrics.lines.isNullOrEmpty()) {
                        Text(
                            text = "No lyrics for this track yet",
                            style = typo().bodyMedium,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(24.dp).align(Alignment.Center),
                        )
                    } else {
                        LyricsView(
                            lyricsData = lyricsData,
                            timeLine = sharedViewModel.timeline,
                            onLineClick = { percent -> sharedViewModel.onUIEvent(UIEvent.UpdateProgress(percent)) },
                            backgroundColor = Color.Transparent,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtworkThumbnail(screenData: NowPlayingScreenData) {
    val thumbnailURL = screenData.thumbnailURL
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!thumbnailURL.isNullOrBlank()) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalPlatformContext.current)
                        .data(thumbnailURL)
                        .crossfade(250)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .diskCacheKey(thumbnailURL)
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = SimpIcons.Album,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun MarqueeTitle(text: String) {
    Text(
        text = text,
        style = typo().titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SyncBadge(syncType: String?) {
    val label =
        when (syncType) {
            "RICH_SYNCED", "LINE_SYNCED" -> "Synced"
            else -> return
        }
    Spacer(Modifier.width(6.dp))
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text = label, style = typo().labelSmall, color = Color.White.copy(alpha = 0.75f))
    }
}

@Composable
private fun SettingsPanel(
    opacity: Float,
    locked: Boolean,
    autoCollapseWhenPaused: Boolean,
    onOpacityChange: (Float) -> Unit,
    onLockedChange: (Boolean) -> Unit,
    onAutoCollapseChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Window settings",
            style = typo().labelMedium,
            color = Color.White.copy(alpha = 0.6f),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Background opacity", style = typo().bodyMedium, color = Color.White)
            Slider(
                value = opacity,
                onValueChange = onOpacityChange,
                valueRange = 0.55f..1f,
                colors =
                    SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White.copy(alpha = 0.85f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                    ),
            )
        }
        SettingsSwitchRow(
            title = "Lock position",
            subtitle = "Stop the window from being dragged by accident",
            checked = locked,
            onCheckedChange = onLockedChange,
        )
        SettingsSwitchRow(
            title = "Auto-hide when paused",
            subtitle = "Shrink to the bubble whenever playback pauses",
            checked = autoCollapseWhenPaused,
            onCheckedChange = onAutoCollapseChange,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = typo().bodyMedium, color = Color.White)
            Text(subtitle, style = typo().labelSmall, color = Color.White.copy(alpha = 0.55f))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color.White.copy(alpha = 0.4f),
                ),
        )
    }
}
