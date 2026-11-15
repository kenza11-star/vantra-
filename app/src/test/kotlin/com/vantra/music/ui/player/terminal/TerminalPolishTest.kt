package com.vantra.music.ui.player.terminal

import org.junit.Assert.*
import org.junit.Test

class TerminalPolishTest {
    @Test fun ordinaryWrappedLyricsKeepTheSameViewportKey() {
        val doc=TerminalDocument.build(longArrayOf(0),listOf("one two three four five six seven eight nine ten eleven twelve"),22)
        val c=TerminalCursor(); doc.locate(0,true,c)
        val first=doc.viewportAnchor(c,20)
        c.row=doc.lines[0].lyricFirst+1
        assertEquals(first,doc.viewportAnchor(c,20))
    }
    @Test fun oversizedLyricsStillAdvanceTheirViewport() {
        val doc=TerminalDocument.build(longArrayOf(0),listOf("words ".repeat(200)),22)
        val c=TerminalCursor(); doc.locate(0,true,c)
        val first=doc.viewportAnchor(c,6)
        c.row=doc.lines[0].lyricFirst+20
        assertTrue(first!=doc.viewportAnchor(c,6))
    }
    @Test fun clockwiseInsetsProtectEveryCutoutAndNavigationEdge() {
        val p=TerminalPresentation.insets(10f,20f,30f,40f,false,false)
        assertEquals(listOf(10f,20f,30f,40f),listOf(p.start,p.top,p.end,p.bottom))
        val l=TerminalPresentation.insets(10f,20f,30f,40f,true,false)
        assertEquals(listOf(20f,30f,40f,10f),listOf(l.start,l.top,l.end,l.bottom))
        val r=TerminalPresentation.insets(10f,20f,30f,40f,true,true)
        assertEquals(listOf(40f,30f,20f,10f),listOf(r.start,r.top,r.end,r.bottom))
    }
    @Test fun controlsRemainAvailableForAccessibilityMenuFocusAndPause() {
        assertTrue(TerminalPresentation.autoHide(true,true,false,false,false,false))
        assertFalse(TerminalPresentation.autoHide(false,true,false,false,false,false))
        assertFalse(TerminalPresentation.autoHide(true,false,false,false,false,false))
        assertFalse(TerminalPresentation.autoHide(true,true,true,false,false,false))
        assertFalse(TerminalPresentation.autoHide(true,true,false,true,false,false))
        assertFalse(TerminalPresentation.autoHide(true,true,false,false,true,false))
        assertFalse(TerminalPresentation.autoHide(true,true,false,false,false,true))
    }
    @Test fun asciiMetricsRespectSeparateCodeAndLyricFonts() {
        assertEquals(40f,TerminalPresentation.asciiAdvance(8,2,6,4f,6f),0f)
        assertEquals(4f,TerminalPresentation.asciiAdvance(1,2,6,4f,6f),0f)
        assertEquals(20f,TerminalPresentation.asciiAdvance(4,2,6,4f,6f),0f)
    }
    @Test fun controlCharactersUseMeasuredFallbackInsteadOfAssumedMonospaceCells() {
        val doc=TerminalDocument.build(longArrayOf(0),listOf("A\u0001B"),60)
        val row=doc.rows.first { it.kind == KindLyric }
        assertFalse(row.ascii)
        assertTrue(row.text.contains('\u0001'))
    }
}
