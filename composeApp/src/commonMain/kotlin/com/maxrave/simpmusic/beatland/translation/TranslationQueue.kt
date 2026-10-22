package com.maxrave.simpmusic.beatland.translation

import com.maxrave.domain.data.model.metadata.Lyrics
import com.maxrave.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Beatland fork feature (D-031): one place that decides WHEN a song's AI translation runs.
 *
 * Before it, each request simply started: tapping "Regenerate" twice, regenerating while the automatic translation
 * of the same song was running, or asking for several songs, all fired overlapping calls at the provider and could
 * overwrite each other's result. Now:
 *
 *  - Only ONE translation runs at a time (one request stream to the provider, no rate-limit storms).
 *  - Each song appears AT MOST once, whether it is running or waiting: a second request for it does nothing.
 *  - When the running one ends, the next is the song that is playing right now if it is waiting; otherwise the one
 *    that was requested first.
 *  - An [Kind.AUTO] request (the translation a song gets when it starts) is only worth doing for the song being
 *    listened to, so it is dropped if the user has moved on before its turn. An explicit [Kind.REGENERATE] is never
 *    dropped.
 *
 * Not thread-safe on purpose: it must be used from a single dispatcher (the view model's main scope), which is what
 * lets [enqueue] decide synchronously — no suspension between "is it already queued?" and "queue it" — so two quick
 * taps cannot both get through.
 */
class TranslationQueue(
    private val scope: CoroutineScope,
    /** The videoId of the song playing right now, or null. */
    private val playingVideoId: () -> String?,
    /** Does the actual work of one job. Exceptions are logged and do not stop the queue. */
    private val execute: suspend (Job) -> Unit,
) {
    enum class Kind { AUTO, REGENERATE }

    class Job(
        val videoId: String,
        val lyrics: Lyrics,
        val kind: Kind,
        /** Song title, used to say which song a message is about when it is not the one playing. */
        val label: String,
    )

    sealed interface Result {
        /** Nothing was ahead of it: it starts right away. */
        data object StartedNow : Result

        /** It waits behind [ahead] other songs (the running one counts). */
        data class Queued(
            val ahead: Int,
        ) : Result

        /** That song is already running or waiting: the request is ignored. */
        data object AlreadyInProgress : Result

        data object QueueFull : Result
    }

    private val pending = ArrayList<Job>()
    private var running: Job? = null
    private var workerActive = false

    private val _inProgress = MutableStateFlow<Set<String>>(emptySet())

    /** VideoIds that are running or waiting. The UI uses it to show "regenerating…" for the song on screen. */
    val inProgress: StateFlow<Set<String>> = _inProgress

    fun enqueue(
        videoId: String,
        lyrics: Lyrics,
        kind: Kind,
        label: String,
    ): Result {
        if (running?.videoId == videoId || pending.any { it.videoId == videoId }) return Result.AlreadyInProgress
        if (pending.size >= MAX_PENDING) return Result.QueueFull
        val nothingAhead = running == null && pending.isEmpty()
        val job = Job(videoId, lyrics, kind, label)
        pending.add(job)
        publish()
        val ahead = (if (running != null) 1 else 0) + executionOrder().indexOf(job)
        startWorker()
        return if (nothingAhead) Result.StartedNow else Result.Queued(ahead)
    }

    /** Waiting jobs in the order they will run: the playing song first, then by request time. */
    private fun executionOrder(): List<Job> {
        val now = playingVideoId()
        val playing = pending.filter { it.videoId == now }
        return playing + pending.filter { it.videoId != now }
    }

    private fun takeNext(): Job? {
        val now = playingVideoId()
        pending.removeAll { it.kind == Kind.AUTO && it.videoId != now }
        val next = executionOrder().firstOrNull() ?: return null
        pending.remove(next)
        return next
    }

    private fun startWorker() {
        if (workerActive) return
        workerActive = true
        scope.launch {
            try {
                while (true) {
                    val job = takeNext() ?: break
                    running = job
                    publish()
                    try {
                        execute(job)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.w(TAG, "Translation job for ${job.videoId} failed: ${e.message}")
                    }
                    running = null
                    publish()
                }
            } finally {
                running = null
                workerActive = false
                publish()
            }
        }
    }

    private fun publish() {
        val ids = HashSet<String>()
        running?.let { ids.add(it.videoId) }
        pending.forEach { ids.add(it.videoId) }
        _inProgress.value = ids
    }

    private companion object {
        const val TAG = "TranslationQueue"
        const val MAX_PENDING = 10
    }
}
