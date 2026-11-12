package com.maxrave.simpmusic.beatland.diagnostics

import com.maxrave.logger.Logger
import com.maxrave.simpmusic.beatland.download.DownloadQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities

/**
 * Beatland fork feature (D-049): tells the log WHY the desktop window froze.
 *
 * A daemon thread asks the UI thread (the AWT event thread Compose Desktop draws on) to answer a
 * ping every half second. When an answer is more than [STALL_AFTER_MS] late, it writes to the app
 * log, under the tag `UiStall` at Warn level, what every thread that matters is doing:
 *  - the UI thread's stack, which shows what it is stuck on (a lock, a `runBlocking`, a file...);
 *  - the player thread's stack;
 *  - how many `Dispatchers.IO/Default` worker threads exist and in which state (all of them
 *    blocked means the pool is exhausted), and the stacks of some of those in download or
 *    stream-extraction code;
 *  - what the download queue was doing (see [DownloadQueue]).
 * It repeats every [REPORT_EVERY_MS] while the freeze lasts (at most [MAX_REPORTS_PER_STALL] times)
 * and logs when the UI thread answers again. Nothing is logged while the UI is healthy.
 *
 * It needs no `jcmd` or `jstack` (the installer's runtime may not ship them) and uses nothing but
 * `java.base` and `java.desktop`. It costs one tiny task on the UI thread every half second.
 */
object UiStallWatchdog {
    private const val TAG = "UiStall"
    private const val START_DELAY_MS = 5_000L
    private const val PING_EVERY_MS = 500L
    private const val POLL_MS = 100L
    private const val STALL_AFTER_MS = 3_000L
    private const val REPORT_EVERY_MS = 7_000L
    private const val MAX_REPORTS_PER_STALL = 4
    private const val UI_FRAMES = 40
    private const val OTHER_FRAMES = 20
    private const val MAX_WORKER_STACKS = 4

    /** A worker thread whose stack contains one of these is "in download code" for the report. */
    private val DOWNLOAD_MARKERS = listOf("download", "extractor", "kotlinytmusicscraper", "pipepipe", "okhttp")

    private val started = AtomicBoolean(false)
    private val pingSeq = AtomicLong(0)
    private val pongSeq = AtomicLong(0)

    @Volatile
    private var uiThread: Thread? = null

    /** Starts watching. Safe to call more than once. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        Thread({ watch() }, "Beatland-UiStallWatchdog").apply { isDaemon = true }.start()
    }

    private fun watch() {
        try {
            // Let the window come up first; the first ping may create the UI thread otherwise.
            Thread.sleep(START_DELAY_MS)
            Logger.i(TAG, "Watching the UI thread: a report is logged when it stops responding for ${STALL_AFTER_MS / 1000}s")
            while (true) {
                val sent = pingSeq.incrementAndGet()
                val sentAt = System.nanoTime()
                SwingUtilities.invokeLater {
                    uiThread = Thread.currentThread()
                    pongSeq.set(sent)
                }
                var reports = 0
                var nextReportAt = STALL_AFTER_MS
                while (pongSeq.get() < sent) {
                    Thread.sleep(POLL_MS)
                    val waited = (System.nanoTime() - sentAt) / 1_000_000
                    if (waited >= nextReportAt && reports < MAX_REPORTS_PER_STALL) {
                        reports++
                        report(waited, reports)
                        nextReportAt = waited + REPORT_EVERY_MS
                    }
                }
                if (reports > 0) {
                    Logger.w(TAG, "UI thread responsive again after ${(System.nanoTime() - sentAt) / 1_000_000} ms")
                }
                Thread.sleep(PING_EVERY_MS)
            }
        } catch (e: InterruptedException) {
            // The JVM is shutting down.
        } catch (e: Throwable) {
            Logger.e(TAG, "The UI watchdog stopped: ${e.message}", e)
        }
    }

    private fun report(
        waitedMs: Long,
        number: Int,
    ) {
        val traces = Thread.getAllStackTraces()
        Logger.w(TAG, "UI thread has not responded for $waitedMs ms (report $number of $MAX_REPORTS_PER_STALL)")
        Logger.w(TAG, "Download queue: ${DownloadQueue.describeForDiagnostics() ?: "not installed"}")

        val ui = uiThread ?: traces.keys.firstOrNull { it.name.startsWith("AWT-EventQueue") }
        if (ui == null) {
            Logger.w(TAG, "UI thread not found")
        } else {
            dump("UI thread", ui, traces[ui] ?: ui.stackTrace, UI_FRAMES)
        }

        traces.keys.firstOrNull { it.name == "Desktop-Player-Thread" }?.let { player ->
            dump("Player thread", player, traces[player] ?: player.stackTrace, OTHER_FRAMES)
        }

        val workers = traces.keys.filter { it.name.startsWith("DefaultDispatcher-worker") }
        Logger.w(TAG, "Coroutine worker threads: ${workers.size} ${workers.groupingBy { it.state }.eachCount()}; all threads: ${traces.size}")
        val inDownloadCode =
            workers.filter { worker ->
                (traces[worker] ?: emptyArray()).any { frame ->
                    DOWNLOAD_MARKERS.any { frame.className.contains(it, ignoreCase = true) }
                }
            }
        Logger.w(TAG, "Worker threads in download or extraction code: ${inDownloadCode.size}")
        inDownloadCode.take(MAX_WORKER_STACKS).forEach { worker ->
            dump("Download thread", worker, traces[worker] ?: worker.stackTrace, OTHER_FRAMES)
        }
    }

    /** One log entry per line, so every line keeps the `UiStall` tag and can be copied on its own. */
    private fun dump(
        label: String,
        thread: Thread,
        frames: Array<StackTraceElement>,
        maxFrames: Int,
    ) {
        Logger.w(TAG, "$label \"${thread.name}\" state=${thread.state}")
        frames.take(maxFrames).forEach { Logger.w(TAG, "    at $it") }
        if (frames.size > maxFrames) Logger.w(TAG, "    ... ${frames.size - maxFrames} more frames")
    }
}
