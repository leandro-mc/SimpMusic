package com.maxrave.simpmusic.expect.floatinglyrics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Desktop already has its own always-on-top Mini Player window (composeApp/src/jvmMain/.../mini_player/)
// for the same "keep lyrics visible while I use other windows" job, so this feature simply doesn't
// exist here — isFloatingLyricsAvailable() = false hides the Settings row entirely, and the rest
// are unreachable no-ops kept only so the shared code in SettingScreen.kt compiles.

actual fun isFloatingLyricsAvailable(): Boolean = false

actual fun hasFloatingLyricsOverlayPermission(): Boolean = false

actual fun requestFloatingLyricsOverlayPermission() {
}

actual fun startFloatingLyricsWindow() {
}

actual fun stopFloatingLyricsWindow() {
}

private val neverShowing: StateFlow<Boolean> = MutableStateFlow(false)

actual fun floatingLyricsWindowShowing(): StateFlow<Boolean> = neverShowing
