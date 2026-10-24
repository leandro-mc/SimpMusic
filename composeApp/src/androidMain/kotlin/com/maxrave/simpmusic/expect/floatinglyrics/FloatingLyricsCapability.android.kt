package com.maxrave.simpmusic.expect.floatinglyrics

import android.content.Context
import android.content.Intent
import com.maxrave.simpmusic.floatinglyrics.FloatingLyricsPermission
import com.maxrave.simpmusic.floatinglyrics.FloatingLyricsService
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * `androidContext(...)` in `SimpMusicApplication.onCreate()` (see `startKoin { ... }`) registers
 * the Application `Context` with Koin, so it can be pulled in here without any new DI wiring.
 */
private object FloatingLyricsAndroidBridge : KoinComponent {
    val context: Context by inject()
}

/**
 * True only in the Beatland build. `composeApp` compiles identically for the `official` flavor and
 * the `beatland` flavor, so this is a runtime check against the applicationId, matching the
 * `applicationIdSuffix = ".beatland"` configured in `androidApp/build.gradle.kts`'s `beatland`
 * product flavor. If that suffix is ever renamed, update [BEATLAND_APPLICATION_ID_MARKER] to match.
 */
private const val BEATLAND_APPLICATION_ID_MARKER = ".beatland"

actual fun isFloatingLyricsAvailable(): Boolean = FloatingLyricsAndroidBridge.context.packageName.contains(BEATLAND_APPLICATION_ID_MARKER)

actual fun hasFloatingLyricsOverlayPermission(): Boolean = FloatingLyricsPermission.isGranted(FloatingLyricsAndroidBridge.context)

actual fun requestFloatingLyricsOverlayPermission() {
    val context = FloatingLyricsAndroidBridge.context
    context.startActivity(
        FloatingLyricsPermission.requestIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

actual fun startFloatingLyricsWindow() {
    FloatingLyricsService.start(FloatingLyricsAndroidBridge.context)
}

actual fun stopFloatingLyricsWindow() {
    FloatingLyricsService.stop(FloatingLyricsAndroidBridge.context)
}

actual fun floatingLyricsWindowShowing(): StateFlow<Boolean> = FloatingLyricsService.running
