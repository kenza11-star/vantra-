package com.vantra.music.ui.player.terminal

import org.junit.Assert.*
import org.junit.Test

class TerminalSynchronizationTest {
    @Test fun transportBoundariesClearAudioAndDistinguishRepeatedMedia() {
        val s = TerminalSynchronization(); val player = Any()
        assertTrue(s.beginFrame(0.016f, 0, "a", player, 0, true, 1f))
        assertFalse(s.beginFrame(0.016f, 16, "a", player, 0, true, 1f))
        assertTrue(s.beginFrame(0.016f, 16, "a", player, 1, true, 1f)) // repeat/explicit seek, even at the same time
        assertTrue(s.beginFrame(0.016f, 16, "b", player, 1, true, 1f))
        assertTrue(s.beginFrame(0.016f, 32, "b", Any(), 1, true, 1f))
        assertEquals(0f, s.pulse, 0f)
        assertEquals(0f, s.tracker.energy, 0f)
    }
    @Test fun pauseResumeAndSeeksReprimeButNormalSpeedDoesNot() {
        val s = TerminalSynchronization(); val player = Any()
        s.beginFrame(0.016f, 0, "a", player, 0, true, 2f)
        assertFalse(s.beginFrame(0.016f, 32, "a", player, 0, true, 2f))
        assertTrue(s.beginFrame(0.016f, 5000, "a", player, 0, true, 2f))
        assertTrue(s.beginFrame(0.016f, 5000, "a", player, 0, false, 2f))
        assertFalse(s.beginFrame(0.1f, 5000, "a", player, 0, false, 2f))
        assertTrue(s.beginFrame(0.016f, 5000, "a", player, 0, true, 2f))
    }
    @Test fun fallbackNeverInventsOnsets() {
        val s = TerminalSynchronization()
        repeat(600) { s.react(1f/60, playing=true, enabled=true, hasLyrics=true, heard=false) }
        assertEquals(BeatSource.LYRIC_TIMING, s.source)
        assertEquals(0, s.tracker.kickCount); assertEquals(0f, s.pulse, 0f)
        s.react(0.016f, true, true, false, false)
        assertEquals(BeatSource.PLAYBACK_TIMING, s.source)
        s.react(0.016f, false, true, true, true)
        assertEquals(BeatSource.NONE, s.source)
        s.react(0.016f, true, false, true, true)
        assertEquals(BeatSource.NONE, s.source)
    }
    @Test fun actualSpectralOnsetsDrivePulseAndAudioLossClearsIt() {
        val s = TerminalSynchronization()
        repeat(240) { n ->
            val t=n/60f; val phase=t%0.5f
            for (b in s.tracker.bands.indices) s.tracker.bands[b] =
                if (b < 12) (0.1f + 0.7f*kotlin.math.exp(-phase/0.06f)) else 0.05f
            s.react(1f/60, true, true, true, true)
        }
        assertEquals(BeatSource.AUDIO, s.source); assertTrue(s.tracker.kickCount > 3)
        assertTrue(s.pulse > 0f)
        s.react(0.016f, true, true, true, false)
        assertEquals(0f, s.pulse, 0f)
        assertEquals(BeatSource.LYRIC_TIMING, s.source)
    }
    @Test fun activeBlockTransitionRetainsPreviousLineAndConvergesAtAnyRefreshRate() {
        fun run(fps:Int): Float {
            val t=TerminalBlockTransition(); t.update(0f, 1, snap=true)
            t.update(0f, 2); assertEquals(1,t.previousLine)
            assertEquals(0f,t.mix,0f)
            repeat(fps) { t.update(1f/fps,2) }
            return t.mix
        }
        assertEquals(run(30),run(120),0.002f); assertTrue(run(60)>0.99f)
    }
    @Test fun typingIsDerivedFromTimestampsAndCannotAdvanceWhilePaused() {
        val doc=TerminalDocument.build(longArrayOf(0,4000),listOf("hello world", "next line"),40)
        val c=TerminalCursor(); doc.locate(100,true,c); val n=c.typed
        repeat(120) { doc.locate(100,true,c); assertEquals(n,c.typed) }
        doc.locate(4000,true,c); assertEquals(1,c.line); assertEquals(0,c.typed)
        doc.locate(100,true,c); assertEquals(n,c.typed)
    }
    @Test fun cameraEmphasisRequiresExplicitAudioAndRemainsBriefAndBounded() {
        val muted=TerminalCamera(); val live=TerminalCamera()
        repeat(30) {
            muted.update(1f/60, 0f, 1.6f, 0f, 0f, 1f, false)
            live.update(1f/60, 0f, 1.6f, 0f, 0f, 1f, false, audioReactive=true)
        }
        assertEquals(0f,muted.beatDolly.value,0f)
        assertTrue(live.beatDolly.value in 0.005f..0.023f)
        repeat(120) { live.update(1f/60, 0f, 1.6f, 0f, 0f, 0f, false, audioReactive=true) }
        assertTrue(live.beatDolly.value < 0.0001f)
        live.update(1f/60,0f,0f,1f,1f,1f,false,audioReactive=true)
        assertEquals(0f,live.beatDolly.value,0f)
    }
}
