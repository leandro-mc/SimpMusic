package com.maxrave.simpmusic.beatland.download

import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.repository.SongRepository
import com.maxrave.logger.Logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/**
 * How many songs are downloaded at the same time on the desktop. Everything else waits in the queue.
 * Each song already opens several connections of its own, so a small number is plenty. Raise it only
 * while the log shows no `UiStall` lines with the bigger value.
 */
const val MAX_CONCURRENT_DOWNLOADS = 2

/** The UI hears about download progress at most this often, however many songs change in between. */
private const val UI_PUBLISH_INTERVAL_MS = 250L

/** Waits this long after startup before cleaning up songs a previous run left half-downloaded. */
private const val STARTUP_CLEANUP_DELAY_MS = 8_000L

private const val TAG = "DownloadQueue"

/**
 * Beatland fork feature (D-048): the desktop download queue.
 *
 * WHY IT EXISTS: `core`'s desktop `DownloadUtils.downloadTrack` has no limit of its own and runs in
 * whatever coroutine calls it (the screen's, i.e. Main). "Download all" on a playlist of 150 songs
 * launched 150 of them at once, each resolving its stream with blocking network calls on the shared
 * `Dispatchers.IO` pool that playback, DataStore and the database also use.
 *
 * WHAT IT DOES (the screens keep calling `DownloadHandler.downloadTrack` as before):
 *  - [enqueue] only records the song and returns, so the caller never waits;
 *  - at most [maxConcurrent] songs are handed to `core` at a time, always on `Dispatchers.IO`;
 *  - a song that is already waiting or running is ignored, and so is one already downloaded;
 *  - it lives in its own scope, so leaving a screen no longer cancels the downloads of that screen;
 *  - [remove] / [removeAll] cancel what is waiting or running, delete the partial files and put the
 *    songs back to "not downloaded";
 *  - [downloadTask] is what `core` reports plus the songs still waiting, at most every
 *    [UI_PUBLISH_INTERVAL_MS], so a long queue does not make the UI recompose for every song.
 *
 * STATES THE UI READS: `SongEntity.downloadState` stays under `core`'s control (it writes DOWNLOADING
 * when a song really starts, DOWNLOADED or NOT_DOWNLOADED when it ends); this queue only writes
 * NOT_DOWNLOADED, to undo a state nobody is working on any more. The [downloadTask] map shows a song
 * as DOWNLOADING from the moment it is queued: the playlist screens derive "this playlist is
 * downloading" from it, and a waiting song must not make them fall back to "not downloaded".
 */
internal class DownloadQueue(
    private val delegate: DownloadHandler,
    private val songRepository: SongRepository,
    maxConcurrent: Int = MAX_CONCURRENT_DOWNLOADS,
    private val scope: CoroutineScope = defaultScope(),
) {
    private val maxConcurrent = maxConcurrent.coerceAtLeast(1)

    private class Item(
        val videoId: String,
        val title: String,
        val thumbnail: String,
    )

    private enum class Outcome { FINISHED, SKIPPED, FAILED, CANCELLED }

    private sealed interface Detached {
        object Unknown : Detached

        object Waiting : Detached

        class Running(
            val job: Job,
        ) : Detached
    }

    /** Guards [waiting], [running] and [overrides]. Never held across a suspension or a blocking call. */
    private val lock = Any()
    private val waiting = LinkedHashMap<String, Item>()
    private val running = HashMap<String, Job>()

    /**
     * Songs whose state this queue knows better than `core` does: waiting and running ones show as
     * DOWNLOADING, and a cancelled or failed one as NOT_DOWNLOADED (that hides a DOWNLOADING that
     * `core` published and never took back). Changed only under [lock]; [overridesVersion] tells the
     * publisher that it changed, so a burst of 150 songs costs 150 counter bumps and not 150 map copies.
     */
    private val overrides = HashMap<String, Int>()
    private val overridesVersion = MutableStateFlow(0L)

    @OptIn(FlowPreview::class)
    val downloadTask: StateFlow<Map<String, Int>> =
        combine(delegate.downloadTask, overridesVersion) { base, _ -> base }
            .sample(UI_PUBLISH_INTERVAL_MS)
            .map { base -> mergedWithOverrides(base) }
            .stateIn(scope, SharingStarted.Eagerly, mergedWithOverrides(delegate.downloadTask.value))

    init {
        instance = this
    }

    /** Queues a song. Returns at once; never blocks and never touches the database. */
    fun enqueue(
        videoId: String,
        title: String,
        thumbnail: String,
    ) {
        if (videoId.isBlank()) {
            Logger.w(TAG, "Ignored a download request without a videoId")
            return
        }
        var duplicateOf: String? = null
        synchronized(lock) {
            when {
                videoId in running -> duplicateOf = "running"
                videoId in waiting -> duplicateOf = "waiting"
                else -> {
                    waiting[videoId] = Item(videoId, title, thumbnail)
                    setOverride(videoId, DownloadState.STATE_DOWNLOADING)
                }
            }
        }
        if (duplicateOf != null) {
            Logger.i(TAG, "Ignored $videoId: already $duplicateOf (${status()})")
        } else {
            Logger.i(TAG, "Queued $videoId \"$title\" (${status()})")
        }
        pump()
    }

    /**
     * Stops the download of one song, or removes its files when it is not in the queue.
     * Cancelling a running one finishes in the background (the files are only deleted once the
     * download has really stopped); a waiting or finished one is handled before this returns.
     */
    fun remove(videoId: String) {
        when (val detached = detach(videoId)) {
            is Detached.Running -> {
                Logger.i(TAG, "Cancelling $videoId (${status()})")
                scope.launch {
                    detached.job.cancelAndJoin()
                    removeFiles(videoId)
                    resetState(videoId)
                }
                pump()
            }
            Detached.Waiting -> {
                Logger.i(TAG, "Removed $videoId from the queue (${status()})")
                scope.launch { resetState(videoId) }
            }
            Detached.Unknown -> removeFiles(videoId)
        }
    }

    /** Cancels everything waiting or running, then lets `core` delete every downloaded file. */
    fun removeAll() {
        val (ids, jobs) =
            synchronized(lock) {
                val queuedIds = waiting.keys.toList()
                val runningIds = running.keys.toList()
                val runningJobs = running.values.toList()
                waiting.clear()
                running.clear()
                overrides.clear()
                overridesVersion.update { it + 1 }
                (queuedIds + runningIds) to runningJobs
            }
        Logger.i(TAG, "Removing all downloads: ${ids.size} were queued or running")
        scope.launch {
            jobs.forEach { it.cancelAndJoin() }
            try {
                delegate.removeAllDownloads()
            } catch (e: Throwable) {
                Logger.w(TAG, "Could not delete every downloaded file: ${e.message}")
            }
            // `core` has just emptied its own map; what is left here are the marks of the jobs above.
            synchronized(lock) {
                overrides.values.removeAll { it != DownloadState.STATE_DOWNLOADING }
                overridesVersion.update { it + 1 }
            }
            ids.forEach { resetState(it) }
        }
    }

    /**
     * A previous run that was closed (or killed) with the queue full leaves songs marked as
     * PREPARING or DOWNLOADING that nobody is working on: the queue lives in memory only. Puts
     * them back to "not downloaded" so their buttons work again. Partial files stay on disk and are
     * overwritten when the song is downloaded again. Songs queued since startup are left alone.
     */
    fun cleanUpStaleStatesLater() {
        scope.launch {
            delay(STARTUP_CLEANUP_DELAY_MS)
            val stale =
                songRepository
                    .getDownloadingSongs()
                    .firstOrNull()
                    .orEmpty()
                    .filterNot { isTracked(it.videoId) }
            if (stale.isEmpty()) return@launch
            Logger.w(TAG, "${stale.size} songs were left as preparing/downloading by a previous run; resetting them")
            stale.forEach { resetState(it.videoId) }
        }
    }

    /** One line for the log and for the `UiStall` report. */
    fun status(): String =
        synchronized(lock) {
            "waiting=${waiting.size} running=${running.size}/$maxConcurrent"
        }

    private fun runningIds(): List<String> = synchronized(lock) { running.keys.toList() }

    private fun isTracked(videoId: String): Boolean = synchronized(lock) { videoId in waiting || videoId in running }

    /** Call with [lock] held. */
    private fun setOverride(
        videoId: String,
        state: Int,
    ) {
        overrides[videoId] = state
        overridesVersion.update { it + 1 }
    }

    /** Call with [lock] held. */
    private fun clearOverride(videoId: String) {
        if (overrides.remove(videoId) != null) overridesVersion.update { it + 1 }
    }

    private fun mergedWithOverrides(base: Map<String, Int>): Map<String, Int> =
        synchronized(lock) {
            if (overrides.isEmpty()) base else HashMap(base).apply { putAll(overrides) }
        }

    private fun detach(videoId: String): Detached =
        synchronized(lock) {
            if (waiting.remove(videoId) != null) {
                clearOverride(videoId)
                return@synchronized Detached.Waiting
            }
            val job = running.remove(videoId)
            // The mark stays until the job has ended: finish() turns it into NOT_DOWNLOADED.
            if (job != null) Detached.Running(job) else Detached.Unknown
        }

    /** Starts waiting songs while there is a free slot. */
    private fun pump() {
        val started = ArrayList<Job>()
        synchronized(lock) {
            while (running.size < maxConcurrent && waiting.isNotEmpty()) {
                val iterator = waiting.entries.iterator()
                val item = iterator.next().value
                iterator.remove()
                val job = scope.launch(start = CoroutineStart.LAZY) { execute(item) }
                running[item.videoId] = job
                started += job
            }
        }
        // Outside the lock: a started job takes the lock itself when it ends.
        started.forEach { it.start() }
    }

    private suspend fun execute(item: Item) {
        val me = currentCoroutineContext()[Job]
        val startedAt = System.nanoTime()
        var outcome = Outcome.FINISHED
        try {
            Logger.i(TAG, "Started ${item.videoId} (${status()})")
            val song = songRepository.getSongById(item.videoId).firstOrNull()
            if (song == null) {
                outcome = Outcome.SKIPPED
                Logger.w(TAG, "Skipped ${item.videoId}: the song is not in the library database")
            } else if (song.downloadState == DownloadState.STATE_DOWNLOADED) {
                outcome = Outcome.SKIPPED
                Logger.i(TAG, "Skipped ${item.videoId}: already downloaded")
            } else {
                delegate.downloadTrack(item.videoId, item.title, item.thumbnail)
                // `core` reports failures by resetting the song, not by throwing, so ask what is left.
                val after = songRepository.getSongById(item.videoId).firstOrNull()?.downloadState
                if (after != DownloadState.STATE_DOWNLOADED) {
                    outcome = Outcome.FAILED
                    if (after != DownloadState.STATE_NOT_DOWNLOADED) resetState(item.videoId)
                }
            }
        } catch (e: CancellationException) {
            outcome = Outcome.CANCELLED
            throw e
        } catch (e: Throwable) {
            outcome = Outcome.FAILED
            Logger.e(TAG, "Failed ${item.videoId}: ${e.message}", e)
            try {
                resetState(item.videoId)
            } catch (ignored: CancellationException) {
                // The scope is going away; the stale-state cleanup of the next run covers it.
            }
        } finally {
            val seconds = (System.nanoTime() - startedAt) / 1_000_000_000.0
            finish(item.videoId, me, outcome)
            val took = "%.1f".format(Locale.ROOT, seconds)
            when (outcome) {
                Outcome.FINISHED -> Logger.i(TAG, "Finished ${item.videoId} in ${took}s (${status()})")
                Outcome.SKIPPED -> Unit
                Outcome.FAILED -> Logger.w(TAG, "Failed ${item.videoId} after ${took}s: no file was produced (${status()})")
                Outcome.CANCELLED -> Logger.i(TAG, "Cancelled ${item.videoId} after ${took}s (${status()})")
            }
        }
    }

    private fun finish(
        videoId: String,
        job: Job?,
        outcome: Outcome,
    ) {
        synchronized(lock) {
            if (running[videoId] === job) running.remove(videoId)
            // Queued again while the old job was still winding down: the new mark must stay.
            if (videoId !in waiting && videoId !in running) {
                when (outcome) {
                    Outcome.FINISHED, Outcome.SKIPPED -> clearOverride(videoId)
                    Outcome.FAILED, Outcome.CANCELLED -> setOverride(videoId, DownloadState.STATE_NOT_DOWNLOADED)
                }
            }
        }
        pump()
    }

    private fun removeFiles(videoId: String) {
        try {
            delegate.removeDownload(videoId)
        } catch (e: Throwable) {
            Logger.w(TAG, "Could not delete the files of $videoId: ${e.message}")
        }
    }

    private suspend fun resetState(videoId: String) {
        try {
            songRepository.updateDownloadState(videoId, DownloadState.STATE_NOT_DOWNLOADED)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.w(TAG, "Could not reset the download state of $videoId: ${e.message}")
        }
    }

    companion object {
        @Volatile
        private var instance: DownloadQueue? = null

        /** For the `UiStall` report: what the queue is doing right now, or null before it exists. */
        fun describeForDiagnostics(): String? {
            val queue = instance ?: return null
            return "${queue.status()} runningIds=${queue.runningIds()}"
        }

        private fun defaultScope(): CoroutineScope =
            CoroutineScope(
                SupervisorJob() + Dispatchers.IO + CoroutineName("BeatlandDownloadQueue") +
                    CoroutineExceptionHandler { _, e ->
                        Logger.e(TAG, "Unhandled error in the download queue: ${e.message}", e)
                    },
            )
    }
}
