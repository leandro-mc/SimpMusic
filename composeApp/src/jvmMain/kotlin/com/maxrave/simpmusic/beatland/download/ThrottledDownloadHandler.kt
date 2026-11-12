package com.maxrave.simpmusic.beatland.download

import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.repository.SongRepository
import com.maxrave.logger.Logger
import kotlinx.coroutines.flow.StateFlow
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

private const val TAG = "DownloadQueue"

/**
 * Beatland fork feature (D-048): the desktop [DownloadHandler] every screen talks to.
 *
 * It is `core`'s own handler wrapped by a [DownloadQueue]: only the four members that decide when a
 * download runs, what the UI sees and how it is stopped are replaced; `downloads` and anything `core`
 * may add to the interface later still go straight to the original.
 *
 * One thing differs from the original on purpose: `downloadTrack` used to suspend until the song was
 * on disk, and now it returns as soon as the song is queued. No caller needs the old behaviour (they
 * read the state from the song and from [downloadTask], never from the call finishing), and without
 * it a screen or a "like" no longer sits waiting for a download.
 */
internal class ThrottledDownloadHandler(
    delegate: DownloadHandler,
    private val queue: DownloadQueue,
) : DownloadHandler by delegate {
    override val downloadTask: StateFlow<Map<String, Int>>
        get() = queue.downloadTask

    override suspend fun downloadTrack(
        videoId: String,
        title: String,
        thumbnail: String,
    ) {
        queue.enqueue(videoId, title, thumbnail)
    }

    override fun removeDownload(videoId: String) {
        queue.remove(videoId)
    }

    override fun removeAllDownloads() {
        queue.removeAll()
    }
}

/**
 * Puts [ThrottledDownloadHandler] in place of `core`'s desktop handler for the whole app.
 *
 * HOW: Koin lets a later definition replace an earlier one for the same type (`allowOverride` is on
 * by default and nothing in the app turns it off). Every user of the handler asks Koin for it: the
 * screens' view models, and `core`'s `SongRepositoryImpl` (auto-download of liked songs, Smart
 * downloads), which resolves it lazily on first use. So this must run right after Koin starts and
 * before any of them asks; if it fails, the app simply keeps `core`'s handler.
 */
fun installThrottledDownloads() {
    val koin = KoinPlatform.getKoin()
    val original = koin.get<DownloadHandler>()
    if (original is ThrottledDownloadHandler) return
    val queue = DownloadQueue(delegate = original, songRepository = koin.get<SongRepository>())
    val replacement = ThrottledDownloadHandler(original, queue)
    koin.loadModules(listOf(module { single<DownloadHandler> { replacement } }), allowOverride = true)
    val resolved = koin.get<DownloadHandler>()
    if (resolved === replacement) {
        Logger.i(TAG, "Installed: at most $MAX_CONCURRENT_DOWNLOADS songs download at the same time")
    } else {
        Logger.w(TAG, "The queue was NOT installed: Koin still resolves ${resolved::class.simpleName}")
    }
    queue.cleanUpStaleStatesLater()
}
