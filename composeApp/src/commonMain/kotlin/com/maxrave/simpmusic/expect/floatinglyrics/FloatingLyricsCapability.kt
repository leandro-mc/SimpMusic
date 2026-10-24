package com.maxrave.simpmusic.expect.floatinglyrics

import kotlinx.coroutines.flow.StateFlow

/**
 * Floating lyrics bubble — a personal-fork feature (Beatland), not part of upstream SimpMusic.
 *
 * This file only declares the shape; see the `.android.kt` / `.jvm.kt` actuals for what happens
 * on each platform. Follows the exact pattern already used for Last.fm / Cast in this codebase:
 * `composeApp`'s shared UI can call these unconditionally on every platform, and
 * [isFloatingLyricsAvailable] is what makes the Settings row disappear everywhere this isn't
 * wired up — the same shape as `isLastfmAvailable()` hiding the Last.fm settings block.
 *
 * Android-only by nature: a system overlay window ("display over other apps") is an Android OS
 * concept with no Desktop equivalent. Desktop already has its own always-on-top Mini Player
 * window for a similar job.
 */
expect fun isFloatingLyricsAvailable(): Boolean

/** Whether "display over other apps" has already been granted for this app. */
expect fun hasFloatingLyricsOverlayPermission(): Boolean

/** Sends the user to the system screen where "display over other apps" is granted. */
expect fun requestFloatingLyricsOverlayPermission()

/** Starts the floating lyrics window. Callers should check [hasFloatingLyricsOverlayPermission] first. */
expect fun startFloatingLyricsWindow()

/** Stops the floating lyrics window. */
expect fun stopFloatingLyricsWindow()

/**
 * Whether the floating lyrics window is on screen right now. It is the single source of truth for every place that
 * shows a switch or a menu entry for it (Settings, the now-playing menu), so they stay right even when the window
 * is closed some other way (dragged onto the trash, the notification's Stop, the system).
 */
expect fun floatingLyricsWindowShowing(): StateFlow<Boolean>
