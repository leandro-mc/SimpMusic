package com.maxrave.simpmusic.beatland.translation

import com.maxrave.domain.data.model.metadata.Line
import com.maxrave.domain.data.model.metadata.Lyrics
import com.maxrave.domain.repository.LyricsCanvasRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.logger.Logger
import kotlinx.coroutines.flow.firstOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Beatland fork feature (D-028): a safety layer around the AI lyrics translation of the `core` submodule.
 *
 * The submodule sends the model one JSON object `{ "0": "line", "1": "line", ... }` for the WHOLE song and trusts
 * that the answer keeps every key. In practice models skip lines that are not text ("...", "♪", "[Instrumental]"),
 * merge short lines or renumber keys, and from that point on every translation is attached to the wrong verse.
 * `core` cannot be edited, so this class fixes the problem from outside:
 *
 *  1. Lines with nothing to translate (no letters: dots, music symbols, dashes; or a bracketed section marker such
 *     as "[Instrumental]") are NEVER sent. Without them the model has nothing to skip or renumber.
 *  2. The rest is sent in small windows ([CHUNK_SIZE] lines) instead of the whole song, so a slip cannot drift far.
 *  3. Every answer is checked. A line that came back untranslated (the submodule fills missing keys with the original
 *     text) or empty is "bad". A window with bad lines is split in halves and asked again, down to [MIN_SPLIT] lines.
 *  4. The result is rebuilt locally: exactly one line per original line, with the ORIGINAL timestamps, so the screen's
 *     match-by-time can never pair a translation with a neighbouring verse. Lines without a translation are blank.
 *
 * It returns [Resource.Error] — so nothing is saved — when the first call fails (wrong key, same language, no
 * network) or when too little of the song ended up translated.
 */
class RobustLyricsTranslator(
    private val lyricsCanvasRepository: LyricsCanvasRepository,
) {
    private class Entry(
        val index: Int,
        val text: String,
    )

    suspend fun translate(
        lyrics: Lyrics,
        targetLanguage: String,
    ): Resource<Lyrics> {
        val lines = lyrics.lines.orEmpty()
        val entries =
            lines.mapIndexedNotNull { index, line ->
                val text = line.words.trim()
                if (isTranslatable(text)) Entry(index, text) else null
            }
        if (entries.isEmpty()) return Resource.Error("Nothing to translate")

        val translations = arrayOfNulls<String>(lines.size)
        var failedCalls = 0
        var firstWindow = true
        for (chunk in entries.chunked(CHUNK_SIZE)) {
            val result = translateWindow(chunk, lines, lyrics.syncType, targetLanguage, depth = 0) { failedCalls++ }
            if (firstWindow) {
                firstWindow = false
                // The very first request failing means the setup is unusable (key, provider, same language...).
                if (failedCalls > 0) return Resource.Error("Translation failed")
            }
            chunk.forEachIndexed { i, entry -> translations[entry.index] = result[i] }
            if (failedCalls >= MAX_FAILED_CALLS) break
        }

        val covered = entries.count { translations[it.index] != null }
        if (covered * 100 < entries.size * MIN_COVERAGE_PERCENT) {
            Logger.w(TAG, "Only $covered/${entries.size} lines translated; not keeping the result")
            return Resource.Error("Translation incomplete")
        }
        val rebuilt =
            lines.mapIndexed { index, line ->
                Line(
                    startTimeMs = line.startTimeMs,
                    endTimeMs = line.endTimeMs,
                    words = translations[index].orEmpty(),
                    syllables = null,
                )
            }
        return Resource.Success(Lyrics(error = false, lines = rebuilt, syncType = lyrics.syncType))
    }

    /**
     * Translates [window]. Returns one slot per entry: the translation, "" when the line legitimately needs none
     * (an interjection that stays the same), or null when it could not be translated.
     */
    private suspend fun translateWindow(
        window: List<Entry>,
        lines: List<Line>,
        syncType: String?,
        language: String,
        depth: Int,
        onCallFailed: () -> Unit,
    ): Array<String?> {
        val result =
            requestWindow(window, lines, syncType, language)
                ?: run {
                    // The call itself failed (key, quota, network, same language): splitting would only repeat it.
                    onCallFailed()
                    return arrayOfNulls(window.size)
                }
        if (result.none { it == null }) return result
        if (depth >= MAX_DEPTH || window.size < MIN_SPLIT) return result
        val mid = window.size / 2
        val merged =
            translateWindow(window.subList(0, mid), lines, syncType, language, depth + 1, onCallFailed) +
                translateWindow(window.subList(mid, window.size), lines, syncType, language, depth + 1, onCallFailed)
        return if (merged.count { it == null } <= result.count { it == null }) merged else result
    }

    /** One AI request for [window]. Returns null when the call failed or the answer cannot be lined up with it. */
    private suspend fun requestWindow(
        window: List<Entry>,
        lines: List<Line>,
        syncType: String?,
        language: String,
    ): Array<String?>? {
        val out = arrayOfNulls<String>(window.size)
        val request =
            Lyrics(
                error = false,
                lines =
                    window.map { entry ->
                        Line(
                            startTimeMs = lines[entry.index].startTimeMs,
                            endTimeMs = lines[entry.index].endTimeMs,
                            words = entry.text,
                            syllables = null,
                        )
                    },
                syncType = syncType,
            )
        val answer =
            try {
                lyricsCanvasRepository.getAITranslationLyrics(request, language).firstOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(TAG, "AI request failed: ${e.message}")
                null
            }
        val answered = (answer as? Resource.Success<Lyrics>)?.data?.lines
        if (answered == null || answered.size != window.size) return null
        window.forEachIndexed { i, entry ->
            val text = answered[i].words.trim()
            out[i] =
                when {
                    text.isEmpty() -> null
                    sameText(text, entry.text) -> if (isShortInterjection(entry.text)) "" else null
                    else -> text
                }
        }
        return out
    }

    companion object {
        private const val TAG = "RobustLyricsTranslator"
        private const val CHUNK_SIZE = 20
        private const val MAX_DEPTH = 2
        private const val MIN_SPLIT = 4
        private const val MAX_FAILED_CALLS = 4
        private const val MIN_COVERAGE_PERCENT = 70

        private val SECTION_MARKERS =
            setOf(
                "music", "instrumental", "intro", "outro", "solo", "bridge", "chorus", "pre-chorus", "pre chorus",
                "verse", "interlude", "applause", "silence", "break", "hook", "refrain", "ad lib", "ad-lib", "guitar solo",
            )

        /** True when the line has something to translate: at least one letter and it is not a bare section marker. */
        internal fun isTranslatable(text: String): Boolean {
            if (text.none { it.isLetter() }) return false // "...", "♪", "—", "• • •"
            val bracketed =
                (text.startsWith("[") && text.endsWith("]")) ||
                    (text.startsWith("(") && text.endsWith(")")) ||
                    (text.startsWith("{") && text.endsWith("}"))
            if (bracketed && text.length <= 40) {
                val inner = text.substring(1, text.length - 1).trim().lowercase()
                if (SECTION_MARKERS.any { inner == it || inner.startsWith("$it ") || inner.endsWith(" $it") }) return false
            }
            return true
        }

        private fun normalize(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

        private fun sameText(
            a: String,
            b: String,
        ): Boolean = normalize(a) == normalize(b)

        /** "Oh oh oh", "Yeah", names: an unchanged answer is fine and not worth another request. */
        private fun isShortInterjection(text: String): Boolean = text.trim().split(Regex("\\s+")).size <= 2
    }
}
