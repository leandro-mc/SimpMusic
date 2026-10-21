package com.maxrave.simpmusic.beatland.collage

import com.maxrave.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.URL
import javax.imageio.ImageIO
import kotlin.math.max

private const val TAG = "PlaylistCollage"
private const val SIZE = 600
private const val TIMEOUT_MS = 8_000

actual suspend fun composePlaylistCollage(imageUrls: List<String>): ByteArray? =
    withContext(Dispatchers.IO) {
        val images = imageUrls.take(4).mapNotNull { download(it) }
        if (images.isEmpty()) return@withContext null
        val out = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.color = Color.BLACK
            g.fillRect(0, 0, SIZE, SIZE)
            val cells = cellsFor(images.size)
            images.forEachIndexed { i, image -> drawCenterCrop(g, image, cells[i]) }
        } finally {
            g.dispose()
        }
        ByteArrayOutputStream().use { stream ->
            ImageIO.write(out, "jpg", stream)
            stream.toByteArray()
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

private fun cellsFor(count: Int): List<Rectangle> {
    val half = SIZE / 2
    return when (count) {
        1 -> listOf(Rectangle(0, 0, SIZE, SIZE))
        2 -> listOf(Rectangle(0, 0, half, SIZE), Rectangle(half, 0, half, SIZE))
        3 -> listOf(Rectangle(0, 0, half, SIZE), Rectangle(half, 0, half, half), Rectangle(half, half, half, half))
        else ->
            listOf(
                Rectangle(0, 0, half, half),
                Rectangle(half, 0, half, half),
                Rectangle(0, half, half, half),
                Rectangle(half, half, half, half),
            )
    }
}

private fun drawCenterCrop(
    g: java.awt.Graphics2D,
    image: BufferedImage,
    cell: Rectangle,
) {
    val scale = max(cell.width.toDouble() / image.width, cell.height.toDouble() / image.height)
    val srcW = (cell.width / scale).toInt().coerceIn(1, image.width)
    val srcH = (cell.height / scale).toInt().coerceIn(1, image.height)
    val left = (image.width - srcW) / 2
    val top = (image.height - srcH) / 2
    g.drawImage(
        image,
        cell.x,
        cell.y,
        cell.x + cell.width,
        cell.y + cell.height,
        left,
        top,
        left + srcW,
        top + srcH,
        null,
    )
}

private fun download(url: String): BufferedImage? {
    if (!url.startsWith("http")) return null
    return runCatching {
        val connection =
            URL(url).openConnection().apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
        connection.getInputStream().use { ImageIO.read(it) }
    }.onFailure { Logger.w(TAG, "Could not load $url: ${it.message}") }.getOrNull()
}
