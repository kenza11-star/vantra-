package com.vantra.music.ui.screens.constellation

import com.vantra.music.db.entities.LyricsEntity
import com.vantra.music.lyrics.LyricsUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** One word of the song, with when it is sung and where its star sits (0..1 of the screen). */
internal class CWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val x: Float,
    val y: Float,
)

/** [links] holds, for each word, the earlier words its star is joined to. */
internal class CGraph(
    val words: List<CWord>,
    val links: List<IntArray>,
    val hasLyrics: Boolean,
)

private class RawWord(val text: String, val start: Long, val end: Long)

private const val XMin = 0.10f
private const val XMax = 0.90f
private const val YMin = 0.36f
private const val YMax = 0.84f

private fun isCjk(c: Char): Boolean = c.code >= 0x2E80

private fun tokenise(text: String): List<String> {
    val out = ArrayList<String>()
    for (token in text.split(Regex("\\s+"))) {
        if (token.isBlank()) continue
        if (token.length > 8 && token.all { isCjk(it) }) {
            token.chunked(3).forEach { out.add(it) }
        } else {
            out.add(token)
        }
    }
    return out
}

private fun spread(tokens: List<String>, start: Long, end: Long): List<RawWord> {
    if (tokens.isEmpty()) return emptyList()
    val span = (end - start).coerceAtLeast(tokens.size * 160L)
    val weights = tokens.map { it.length + 2 }
    val total = weights.sum().toFloat()
    var cursor = start.toFloat()
    val out = ArrayList<RawWord>(tokens.size)
    for ((i, token) in tokens.withIndex()) {
        val share = span * weights[i] / total
        out.add(RawWord(token, cursor.toLong(), (cursor + share).toLong()))
        cursor += share
    }
    return out
}

private fun rawWords(lyrics: String?, durationMs: Long, fallback: List<String>): Pair<List<RawWord>, Boolean> {
    val entries = if (lyrics.isNullOrBlank() || lyrics == LyricsEntity.LYRICS_NOT_FOUND) {
        emptyList()
    } else {
        runCatching { LyricsUtils.parseLyricsCached(lyrics) }.getOrDefault(emptyList())
    }
    val lines = entries.filter { it.text.isNotBlank() }
    if (lines.isEmpty()) {
        // nothing to sing: let the title and the artist drift through the stars instead of an empty screen
        val base = fallback.flatMap { tokenise(it) }.ifEmpty { listOf("♪") }
        val out = ArrayList<RawWord>()
        var t = 800L
        repeat(60) { i ->
            val w = base[i % base.size]
            out.add(RawWord(w, t, t + 2000L))
            t += 2000L
        }
        return out to false
    }
    val synced = lines.size >= 2 && (lines.last().time - lines.first().time) > lines.size * 300L
    val out = ArrayList<RawWord>()
    if (synced) {
        for ((i, e) in lines.withIndex()) {
            val start = e.time
            val end = minOf(lines.getOrNull(i + 1)?.time ?: (start + 4000L), start + 8000L)
            val timed = e.words?.filter { it.text.isNotBlank() }.orEmpty()
            if (timed.isNotEmpty()) {
                for (w in timed) {
                    val s = (w.startTime * 1000.0).toLong()
                    val en = (w.endTime * 1000.0).toLong().coerceAtLeast(s + 120L)
                    out.add(RawWord(w.text.trim(), s, en))
                }
            } else {
                out.addAll(spread(tokenise(e.text), start, end))
            }
        }
    } else {
        val tokens = lines.flatMap { tokenise(it.text) }
        val total = if (durationMs > 0) durationMs else 180_000L
        out.addAll(spread(tokens, 3000L, (total - 2000L).coerceAtLeast(6000L)))
    }
    val sorted = out.filter { it.text.isNotBlank() }.sortedBy { it.start }
    return sorted to true
}

internal fun buildGraph(lyrics: String?, durationMs: Long, fallback: List<String>, seedText: String): CGraph {
    val (raw, has) = rawWords(lyrics, durationMs, fallback)
    val rnd = Random(seedText.hashCode())
    var x = 0.5f
    var y = 0.6f
    val words = ArrayList<CWord>(raw.size)
    val links = ArrayList<IntArray>(raw.size)
    for ((j, r) in raw.withIndex()) {
        val angle = rnd.nextFloat() * (2f * PI.toFloat())
        val step = 0.10f + rnd.nextFloat() * 0.16f
        var nx = x + cos(angle) * step
        var ny = y + sin(angle) * step * 1.3f
        if (nx < XMin) nx = XMin + (XMin - nx)
        if (nx > XMax) nx = XMax - (nx - XMax)
        if (ny < YMin) ny = YMin + (YMin - ny)
        if (ny > YMax) ny = YMax - (ny - YMax)
        nx = nx.coerceIn(XMin, XMax)
        ny = ny.coerceIn(YMin, YMax)
        x = nx
        y = ny
        words.add(CWord(r.text, r.start, maxOf(r.end, r.start + 100L), nx, ny))
        val l = ArrayList<Int>(3)
        if (j >= 1) l.add(j - 1)
        if (j >= 2 && rnd.nextFloat() < 0.40f) l.add(j - 2)
        if (j >= 3 && rnd.nextFloat() < 0.20f) l.add(j - 3)
        links.add(l.toIntArray())
    }
    return CGraph(words, links, has)
}
