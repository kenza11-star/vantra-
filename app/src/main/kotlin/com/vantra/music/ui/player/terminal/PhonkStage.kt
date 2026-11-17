package com.vantra.music.ui.player.terminal

import com.vantra.music.lyrics.LyricsEntry
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** One to three words on the screen at a time, from [startMs] until the next chunk takes over. */
internal class PhonkChunk(val words: List<String>, val startMs: Long, val endMs: Long, val line: Int, val id: Int)

/** Cuts synced lyric lines into the short phrases a phonk lyric video shows, one per beat. */
internal object PhonkChunker {
    private const val MaxChars = 13
    private const val MinChunkMs = 300L
    private const val FallbackLineMs = 4_500L

    /** how long the last chunk of a line stays up while the next line is still far away */
    private const val HoldMs = 2_800L

    fun chunks(lines: List<LyricsEntry>): List<PhonkChunk> {
        val out = ArrayList<PhonkChunk>()
        var id = 0
        for (i in lines.indices) {
            val line = lines[i]
            val words = line.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) continue
            val start = line.time
            val next = lines.getOrNull(i + 1)?.time
            val interval = ((next ?: (start + FallbackLineMs)) - start).coerceAtLeast(1L)
            // the words take about as long as they would be sung, never longer than the line itself
            val sung = max(1_800L, words.size * 500L + 800L)
            val span = min(interval * 92 / 100, sung).coerceAtLeast(1L)
            var groups = group(words)
            while (groups.size > 1 && span / groups.size < MinChunkMs) groups = mergeShortest(groups)
            val weights = IntArray(groups.size) { g -> groups[g].sumOf { it.length } + 4 }
            val total = weights.sum().toFloat()
            var cursor = start.toFloat()
            for (g in groups.indices) {
                val share = span * weights[g] / total
                val chunkStart = cursor.toLong()
                cursor += share
                val chunkEnd = if (g < groups.lastIndex) cursor.toLong()
                else min(next ?: Long.MAX_VALUE, cursor.toLong() + HoldMs)
                out.add(PhonkChunk(groups[g], chunkStart, max(chunkEnd, chunkStart + 1), i, id++))
            }
        }
        return out
    }

    /** index of the chunk on screen at [timeMs], or -1 between phrases (the idle screen) */
    fun at(chunks: List<PhonkChunk>, timeMs: Long): Int {
        var lo = 0
        var hi = chunks.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (chunks[mid].startMs <= timeMs) lo = mid + 1 else hi = mid
        }
        val i = lo - 1
        return if (i >= 0 && timeMs < chunks[i].endMs) i else -1
    }

    private fun group(words: List<String>): List<List<String>> {
        val out = ArrayList<List<String>>()
        var current = ArrayList<String>()
        var chars = 0
        for (w in words) {
            if (current.isNotEmpty() && (current.size >= 3 || chars + 1 + w.length > MaxChars)) {
                out.add(current)
                current = ArrayList()
                chars = 0
            }
            if (current.isNotEmpty()) chars += 1
            current.add(w)
            chars += w.length
        }
        if (current.isNotEmpty()) out.add(current)
        // a lonely tiny last word goes with the phrase before it
        if (out.size > 1 && out.last().size == 1 && out.last()[0].length <= 2) {
            val tail = out.removeAt(out.lastIndex)
            out[out.lastIndex] = out.last() + tail
        }
        return out
    }

    private fun mergeShortest(groups: List<List<String>>): List<List<String>> {
        var best = 0
        var bestLen = Int.MAX_VALUE
        for (i in 0 until groups.size - 1) {
            val len = groups[i].sumOf { it.length } + groups[i + 1].sumOf { it.length }
            if (len < bestLen) { bestLen = len; best = i }
        }
        val out = ArrayList<List<String>>()
        for (i in groups.indices) {
            if (i == best) out.add(groups[i] + groups[i + 1]) else if (i != best + 1) out.add(groups[i])
        }
        return out
    }
}

/**
 * Which chunk is on screen. The lyric timing says when a phrase is due; the beat decides the
 * exact frame: a phrase that is about to start waits for the next kick, but never longer than a
 * moment past its time, so the words land on the beat without drifting from the song.
 */
internal class PhonkTimeline {
    var index = -1
        private set

    /** seconds the current chunk has been on screen */
    var age = 0f
        private set

    fun reset() { index = -1; age = 0f }

    /** @return true when the chunk on screen changed this frame */
    fun step(chunks: List<PhonkChunk>, timeMs: Long, dt: Float, kick: Boolean, jump: Boolean): Boolean {
        age += dt.coerceIn(0f, 0.1f)
        if (chunks.isEmpty()) {
            val had = index != -1
            index = -1
            return had
        }
        if (jump) {
            val target = PhonkChunker.at(chunks, timeMs)
            val changed = target != index
            index = target
            age = 0f
            return changed
        }
        val due = PhonkChunker.at(chunks, timeMs + SnapAheadMs)
        if (due == index) return false
        if (due < index || due == -1) {
            index = if (due == -1) PhonkChunker.at(chunks, timeMs) else due
            age = 0f
            return true
        }
        if (kick || timeMs >= chunks[due].startMs + SnapLateMs) {
            index = due
            age = 0f
            return true
        }
        return false
    }

    private companion object {
        const val SnapAheadMs = 110L
        const val SnapLateMs = 130L
    }
}

/** What the renderer draws for one frame of the beat: written by [PhonkBeat.update]. */
internal class PhonkFx {
    /** background level of the beat flash: 1 white, then a grey step, then black (0) */
    var flash = 0f
    var punch = 0f
    var tilt = 0f
    var lines = 0f
    var blur = 0f
    var rumble = 0f

    /** progress 0..1 of the shockwave ring and of its echo, -1 when they are not out */
    var ring = -1f
    var ring2 = -1f
    var ringPower = 0f
    var seed = 0

    fun clear() {
        flash = 0f; punch = 0f; tilt = 0f; lines = 0f; blur = 0f; rumble = 0f
        ring = -1f; ring2 = -1f; ringPower = 0f
    }

    /** nothing moving: the frame loop may rest */
    fun isQuiet(): Boolean = flash <= 0f && punch < 0.01f && lines < 0.01f && ring < 0f && ring2 < 0f && rumble < 0.02f
}

/**
 * The beat of a phonk / montagem edit. Every kick punches the picture, sends a shockwave ring and
 * its echo out of the middle and tilts the frame; every kick also cuts the screen to white, then a
 * grey step, then back to black. The flash is limited to under three a second.
 */
internal class PhonkBeat {
    private var punch = 0f
    private var lines = 0f
    private var flashAge = 99f
    private var sinceFlash = 99f
    private var ringAge = 99f
    private var ringPower = 0f
    private var count = 0
    private var tiltSign = 1f
    private var seed = 0

    var flashCount = 0
        private set
    var minFlashGap = 99f
        private set
    var kickCount = 0
        private set

    fun reset() {
        punch = 0f; lines = 0f
        flashAge = 99f; sinceFlash = 99f; ringAge = 99f
        ringPower = 0f
    }

    /**
     * @param kick strength of a kick in this very frame, else 0
     * @param bass smoothed bass level 0..1, for the constant rumble
     * @param flashScale 0 no flash, below 1 a soft one, 1 the full cut
     * @param gain the user's bass response
     * @param motion false when motion is off or reduced: nothing moves
     */
    fun update(dt: Float, kick: Float, bass: Float, flashScale: Float, gain: Float, motion: Boolean, out: PhonkFx) {
        val h = dt.coerceIn(0.001f, 0.1f)
        if (!motion) {
            reset()
            out.clear()
            return
        }
        sinceFlash += h
        flashAge += h
        ringAge += h
        punch *= exp(-h / 0.14f)
        lines *= exp(-h / 0.22f)
        val g = gain.coerceIn(0f, 1.6f)
        if (kick > 0f) {
            val strength = kick.coerceIn(0.25f, 1f)
            val power = ((0.55f + 0.45f * strength) * g).coerceIn(0f, 1.4f)
            count++
            kickCount++
            // the first beat of every four, and any very hard kick, hits harder
            val accent = count % 4 == 1 || strength > 0.85f
            punch = max(punch, if (accent) power * 1.15f else power)
            tiltSign = -tiltSign
            ringAge = 0f
            ringPower = if (accent) power * 1.2f else power
            if (accent) lines = max(lines, power)
            seed++
            if (sinceFlash >= FlashGap) {
                if (sinceFlash < minFlashGap) minFlashGap = sinceFlash
                flashAge = 0f
                sinceFlash = 0f
                flashCount++
            }
        }
        out.flash = (flashLevel(flashAge) * flashScale.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        out.punch = punch.coerceIn(0f, 1.6f)
        out.tilt = tiltSign * punch * 0.022f
        out.lines = lines.coerceIn(0f, 1f)
        out.blur = punch.coerceIn(0f, 1f)
        out.rumble = (bass * g).coerceIn(0f, 1.2f)
        out.ring = if (ringAge < RingSpan) ringAge / RingSpan else -1f
        val echo = ringAge - RingEcho
        out.ring2 = if (echo >= 0f && echo < RingSpan) echo / RingSpan else -1f
        out.ringPower = ringPower
        out.seed = seed
    }

    /** hard steps, no ramps: white, a grey step, then black */
    private fun flashLevel(t: Float): Float = when {
        t < WhiteHold -> 1f
        t < WhiteHold + GreyHold -> 0.36f
        else -> 0f
    }

    private companion object {
        /** under three flashes a second, for photosensitive viewers */
        const val FlashGap = 0.34f
        const val WhiteHold = 0.07f
        const val GreyHold = 0.05f
        const val RingSpan = 0.55f
        const val RingEcho = 0.12f
    }
}
