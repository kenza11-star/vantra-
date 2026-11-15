package com.vantra.music.ui.player.terminal

/** Geometry/presentation decisions shared by the native frame and renderer. No Android state. */
internal object TerminalPresentation {
    data class Edges(val start: Float, val top: Float, val end: Float, val bottom: Float)

    fun insets(left: Float, top: Float, right: Float, bottom: Float, rotated: Boolean, rtl: Boolean): Edges {
        val l = if (rotated) top else left
        val r = if (rotated) bottom else right
        return Edges(if (rtl) r else l, if (rotated) right else top,
            if (rtl) l else r, if (rotated) left else bottom)
    }

    fun autoHide(landscape: Boolean, playing: Boolean, menu: Boolean, touchExploration: Boolean,
                 focused: Boolean, preview: Boolean): Boolean =
        landscape && playing && !menu && !touchExploration && !focused && !preview

    fun asciiAdvance(end: Int, lyricFrom: Int, lyricTo: Int, codeCell: Float, lyricCell: Float): Float =
        minOf(end, lyricFrom) * codeCell +
            (minOf(end, lyricTo) - lyricFrom).coerceAtLeast(0) * lyricCell +
            (end - lyricTo).coerceAtLeast(0) * codeCell
}
