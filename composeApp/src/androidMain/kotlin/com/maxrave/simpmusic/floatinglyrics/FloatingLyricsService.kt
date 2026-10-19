package com.maxrave.simpmusic.floatinglyrics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.maxrave.simpmusic.brand.Brand
import com.maxrave.simpmusic.viewModel.SharedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.roundToInt

/**
 * Hosts the floating lyrics bubble as a `TYPE_APPLICATION_OVERLAY` window, on top of whatever app
 * is in front (WhatsApp, Instagram, the launcher, ...).
 *
 * Three window states, matching Musixmatch's own bubble: [FloatingLyricsMode.BUBBLE] (a small
 * always-on-top circle, tap to reopen, drag to move, drag onto the bottom edge to dismiss),
 * [FloatingLyricsMode.MINI] (a narrow bar for using the phone one-handed alongside another app)
 * and [FloatingLyricsMode.EXPANDED] (the full scrollable card).
 *
 * Deliberately Android-only and deliberately outside `MainActivity`: SimpMusic already exposes
 * everything this needs as a process-wide Koin singleton (`SharedViewModel` is registered with
 * Koin's `single { }`, not `viewModel { }` — see `composeApp/.../di/ViewModelModule.kt` — so it is
 * the SAME instance the Now Playing screen observes, with zero duplicated sync logic).
 */
class FloatingLyricsService :
    Service(),
    KoinComponent {
    private val sharedViewModel: SharedViewModel by inject()

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var lifecycleOwner: FloatingLyricsLifecycleOwner? = null
    private lateinit var prefs: SharedPreferences
    private var serviceJob = SupervisorJob()
    private var serviceScope = CoroutineScope(AndroidUiDispatcher.Main + serviceJob)
    private var animationJob: Job? = null

    private var layoutParams: WindowManager.LayoutParams? = null

    private var trashZoneView: ComposeView? = null
    private var trashZoneLifecycleOwner: FloatingLyricsLifecycleOwner? = null

    // ── Compose-observable state, hoisted here so mode/setting changes just recompose the
    //    already-attached content instead of tearing down and rebuilding the ComposeView. ──
    private val modeState = mutableStateOf(FloatingLyricsMode.MINI)
    private val opacityState = mutableFloatStateOf(0.94f)
    private val lockedState = mutableStateOf(false)
    private val autoCollapseState = mutableStateOf(false)
    private val dismissZoneActiveState = mutableStateOf(false)

    /** What BUBBLE taps back into — whichever of MINI/EXPANDED was last actually shown. */
    private var lastExpandedMode = FloatingLyricsMode.MINI
    private var wasPlaying = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val savedMode = prefs.getString(KEY_MODE, MODE_MINI)
        modeState.value =
            when (savedMode) {
                MODE_EXPANDED -> FloatingLyricsMode.EXPANDED
                MODE_BUBBLE -> FloatingLyricsMode.BUBBLE
                else -> FloatingLyricsMode.MINI
            }
        lastExpandedMode = if (modeState.value == FloatingLyricsMode.EXPANDED) FloatingLyricsMode.EXPANDED else FloatingLyricsMode.MINI
        opacityState.floatValue = prefs.getFloat(KEY_OPACITY, 0.94f)
        lockedState.value = prefs.getBoolean(KEY_LOCKED, false)
        autoCollapseState.value = prefs.getBoolean(KEY_AUTO_COLLAPSE, false)

        startForegroundWithNotification()
        addOverlayWindow()
        observePlaybackForAutoCollapse()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        hideTrashZone()
        removeOverlayWindow()
        serviceJob.cancel()
        super.onDestroy()
    }

    // ───────────────────────────── window setup ─────────────────────────────

    private fun addOverlayWindow() {
        val owner = FloatingLyricsLifecycleOwner().also { lifecycleOwner = it }
        val metrics = screenMetrics()

        val view =
            ComposeView(this).apply {
                owner.attachToView(this)
                disposeComposeOnLifecycleDestroyed(this)
                setContent {
                    FloatingLyricsOverlayRoot(
                        sharedViewModel = sharedViewModel,
                        mode = modeState.value,
                        opacity = opacityState.floatValue,
                        locked = lockedState.value,
                        autoCollapseWhenPaused = autoCollapseState.value,
                        dismissZoneActive = dismissZoneActiveState.value,
                        onRequestMinimize = { switchMode(FloatingLyricsMode.BUBBLE) },
                        onRequestExpand = { switchMode(FloatingLyricsMode.EXPANDED) },
                        onRequestCollapseToMini = { switchMode(FloatingLyricsMode.MINI) },
                        onRequestRestoreFromBubble = { switchMode(lastExpandedMode) },
                        onBubbleDrag = ::onBubbleDrag,
                        onBubbleDragEnd = ::onBubbleDragEnd,
                        onFreeDrag = ::onFreeDrag,
                        onOpacityChange = ::onOpacityChange,
                        onLockedChange = ::onLockedChange,
                        onAutoCollapseChange = ::onAutoCollapseChange,
                    )
                }
            }
        composeView = view

        val (savedX, savedY) = readSavedPosition()
        val widthPx = widthPxFor(modeState.value, metrics)
        val params =
            WindowManager.LayoutParams(
                widthPx,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = savedX
                y = savedY
            }
        layoutParams = params
        clampToScreen(params, widthPx, approxHeightPxFor(modeState.value, metrics))
        windowManager.addView(view, params)
        owner.onWindowShown()
    }

    private fun removeOverlayWindow() {
        composeView?.let { view -> runCatching { windowManager.removeViewImmediate(view) } }
        lifecycleOwner?.let {
            it.onWindowHidden()
            it.destroy()
        }
        composeView = null
        lifecycleOwner = null
    }

    // ───────────────────────────── mode switching ─────────────────────────────

    private fun switchMode(newMode: FloatingLyricsMode) {
        if (modeState.value == newMode) return
        val metrics = screenMetrics()
        val params = layoutParams ?: return
        val view = composeView ?: return

        val oldWidthPx = params.width
        val oldHeightPx = approxHeightPxFor(modeState.value, metrics)
        val newWidthPx = widthPxFor(newMode, metrics)
        val newHeightPx = approxHeightPxFor(newMode, metrics)

        // Resize around the window's current CENTER, not its top-left corner, so switching sizes
        // feels like a natural zoom in/out from where the bubble/card already is instead of
        // jumping to re-anchor at a corner -- this is the "no incómoda" requirement.
        val centerX = params.x + oldWidthPx / 2
        val centerY = params.y + oldHeightPx / 2
        var targetX = centerX - newWidthPx / 2
        var targetY = centerY - newHeightPx / 2
        val clampedPoint = clampPoint(targetX, targetY, newWidthPx, newHeightPx, metrics)
        targetX = clampedPoint.first
        targetY = clampedPoint.second

        if (newMode != FloatingLyricsMode.BUBBLE) {
            lastExpandedMode = newMode
        }
        modeState.value = newMode
        prefs
            .edit()
            .putString(
                KEY_MODE,
                when (newMode) {
                    FloatingLyricsMode.EXPANDED -> MODE_EXPANDED
                    FloatingLyricsMode.BUBBLE -> MODE_BUBBLE
                    FloatingLyricsMode.MINI -> MODE_MINI
                },
            ).apply()

        if (newMode == FloatingLyricsMode.EXPANDED) {
            // Entering EXPANDED means LyricsView is composing for the very first time (its own
            // scroll state, rich-sync word timers, romanization lookups, ...) -- real,
            // unavoidable main-thread work. Animating the window around that at the same time
            // just reads as dropped frames, so this one transition snaps instead of easing; every
            // other transition still animates smoothly since none of them carry that cost.
            animationJob?.cancel()
            params.x = targetX
            params.y = targetY
            params.width = newWidthPx
            runCatching { windowManager.updateViewLayout(view, params) }
            savePosition(targetX, targetY)
        } else {
            animateWindowTo(targetX, targetY, newWidthPx)
        }
    }

    // ───────────────────────────── drag handling ─────────────────────────────

    private fun onFreeDrag(
        dxPx: Float,
        dyPx: Float,
    ) {
        if (lockedState.value) return
        val view = composeView ?: return
        val params = layoutParams ?: return
        val metrics = screenMetrics()
        params.x += dxPx.roundToInt()
        params.y += dyPx.roundToInt()
        val clamped = clampPoint(params.x, params.y, params.width, approxHeightPxFor(modeState.value, metrics), metrics)
        params.x = clamped.first
        params.y = clamped.second
        runCatching { windowManager.updateViewLayout(view, params) }
        savePosition(params.x, params.y)
    }

    private fun onBubbleDrag(
        dxPx: Float,
        dyPx: Float,
    ) {
        if (lockedState.value) return
        val view = composeView ?: return
        val params = layoutParams ?: return
        val metrics = screenMetrics()
        if (trashZoneView == null) showTrashZone(metrics)
        params.x = (params.x + dxPx.roundToInt()).coerceIn(-params.width / 2, metrics.widthPixels - params.width / 2)
        val bottomMarginPx = (24 * metrics.density).roundToInt()
        params.y = (params.y + dyPx.roundToInt()).coerceIn(0, metrics.heightPixels - bottomMarginPx)
        runCatching { windowManager.updateViewLayout(view, params) }
        val dismissZoneTop = metrics.heightPixels - (DISMISS_ZONE_HEIGHT_DP * metrics.density)
        dismissZoneActiveState.value = params.y > dismissZoneTop
    }

    private fun onBubbleDragEnd() {
        hideTrashZone()
        val params = layoutParams ?: return
        val metrics = screenMetrics()
        if (dismissZoneActiveState.value) {
            dismissZoneActiveState.value = false
            stopSelf()
            return
        }
        val bubbleWidthPx = widthPxFor(FloatingLyricsMode.BUBBLE, metrics)
        val margin = (BUBBLE_EDGE_MARGIN_DP * metrics.density).roundToInt()
        val targetX =
            if (params.x + bubbleWidthPx / 2 < metrics.widthPixels / 2) {
                margin
            } else {
                metrics.widthPixels - bubbleWidthPx - margin
            }
        val targetY = params.y.coerceIn(margin, (metrics.heightPixels - bubbleWidthPx - margin).coerceAtLeast(margin))
        savePosition(targetX, targetY)
        animateWindowTo(targetX, targetY, bubbleWidthPx)
    }

    /** Small always-on-top "drag here to remove" target shown only while the bubble is being
     *  dragged. A fully separate window (not touchable — see [WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE])
     *  so it never intercepts the drag itself; only reacts visually via [dismissZoneActiveState]. */
    private fun showTrashZone(metrics: DisplayMetrics) {
        val owner = FloatingLyricsLifecycleOwner().also { trashZoneLifecycleOwner = it }
        val basePx = (FloatingLyricsSizes.trashZoneSize.value * metrics.density).roundToInt()
        val view =
            ComposeView(this).apply {
                owner.attachToView(this)
                disposeComposeOnLifecycleDestroyed(this)
                setContent { TrashZoneRoot(active = dismissZoneActiveState.value) }
            }
        trashZoneView = view
        val marginBottomPx = (28 * metrics.density).roundToInt()
        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (metrics.widthPixels - basePx) / 2
                y = metrics.heightPixels - basePx - marginBottomPx
            }
        runCatching { windowManager.addView(view, params) }
        owner.onWindowShown()
    }

    private fun hideTrashZone() {
        trashZoneView?.let { view -> runCatching { windowManager.removeViewImmediate(view) } }
        trashZoneLifecycleOwner?.let {
            it.onWindowHidden()
            it.destroy()
        }
        trashZoneView = null
        trashZoneLifecycleOwner = null
    }

    // ───────────────────────────── settings ─────────────────────────────

    private fun onOpacityChange(value: Float) {
        opacityState.floatValue = value
        prefs.edit().putFloat(KEY_OPACITY, value).apply()
    }

    private fun onLockedChange(value: Boolean) {
        lockedState.value = value
        prefs.edit().putBoolean(KEY_LOCKED, value).apply()
    }

    private fun onAutoCollapseChange(value: Boolean) {
        autoCollapseState.value = value
        prefs.edit().putBoolean(KEY_AUTO_COLLAPSE, value).apply()
    }

    private fun observePlaybackForAutoCollapse() {
        serviceScope.launch {
            sharedViewModel.controllerState
                .map { it.isPlaying }
                .distinctUntilChanged()
                .collect { isPlaying ->
                    if (autoCollapseState.value) {
                        if (!isPlaying && wasPlaying && modeState.value != FloatingLyricsMode.BUBBLE) {
                            switchMode(FloatingLyricsMode.BUBBLE)
                        } else if (isPlaying && !wasPlaying && modeState.value == FloatingLyricsMode.BUBBLE) {
                            switchMode(lastExpandedMode)
                        }
                    }
                    wasPlaying = isPlaying
                }
        }
    }

    // ───────────────────────────── geometry / animation helpers ─────────────────────────────

    private fun screenMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        return metrics
    }

    private fun widthPxFor(
        mode: FloatingLyricsMode,
        metrics: DisplayMetrics,
    ): Int {
        val dp =
            when (mode) {
                FloatingLyricsMode.BUBBLE -> FloatingLyricsSizes.bubbleSize
                FloatingLyricsMode.MINI -> FloatingLyricsSizes.miniWidth
                FloatingLyricsMode.EXPANDED -> FloatingLyricsSizes.expandedWidth
            }
        return (dp.value * metrics.density).roundToInt()
    }

    /** Only used for centering math on mode-switch (real height is WRAP_CONTENT / dynamic). */
    private fun approxHeightPxFor(
        mode: FloatingLyricsMode,
        metrics: DisplayMetrics,
    ): Int {
        val dp =
            when (mode) {
                FloatingLyricsMode.BUBBLE -> FloatingLyricsSizes.bubbleSize
                FloatingLyricsMode.MINI -> FloatingLyricsSizes.miniApproxHeight
                FloatingLyricsMode.EXPANDED -> FloatingLyricsSizes.expandedHeight
            }
        return (dp.value * metrics.density).roundToInt()
    }

    private fun clampPoint(
        x: Int,
        y: Int,
        widthPx: Int,
        heightPx: Int,
        metrics: DisplayMetrics,
    ): Pair<Int, Int> {
        val clampedX = x.coerceIn(0, (metrics.widthPixels - widthPx).coerceAtLeast(0))
        val clampedY = y.coerceIn(0, (metrics.heightPixels - heightPx).coerceAtLeast(0))
        return clampedX to clampedY
    }

    private fun clampToScreen(
        params: WindowManager.LayoutParams,
        widthPx: Int,
        heightPx: Int,
    ) {
        val metrics = screenMetrics()
        val clamped = clampPoint(params.x, params.y, widthPx, heightPx, metrics)
        params.x = clamped.first
        params.y = clamped.second
    }

    /** Eases the window's x/y/width to a target over [durationMs] instead of an instant jump.
     *  Runs on the real display frame clock (see [AndroidUiDispatcher.Main] on [serviceScope]) —
     *  a fixed-step delay() loop was tried first and looked choppy (~10 discrete updates over
     *  the whole animation, regardless of the device's actual refresh rate); this ticks every
     *  frame and paces itself off real elapsed time, so it's smooth at 60/90/120Hz alike. */
    private fun animateWindowTo(
        targetX: Int,
        targetY: Int,
        targetWidthPx: Int,
        durationMs: Long = 220,
    ) {
        val params = layoutParams ?: return
        val view = composeView ?: return
        animationJob?.cancel()
        val startX = params.x
        val startY = params.y
        val startW = params.width
        val durationNanos = durationMs * 1_000_000L
        animationJob =
            serviceScope.launch {
                var startTimeNanos = -1L
                while (true) {
                    val frameTimeNanos = withFrameNanos { it }
                    if (startTimeNanos < 0) startTimeNanos = frameTimeNanos
                    val elapsed = frameTimeNanos - startTimeNanos
                    val t = (elapsed.toFloat() / durationNanos).coerceIn(0f, 1f)
                    val eased = 1 - (1 - t) * (1 - t)
                    params.x = (startX + (targetX - startX) * eased).roundToInt()
                    params.y = (startY + (targetY - startY) * eased).roundToInt()
                    params.width = (startW + (targetWidthPx - startW) * eased).roundToInt()
                    runCatching { windowManager.updateViewLayout(view, params) }
                    if (t >= 1f) break
                }
            }
    }

    private fun readSavedPosition(): Pair<Int, Int> {
        val x = prefs.getInt(KEY_X, 40)
        val y = prefs.getInt(KEY_Y, 160)
        return x to y
    }

    private fun savePosition(
        x: Int,
        y: Int,
    ) {
        prefs.edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply()
    }

    // ───────────────────────────── foreground notification ─────────────────────────────

    private fun startForegroundWithNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Floating lyrics", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Keeps the floating lyrics window shown over other apps"
                    setShowBadge(false)
                },
            )
        }

        val stopIntent = Intent(this, FloatingLyricsService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent =
            PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification: Notification =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setContentTitle(Brand.NAME)
                .setContentText("Floating lyrics is active")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .addAction(0, "Stop", stopPendingIntent)
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "beatland_floating_lyrics"
        private const val NOTIFICATION_ID = 4271
        const val ACTION_STOP = "com.maxrave.simpmusic.floatinglyrics.ACTION_STOP"

        private const val PREFS_NAME = "beatland_floating_lyrics_prefs"
        private const val KEY_X = "window_x"
        private const val KEY_Y = "window_y"
        private const val KEY_MODE = "window_mode"
        private const val KEY_OPACITY = "window_opacity"
        private const val KEY_LOCKED = "window_locked"
        private const val KEY_AUTO_COLLAPSE = "window_auto_collapse"
        private const val MODE_MINI = "mini"
        private const val MODE_EXPANDED = "expanded"
        private const val MODE_BUBBLE = "bubble"

        private const val DISMISS_ZONE_HEIGHT_DP = 130
        private const val BUBBLE_EDGE_MARGIN_DP = 8

        fun start(context: Context) {
            val intent = Intent(context, FloatingLyricsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingLyricsService::class.java))
        }
    }
}
