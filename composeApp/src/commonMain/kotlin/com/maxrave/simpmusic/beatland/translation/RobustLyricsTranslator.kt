package com.maxrave.simpmusic.beatland.translation

import com.maxrave.domain.data.model.metadata.Line
import com.maxrave.domain.data.model.metadata.Lyrics
import com.maxrave.domain.repository.LyricsCanvasRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.logger.Logger
import kotlinx.coroutines.delay
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
 *  3. A second, bounded REPAIR pass asks again, in even smaller batches ([REPAIR_SIZE]), only for the lines that came
 *     back untranslated (the submodule fills a missing key with the original text) although they are real sentences.
 *  4. The result is rebuilt locally: exactly one line per original line, with the ORIGINAL timestamps, so the screen's
 *     match-by-time can never pair a translation with a neighbouring verse. A line without a translation is blank
 *     and the screen does not show it.
 *
 * FAILURE POLICY (what went wrong in the first version: it gave up on any failed call, so one hiccup on a small
 * retry window threw away a song that was 95 % translated). The submodule reports every failure as the same
 * "Translation failed", including "this chunk is already in the target language", so a failed call is NOT proof of a
 * broken setup. Therefore:
 *  - only the FIRST request of the song decides if the setup is unusable (key, provider, language): it is tried twice,
 *    and if it still fails the result is [Resource.Error] and nothing is saved;
 *  - any later failed call just leaves its lines untranslated (blank) — it never discards the rest;
 *  - the result is kept when at least [MIN_TRANSLATED_PERCENT] % of the translatable lines really got a translation.
 */
class RobustLyricsTranslator(
    private val lyricsCanvasRepository: LyricsCanvasRepository,
) {
    private class Entry(
        val index: Int,
        val text: String,
    )

    private class Budget(
        var calls: Int,
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
        val budget = Budget(MAX_CALLS)

        // Pass 1: the whole song, in windows.
        var first = true
        for (chunk in entries.chunked(CHUNK_SIZE)) {
            val answer = ask(chunk, lines, lyrics.syncType, targetLanguage, budget)
            if (answer == null) {
                if (first) {
                    Logger.w(TAG, "The first request failed twice; the AI setup is not usable for this song")
                    return Resource.Error("Translation failed")
                }
                continue // later failure: those lines stay untranslated, the rest is kept
            }
            first = false
            chunk.forEachIndexed { i, entry -> translations[entry.index] = answer[i] }
        }

        // Pass 2: real sentences the model left untouched.
        val missed = entries.filter { translations[it.index] == null && looksLikeASentence(it.text) }
        for (batch in missed.chunked(REPAIR_SIZE)) {
            val answer = ask(batch, lines, lyrics.syncType, targetLanguage, budget) ?: continue
            batch.forEachIndexed { i, entry -> answer[i]?.let { translations[entry.index] = it } }
        }

        val translated = entries.count { translations[it.index] != null }
        Logger.d(TAG, "Translated $translated of ${entries.size} translatable lines (${lines.size} total, ${budget.calls} calls left)")
        if (translated * 100 < entries.size * MIN_TRANSLATED_PERCENT) {
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
     * One AI request for [window], tried up to twice. Returns one slot per entry — the translation, or null when the
     * model left the line as it was or empty — or null as a whole when the call failed (or [budget] ran out).
     */
    private suspend fun ask(
        window: List<Entry>,
        lines: List<Line>,
        syncType: String?,
        language: String,
        budget: Budget,
    ): Array<String?>? {
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
        repeat(ATTEMPTS) { attempt ->
            if (budget.calls <= 0) return null
            budget.calls--
            val answer =
                try {
                    lyricsCanvasRepository.getAITranslationLyrics(request, language).firstOrNull()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.w(TAG, "AI request threw: ${e.message}")
                    null
                }
            val answered = (answer as? Resource.Success<Lyrics>)?.data?.lines
            if (answered != null && answered.size == window.size) {
                return Array(window.size) { i ->
                    val text = answered[i].words.trim()
                    if (text.isEmpty() || sameText(text, window[i].text)) null else text
                }
            }
            if (attempt < ATTEMPTS - 1) delay(RETRY_DELAY_MS)
        }
        return null
    }

    companion object {
        private const val TAG = "RobustLyricsTranslator"
        private const val CHUNK_SIZE = 20
        private const val REPAIR_SIZE = 12
        private const val ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 800L
        private const val MAX_CALLS = 40
        private const val MIN_TRANSLATED_PERCENT = 40

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

        /**
         * A real sentence worth a second try: at least three different words. "Oh oh oh", "Yeah", "La la la" and names
         * that stay the same are left alone.
         */
        private fun looksLikeASentence(text: String): Boolean {
            val words =
                text
                    .lowercase()
                    .split(Regex("[^\\p{L}]+"))
                    .filter { it.isNotEmpty() }
            return words.size >= 3 && words.toSet().size >= 3
        }
    }
}
