package com.vantra.music.ui.player.terminal

import com.vantra.music.lyrics.LyricsEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhonkStageTest {
    private val lines = listOf(
        LyricsEntry(0, "Quand cet homme est venu me voir pour parler"),
        LyricsEntry(3500, "Et même s'il était tard, qu'on m'attendait pour dîner"),
        LyricsEntry(9000, "Je savais que c'était lui que j'attendais"),
        LyricsEntry(30000, "Hey"),
    )

    @Test fun linesBecomeShortPhrasesThatTileTheirLineAndNeverRunIntoTheNext() {
        val chunks = PhonkChunker.chunks(lines)
        assertTrue(chunks.size > lines.size)
        for (c in chunks) {
            assertTrue(c.words.isNotEmpty())
            assertTrue("phrase too long: ${c.words}", c.words.size <= 3 || c.words.joinToString(" ").length <= 26)
            assertTrue(c.endMs > c.startMs)
        }
        for (i in 1 until chunks.size) {
            assertTrue(chunks[i].startMs >= chunks[i - 1].startMs)
            assertTrue("chunk $i overlaps", chunks[i - 1].endMs <= chunks[i].startMs || chunks[i - 1].line == chunks[i].line)
        }
        for (line in lines.indices.drop(1)) {
            val before = chunks.filter { it.line == line - 1 }
            assertTrue(before.all { it.endMs <= lines[line].time })
        }
        // each phrase is on screen long enough to read
        assertTrue(chunks.filter { it.line < 3 }.all { it.endMs - it.startMs >= 300 })
    }

    @Test fun phraseLookupFindsTheRightChunkAndTheIdleGaps() {
        val chunks = PhonkChunker.chunks(lines)
        assertEquals(0, PhonkChunker.at(chunks, 0))
        val first = chunks.first { it.line == 1 }
        assertEquals(chunks.indexOf(first), PhonkChunker.at(chunks, 3500))
        assertEquals(-1, PhonkChunker.at(chunks, 25_000))
        assertEquals(chunks.size - 1, PhonkChunker.at(chunks, 30_100))
    }

    @Test fun aPhraseLandsOnTheKickNearItsTimeAndNeverDriftsFarFromIt() {
        val chunks = PhonkChunker.chunks(lines)
        val timeline = PhonkTimeline()
        var landedOnKick = 0
        var landings = 0
        var worstLate = 0L
        var worstEarly = 0L
        var t = 0L
        var sinceKick = 0L
        while (t < 12_000) {
            sinceKick += 16
            val kick = sinceKick >= 395
            if (kick) sinceKick -= 395
            val before = timeline.index
            val changed = timeline.step(chunks, t, 0.016f, kick, false)
            if (changed && timeline.index > before) {
                landings++
                if (kick) landedOnKick++
                val due = chunks[timeline.index].startMs
                worstLate = maxOf(worstLate, t - due)
                worstEarly = minOf(worstEarly, t - due)
            }
            t += 16
        }
        assertTrue("landings=$landings", landings >= 8)
        assertTrue("worst late=$worstLate", worstLate <= 160)
        assertTrue("worst early=$worstEarly", worstEarly >= -120)
        assertTrue("on kick $landedOnKick of $landings", landedOnKick * 2 >= landings)
        // a seek goes straight to the phrase for that time
        assertTrue(timeline.step(chunks, 3500, 0.016f, false, true) || timeline.index == PhonkChunker.at(chunks, 3500))
        assertEquals(PhonkChunker.at(chunks, 3500), timeline.index)
    }

    private fun beats(seconds: Float, every: Float, flashScale: Float = 1f, gain: Float = 1f): Triple<PhonkBeat, PhonkFx, FloatArray> {
        val beat = PhonkBeat()
        val fx = PhonkFx()
        val seen = FloatArray(4)
        var since = 99f
        repeat((seconds * 60f).toInt()) {
            since += 1f / 60f
            var kick = 0f
            if (since >= every) { kick = 0.8f; since -= every }
            beat.update(1f / 60f, kick, 0.5f, flashScale, gain, true, fx)
            if (fx.flash > 0.01f) seen[0] = 1f
            if (fx.punch > 0.3f) seen[1] = 1f
            if (fx.ring >= 0f) seen[2] = 1f
            if (fx.ring2 >= 0f) seen[3] = 1f
        }
        return Triple(beat, fx, seen)
    }

    @Test fun everyKickPunchesFlashesAndSendsARingWithItsEchoUnderThreeFlashesASecond() {
        val (beat, _, seen) = beats(10f, 0.395f)
        assertTrue(seen.all { it == 1f })
        assertTrue("flashes=${beat.flashCount} kicks=${beat.kickCount}", beat.flashCount >= beat.kickCount - 2)
        assertTrue("min gap=${beat.minFlashGap}", beat.minFlashGap >= 0.33f)
        // very fast kicks still punch, but the flash holds back
        val (fast, _, _) = beats(10f, 0.2f)
        assertTrue(fast.minFlashGap >= 0.33f)
        assertTrue(fast.flashCount < fast.kickCount)
    }

    @Test fun theFlashIsWhiteThenGreyThenBlackWithNoInBetweenLevels() {
        val beat = PhonkBeat()
        val fx = PhonkFx()
        val levels = HashSet<Int>()
        var since = 99f
        repeat(600) {
            since += 1f / 60f
            var kick = 0f
            if (since >= 0.4f) { kick = 0.8f; since -= 0.4f }
            beat.update(1f / 60f, kick, 0.5f, 1f, 1f, true, fx)
            levels.add((fx.flash * 100f).toInt())
        }
        assertEquals(setOf(0, 36, 100), levels)
    }

    @Test fun flashOffKeepsTheScreenDarkSoftDimsItAndReducedMotionStopsEverything() {
        val (_, off, _) = beats(5f, 0.4f, flashScale = 0f)
        assertEquals(0f, off.flash, 0f)
        val beat = PhonkBeat()
        val fx = PhonkFx()
        var peak = 0f
        var since = 99f
        repeat(300) {
            since += 1f / 60f
            var kick = 0f
            if (since >= 0.4f) { kick = 0.8f; since -= 0.4f }
            beat.update(1f / 60f, kick, 0.5f, 0.4f, 1f, true, fx)
            peak = maxOf(peak, fx.flash)
        }
        assertTrue("soft peak=$peak", peak > 0.3f && peak <= 0.41f)
        beat.update(1f / 60f, 0.8f, 0.5f, 1f, 1f, false, fx)
        assertEquals(0f, fx.punch, 0f)
        assertEquals(0f, fx.flash, 0f)
        assertEquals(-1f, fx.ring, 0f)
        assertTrue(fx.isQuiet())
    }
}
