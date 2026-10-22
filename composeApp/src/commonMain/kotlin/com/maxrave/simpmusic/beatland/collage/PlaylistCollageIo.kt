package com.maxrave.simpmusic.beatland.collage

/**
 * Beatland fork feature (D-027): platform half of the automatic playlist collage.
 *
 * Downloads up to four cover images and returns one square JPEG with them laid out as a collage:
 * 1 image = full, 2 = two vertical halves, 3 = one tall half + two stacked quarters, 4 = 2x2 grid.
 * Every image is centre-cropped to fill its cell. Returns null when no image could be loaded, so the
 * caller simply tries again later.
 */
expect suspend fun composePlaylistCollage(imageUrls: List<String>): ByteArray?

/** Deletes a collage file previously written by the app. Ignores anything that is not a `collage_` file. */
expect suspend fun deleteCollageFile(uri: String)
