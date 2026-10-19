package com.maxrave.simpmusic.floatinglyrics

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * `SYSTEM_ALERT_WINDOW` ("display over other apps") has no runtime dialog like normal
 * permissions — the user has to be sent to a system Settings screen to grant it. This object is
 * the single place that knows how.
 */
object FloatingLyricsPermission {
    fun isGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Intent that opens the system's "display over other apps" screen for this app. */
    fun requestIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
