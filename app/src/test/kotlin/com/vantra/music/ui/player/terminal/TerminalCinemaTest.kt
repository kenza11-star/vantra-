package com.vantra.music.ui.player.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalCinemaTest {
    /** Feeds the controller at 60 fps with a kick every [kickEvery] seconds (0 = none). */
    private class Sim {
        val cinema = TerminalCinema()
        val fx = TerminalFx()
        private var sinceKick = 99f

        fun step(seconds: Float, kickEvery: Float, bass: Float, drive: Float,
                 idle: Boolean = false, climax: Float = 0f, audio: Boolean = true, motion: Boolean = true) {
            repeat((seconds * 60f).toInt()) {
                sinceKick += 1f / 60f
                var kick = 0f
                if (kickEvery > 0f && sinceKick >= kickEvery) { kick = 0.8f; sinceKick -= kickEvery }
                cinema.update(1f / 60f, kick, bass, drive, 0.6f, audio, motion, true, idle, true, climax, false, 1f, fx)
            }
        }
    }

    @Test fun steadyHeavyKickLocksPhonkModeWithAFlashRateThatStaysSafe() {
        val s = Sim()
        s.step(8f, 0.395f, 0.5f, 0.5f)
        assertTrue("phonk=${s.fx.phonk}", s.fx.phonk > 0.9f)
        assertTrue("bpm=${s.cinema.bpm}", kotlin.math.abs(s.cinema.bpm - 152f) < 4f)
        assertTrue("flashes=${s.cinema.flashCount}", s.cinema.flashCount in 5..21)
    }

    @Test fun irregularKicksAndSlowBalladsNeverTurnTheEffectsOn() {
        val s = Sim()
        val rnd = java.util.Random(3)
        var t = 0f
        var next = 0.5f
        var peak = 0f
        repeat(1200) {
            t += 1f / 60f
            var kick = 0f
            if (t >= next) { kick = 0.8f; next = t + 0.2f + rnd.nextFloat() * 1.2f }
            s.cinema.update(1f / 60f, kick, 0.5f, 0.5f, 0.6f, true, true, true, false, true, 0f, false, 1f, s.fx)
            peak = maxOf(peak, s.fx.phonk)
        }
        assertTrue("irregular phonk=$peak", peak < 0.3f)
        val ballad = Sim()
        ballad.step(20f, 1.0f, 0.5f, 0.5f)
        assertEquals(0f, ballad.fx.phonk, 0.01f)
        assertEquals(0f, ballad.fx.flash, 0.01f)
    }

    @Test fun flashesNeverComeFasterThanAboutTwoAndAHalfPerSecond() {
        val s = Sim()
        s.step(10f, 0.2f, 0.5f, 0.5f)
        assertTrue("flashes=${s.cinema.flashCount}", s.cinema.flashCount <= 26)
    }

    @Test fun breakdownPullsBackToTheWallAndTheReturningKickIsADrop() {
        val s = Sim()
        s.step(12f, 0.395f, 0.5f, 0.5f)
        assertTrue("close=${s.fx.close}", s.fx.close > 0.9f)
        s.step(5f, 0f, 0.04f, 0.05f)
        assertTrue("wall=${s.fx.wall}", s.fx.wall > 0.5f)
        assertTrue("orb=${s.fx.orb}", s.fx.orb > 0.4f)
        s.step(0.1f, 0.395f, 0.5f, 0.5f)
        assertEquals(1, s.cinema.dropCount)
        assertTrue("flash=${s.fx.flash}", s.fx.flash > 0.8f)
        s.step(1.5f, 0.395f, 0.5f, 0.5f)
        assertTrue("wall=${s.fx.wall}", s.fx.wall < 0.3f)
        assertTrue("close=${s.fx.close}", s.fx.close > 0.5f)
    }

    @Test fun shotsFollowTheLyricsAndStayOffWithoutThem() {
        val s = Sim()
        s.step(1.5f, 0f, 0f, 0f)
        assertTrue(s.fx.close > 0.9f)
        s.step(3f, 0f, 0f, 0f, idle = true)
        assertTrue(s.fx.wall > 0.9f)
        assertTrue(s.fx.close < 0.1f)
        val bare = TerminalCinema(); val fx = TerminalFx()
        repeat(120) { bare.update(1f / 60f, 0f, 0f, 0f, 0f, false, true, true, false, false, 0f, false, 1f, fx) }
        assertEquals(0f, fx.close, 0.001f)
        assertEquals(0f, fx.wall, 0.001f)
    }

    @Test fun reducedMotionAndMissingAudioProduceNoEffects() {
        val s = Sim()
        s.step(8f, 0.395f, 0.5f, 0.5f)
        s.step(0.5f, 0.395f, 0.5f, 0.5f, motion = false)
        assertEquals(0f, s.fx.close, 0f)
        assertEquals(0f, s.fx.phonk, 0f)
        assertEquals(0f, s.fx.flash, 0f)
        val quiet = Sim()
        quiet.step(8f, 0.395f, 0.5f, 0.5f, audio = false)
        assertEquals(0f, quiet.fx.phonk, 0f)
        assertEquals(0, quiet.cinema.flashCount)
    }
}
