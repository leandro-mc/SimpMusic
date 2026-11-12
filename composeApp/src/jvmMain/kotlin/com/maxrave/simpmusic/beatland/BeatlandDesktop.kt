package com.maxrave.simpmusic.beatland

import com.maxrave.logger.Logger
import com.maxrave.simpmusic.beatland.diagnostics.UiStallWatchdog
import com.maxrave.simpmusic.beatland.download.installThrottledDownloads

/**
 * Beatland fork (D-048, D-049): the one call `DesktopApp.kt` makes for everything that is desktop-only and
 * Beatland's own, so the upstream file carries a single line.
 *
 * Call it right after Koin starts and before the window exists:
 *  - the download queue replaces `core`'s download handler in Koin, which has to happen before anything
 *    asks Koin for the handler;
 *  - the UI watchdog starts its own daemon thread.
 * Neither can stop the app from starting: a failure is logged and the app carries on without it.
 */
fun installBeatlandDesktopServices() {
    try {
        installThrottledDownloads()
    } catch (e: Throwable) {
        Logger.e("DownloadQueue", "The download queue could not be installed; downloads keep their old behaviour: ${e.message}", e)
    }
    try {
        UiStallWatchdog.start()
    } catch (e: Throwable) {
        Logger.e("UiStall", "The UI watchdog could not start: ${e.message}", e)
    }
}
