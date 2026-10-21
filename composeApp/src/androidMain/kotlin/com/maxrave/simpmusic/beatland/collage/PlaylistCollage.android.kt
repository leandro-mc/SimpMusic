package com.maxrave.simpmusic.beatland.collage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.maxrave.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.math.max

private const val TAG = "PlaylistCollage"
private const val SIZE = 600
private const val TIMEOUT_MS = 8_000

actual suspend fun composePlaylistCollage(imageUrls: List<String>): ByteArray? =
    withContext(Dispatchers.IO) {
        val bitmaps = imageUrls.take(4).mapNotNull { download(it) }
        if (bitmaps.isEmpty()) return@withContext null
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(out)
            canvas.drawColor(Color.BLACK)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            val cells = cellsFor(bitmaps.size)
            bitmaps.forEachIndexed { i, bitmap -> drawCenterCrop(canvas, bitmap, cells[i], paint) }
            ByteArrayOutputStream().use { stream ->
                out.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                stream.toByteArray()
            }
        } finally {
            bitmaps.forEach { it.recycle() }
            out.recycle()
        }
    }

actual suspend fun deleteCollageFile(uri: String) {
    withContext(Dispatchers.IO) {
        runCatching {
            val file = File(URI(uri))
            if (file.name.startsWith("collage_")) file.delete()
        }
    }
}

private fun cellsFor(count: Int): List<Rect> {
    val half = SIZE / 2
    return when (count) {
        1 -> listOf(Rect(0, 0, SIZE, SIZE))
        2 -> listOf(Rect(0, 0, half, SIZE), Rect(half, 0, SIZE, SIZE))
        3 -> listOf(Rect(0, 0, half, SIZE), Rect(half, 0, SIZE, half), Rect(half, half, SIZE, SIZE))
        else ->
            listOf(
                Rect(0, 0, half, half),
                Rect(half, 0, SIZE, half),
                Rect(0, half, half, SIZE),
                Rect(half, half, SIZE, SIZE),
            )
    }
}

private fun drawCenterCrop(
    canvas: Canvas,
    bitmap: Bitmap,
    cell: Rect,
    paint: Paint,
) {
    val scale = max(cell.width().toFloat() / bitmap.width, cell.height().toFloat() / bitmap.height)
    val srcW = (cell.width() / scale).toInt().coerceIn(1, bitmap.width)
    val srcH = (cell.height() / scale).toInt().coerceIn(1, bitmap.height)
    val left = (bitmap.width - srcW) / 2
    val top = (bitmap.height - srcH) / 2
    canvas.drawBitmap(bitmap, Rect(left, top, left + srcW, top + srcH), cell, paint)
}

private fun download(url: String): Bitmap? {
    if (!url.startsWith("http")) return null
    return runCatching {
        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
        try {
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            connection.disconnect()
        }
    }.onFailure { Logger.w(TAG, "Could not load $url: ${it.message}") }.getOrNull()
}
