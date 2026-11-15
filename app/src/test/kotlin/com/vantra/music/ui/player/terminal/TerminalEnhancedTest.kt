package com.vantra.music.ui.player.terminal

import org.junit.Assert.*
import org.junit.Test

class TerminalEnhancedTest {
    private fun doc()=TerminalDocument.build(longArrayOf(1000,5000),listOf("Aku masih di sini", "Kita pulang"),60)
    @Test fun completeCodeActuallyRevealsPrefixesAndKeepsPastBlocks() {
        val d=doc(); val t=TerminalTypingTimeline(d)
        val row=d.lines[0].groupFirst
        assertEquals(0,t.visibleChars(row,0,1f,true))
        var partial=false;var last=0
        for(ms in 0L..5000L step 5) {
            val n=t.visibleChars(row,ms,1f,true)
            assertTrue(n>=last);last=n
            if(n in 1 until d.rows[row].text.length) partial=true
        }
        assertTrue("code declaration/comment must really type",partial)
        assertEquals(d.rows[row].text.length,t.visibleChars(row,6000,1f,true))
    }
    @Test fun lyricCharactersStartAtTheirRealTimestampAndSeekIsPure() {
        val d=doc();val t=TerminalTypingTimeline(d);val row=d.lines[0].lyricFirst
        assertTrue(t.visibleChars(row,999,1f,true)<=d.rows[row].lyricFrom)
        val a=t.visibleChars(row,1300,1f,true)
        t.visibleChars(row,6000,1f,true)
        assertEquals(a,t.visibleChars(row,1300,1f,true))
        assertEquals(d.rows[row].text.length,t.visibleChars(row,1300,1f,false))
    }
    @Test fun unicodeRevealNeverSplitsSurrogatesAndFastIntervalsFinish() {
        val d=TerminalDocument.build(longArrayOf(0,250,500),listOf("🎵e\u0301".repeat(20),"next", "last"),30)
        val t=TerminalTypingTimeline(d)
        for(ms in 0L..500L step 7) for(r in d.rows.indices) {
            val n=t.visibleChars(r,ms,0.65f,true)
            assertTrue(n==0 || !d.rows[r].text[n-1].isHighSurrogate())
            assertTrue(n==d.rows[r].text.length || d.rows[r].text[n]!='\u0301')
        }
        val first=d.lines[0]
        for(r in first.groupFirst until first.groupFirst+first.groupCount)
            assertEquals(d.rows[r].text.length,t.visibleChars(r,250,0.65f,true))
    }
    @Test fun typingSpeedAndCaretFollowOneTimeline() {
        val d=doc();val t=TerminalTypingTimeline(d);val r=d.lines[0].lyricFirst
        assertTrue(t.visibleChars(r,1400,1.6f,true)>=t.visibleChars(r,1400,0.65f,true))
        val c=TerminalCodeCaret();t.locate(1400,1f,true,c)
        assertEquals(t.visibleChars(c.row,1400,1f,true),c.col)
        repeat(60) { t.locate(1400,1f,true,c);assertEquals(t.visibleChars(c.row,1400,1f,true),c.col) }
    }
    @Test fun markersValidateAndReconstructWithoutKeywordInference() {
        val p=TerminalEmphasis.parse("00:12.500 | push | 1.0 | 2400\n00:20.000 | still | 0.6 | 1800")
        assertNull(p.error);assertEquals(2,p.markers.size)
        assertNotNull(TerminalEmphasis.parse("00:12.500 | heartbreak | 1 | 2000").error)
        assertNotNull(TerminalEmphasis.parse("00:12.500 | push | 4 | 2000").error)
        val f=TerminalEmphasisFrame()
        TerminalEmphasis.sample(p.markers,12500,1f,f);val peak=f.zoom
        assertTrue(peak>0f)
        TerminalEmphasis.sample(p.markers,19000,1f,f);assertEquals(0f,f.zoom,0f)
        TerminalEmphasis.sample(p.markers,12500,1f,f);assertEquals(peak,f.zoom,0f)
        TerminalEmphasis.sample(emptyList(),12500,1f,f);assertEquals(0f,f.zoom,0f)
    }
    @Test fun emotionalEnvelopeIsContinuousAndStillMarkersSuppressMotion() {
        val p=TerminalEmphasis.parse("00:05.000 | power | 1.0 | 2400\n00:10.000 | still | 1.0 | 2000")
        val f=TerminalEmphasisFrame();var last=0f
        for(ms in 4000L..8000L step 10) {
            TerminalEmphasis.sample(p.markers,ms,1f,f)
            assertTrue(kotlin.math.abs(f.zoom-last)<0.012f);last=f.zoom
        }
        TerminalEmphasis.sample(p.markers,10000,1f,f);assertTrue(f.stillness>0.99f)
    }
    @Test fun upperBandTransientsAreNotBassBeats() {
        val b=BeatTracker()
        repeat(600) { n ->
            for(i in b.bands.indices) b.bands[i]=if(i<8) 0.3f else if(i<12 && n%30<3) 0.95f else 0.1f
            b.update(1f/60,true)
        }
        assertEquals("steady bass plus changing upper bins must not kick",0,b.kickCount)
    }
    @Test fun everyNonemptyCodeRowCanProgressWithoutSeparateTextFrames() {
        val d=doc();val t=TerminalTypingTimeline(d)
        for(r in d.rows.indices) {
            if(d.rows[r].text.length<2) continue
            var partial=false
            for(ms in 0L..8500L step 2) {
                val n=t.visibleChars(r,ms,1f,true)
                if(n in 1 until d.rows[r].text.length) {partial=true;break}
            }
            assertTrue("row $r never progressively revealed: ${d.rows[r].text}",partial)
        }
    }
    @Test fun enhancedBassUsesOneStrongBoundedImpulseThenReleases() {
        val c=TerminalCamera();val intent=TerminalCameraIntent();intent.onset=1f
        var peak=0f
        repeat(120) {n ->
            c.update(1f/60,0f,1f,0f,0f,0f,false,audioReactive=true,intent=intent)
            peak=maxOf(peak,c.beatDolly.value);intent.onset=0f
        }
        assertTrue("peak=$peak",peak in 0.025f..0.09f)
        assertTrue(c.beatDolly.value<0.0001f)
    }
    @Test fun focusWinsCameraBudgetAndUnreadableExtremesStayBounded() {
        val c=TerminalCamera();val intent=TerminalCameraIntent()
        intent.focusZoom=0.13f;intent.focusWeight=1f;intent.bassGain=1.6f
        repeat(360) {n ->
            intent.onset=if(n%30==0) 1f else 0f
            c.update(1f/60,n/60f,1.6f,1f,1f,1f,true,audioReactive=true,intent=intent)
            assertTrue(c.combinedDolly()<=0.18001f)
        }
        assertEquals(0.13f,c.emotionalDolly.value,0.001f)
        assertTrue(c.beatDolly.value<=0.05f)
    }
    @Test fun unsupportedAudioCannotCreateEnhancedImpulsesAndStillnessSettles() {
        val c=TerminalCamera();val intent=TerminalCameraIntent()
        intent.focusZoom=0.1f;intent.focusWeight=1f;intent.onset=1f;intent.stillness=1f
        repeat(240) {n -> c.update(1f/60,n/60f,1f,0f,0f,1f,false,audioReactive=false,intent=intent) }
        assertEquals(0f,c.beatDolly.value,0f)
        assertEquals(0f,c.roll.value,0.0001f)
        assertEquals(0.1f,c.emotionalDolly.value,0.001f)
    }
    @Test fun enhancedCameraSafetyStillFitsBothOrientations() {
        for((w,h) in listOf(360f to 620f,820f to 260f)) {
            val c=TerminalCamera();c.yaw.snap(0.18f);c.pitch.snap(-0.12f);c.roll.snap(0.013f)
            c.dolly.snap(0.06f);c.emotionalDolly.snap(0.13f);c.beatDolly.snap(0.09f)
            val p=TerminalProjector();p.setup(w/2,h/2,w*(if(w>h) 1.1f else 1.65f),c)
            val page=FloatArray(8);val focus=FloatArray(8);val x=w*0.42f
            val xs=floatArrayOf(-x,x,x,-x)
            val ys=floatArrayOf(-h/2,-h/2,h/2,h/2)
            val fy=floatArrayOf(-h*0.25f,-h*0.25f,h*0.25f,h*0.25f)
            repeat(4) {n -> assertTrue(p.project(xs[n],ys[n],0f,page,n*2));assertTrue(p.project(xs[n],fy[n],0f,focus,n*2)) }
            val f=TerminalFraming();f.fit(page,focus,w,h)
            repeat(4) {n ->
                assertTrue(focus[n*2]*f.scale+f.dx in w*0.04f-0.1f..w*0.96f+0.1f)
                assertTrue(focus[n*2+1]*f.scale+f.dy in h*0.12f-0.1f..h*0.88f+0.1f)
            }
        }
    }
    @Test fun newImpulseSettlesConsistentlyAtThirtyAndOneTwentyHertz() {
        fun run(fps:Int):Float {
            val c=TerminalCamera();val i=TerminalCameraIntent();i.onset=1f
            repeat(fps/2) {n ->c.update(1f/fps,n.toFloat()/fps,1f,0f,0f,0f,false,audioReactive=true,intent=i);i.onset=0f}
            return c.beatDolly.value
        }
        assertEquals(run(30),run(120),0.001f)
    }
    @Test fun oversizedLyricViewportFollowsTheActualFullCodeCaret() {
        val d=TerminalDocument.build(longArrayOf(1000,7000),listOf("long lyric words ".repeat(80),"next"),22)
        val t=TerminalTypingTimeline(d);val c=TerminalCursor();val caret=TerminalCodeCaret()
        var checked=0
        for(ms in 1300L..3000L step 100) {
            d.locate(ms,true,c);t.locate(ms,0.65f,true,caret);t.focusCursor(c,caret)
            if(d.rows[caret.row].kind==KindLyric && d.rows[caret.row].line==c.line) {
                assertEquals(caret.row,c.row);checked++
            }
        }
        assertTrue(checked>0)
    }
    @Test fun cameraReachesTheManualPeakWithoutASecondSlowZoomFilter() {
        val markers=TerminalEmphasis.parse("00:01.000 | push | 1 | 2400").markers
        val f=TerminalEmphasisFrame();val intent=TerminalCameraIntent();val c=TerminalCamera()
        for(n in 0..60) {
            TerminalEmphasis.sample(markers,n*1000L/60,1f,f)
            intent.focusZoom=f.zoom;intent.focusWeight=f.weight
            c.update(1f/60,n/60f,1f,0f,0f,0f,false,intent=intent)
        }
        assertEquals(0.075f,c.emotionalDolly.value,0.0001f)
    }

    @Test fun wordsAreTypedAtAHumanPaceNotInABurst() {
        val d=doc();val t=TerminalTypingTimeline(d);val r=d.lines[0].lyricFirst
        val to=d.rows[r].lyricTo
        var done=-1L
        for(ms in 1000L..5000L step 5) if(t.visibleChars(r,ms,1f,true)>=to) { done=ms;break }
        assertTrue("words never finished",done>0)
        assertTrue("typed too fast: ${done-1000}ms",done-1000>=450)
        assertTrue("must finish before the next line starts",done<5000)
    }

    @Test fun keyInFlightFadesInBetweenWholeCharacters() {
        val d=doc();val t=TerminalTypingTimeline(d);val c=TerminalCodeCaret()
        var sawFraction=false;var last=-1
        for(ms in 1000L..2500L) {
            t.locate(ms,1f,true,c)
            assertTrue(c.next>=c.col)
            assertTrue(c.frac in 0f..1f)
            if(c.next>c.col && c.frac>0.05f && c.frac<0.95f) sawFraction=true
            if(c.row==d.lines[0].lyricFirst) { assertTrue(c.col>=last);last=c.col }
        }
        assertTrue("no letter was ever caught mid-stroke",sawFraction)
        t.locate(1400,1f,false,c)
        assertEquals(c.col,c.next);assertEquals(1f,c.frac,0f)
    }

    @Test fun playClockRunsSteadilyBetweenCoarsePositionReports() {
        val c=TerminalPlayClock()
        assertEquals(1000L,c.update(0.016f,1000L,true,1f,true))
        var reported=1000L;var last=1000L;var biggest=0L
        for(frame in 1..120) {
            // the player only reports every 5th frame, in 80 ms steps
            if(frame%5==0) reported+=80
            val v=c.update(0.016f,reported,true,1f,false)
            assertTrue(v>=last);biggest=maxOf(biggest,v-last);last=v
        }
        assertTrue("steps must stay small: $biggest",biggest<=40)
        assertTrue(kotlin.math.abs(last-reported)<120)
        assertEquals(5000L,c.update(0.016f,5000L,false,1f,false))
        assertEquals(9000L,c.update(0.016f,9000L,true,1f,false))
    }
}
