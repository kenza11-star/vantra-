package com.vantra.music.ui.player.terminal

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Stage 1's continuous editor remains the focus plane. Two structured code planes behind it share
 * a genuine perspective camera and screen safety correction. Text never rotates independently.
 * Layout/document wrapping stays off the draw path; paints, matrices and scratch arrays are reused.
 */
internal class TerminalRenderer {
    private val code = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val lyric = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val numbers = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.RIGHT
    }
    private val fill = Paint()
    private val guide = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private val src = FloatArray(8)
    private val dst = FloatArray(8)
    private val focusQuad = FloatArray(8)
    private val framing = TerminalFraming()
    private val backgroundMatrix = Matrix()
    private val projector = TerminalProjector()
    @Volatile var failed = false
        private set
    private var width = 0
    private var height = 0
    private var density = 1f
    private var charW = 0f
    private var lyricCharW = 0f
    private var gutter = 0f
    private var pageWidth = 0f
    private var rowHeight = 0f
    private var baseline = 0f
    private var columnCount = TerminalDocument.MinColumns
    private val viewportMotion = TerminalViewportMotion()
    private var lastDrawNs = 0L
    private val caretX = Spring()
    private val caretY = Spring()
    private var caretReady = false
    private var caretRow = Int.MIN_VALUE
    private var motionGeneration = Long.MIN_VALUE
    private var typingTimeline: TerminalTypingTimeline? = null
    private var typingTimeMs = 0L
    private var typingRate = 1f
    private var typingOn = false
    private var viewport = IntArray(0)
    private var viewportDoc: TerminalDocument? = null
    private var viewportLine = Int.MIN_VALUE
    private var viewportCursorRow = Int.MIN_VALUE
    private var viewportBudget = 0
    private var viewportIdle = false

    fun layout(w: Int, h: Int, landscape: Boolean, density: Float = 1f, fontScale: Float = 1f): Int {
        if (w <= 0 || h <= 0) { width = 0; height = 0; caretReady = false; return TerminalDocument.MinColumns }
        // A font/orientation change invalidates caret geometry even at the same pixel size.
        caretReady = false
        if (width != w || height != h || this.density != density) caretReady = false
        width = w; height = h
        this.density = density
        val sp = density * fontScale
        // 18sp lyrics / 13sp code upright; sideways has more document context at 16sp / 12sp.
        lyric.textSize = (if (landscape) 16f else 18f) * sp
        code.textSize = (if (landscape) 12f else 13f) * sp
        numbers.textSize = 10f * sp
        charW = code.measureText("M")
        gutter = max(30f * density, numbers.measureText("9999") + 12f * density)
        pageWidth = w * 0.84f
        // Constrain font scale only when a very narrow preview cannot hold the minimum document.
        val available = max(1f, pageWidth - gutter - 12f * density)
        val minimum = lyric.measureText("M") * TerminalDocument.MinColumns
        if (minimum > available) {
            val fit = available / minimum
            lyric.textSize *= fit
            code.textSize *= fit
            numbers.textSize *= fit
            charW = code.measureText("M")
        }
        lyricCharW = lyric.measureText("M")
        columnCount = max(TerminalDocument.MinColumns, floor(available / lyricCharW).toInt())
        rowHeight = max(lyric.fontSpacing * 1.18f, lyric.textSize * 1.48f)
        baseline = (rowHeight - (lyric.descent() - lyric.ascent())) / 2f - lyric.ascent()
        guide.strokeWidth = max(1f, density * 0.5f)
        return columnCount
    }

    /** Conservative cell width for document wrapping, including Android's actual fallback fonts. */
    fun measureColumns(text: String): Float {
        val cell = lyricCharW
        return if (cell > 0f) lyric.measureText(text) / cell else text.length.toFloat()
    }

    fun visibleRows(): Int = max(3, floor(height / max(1f, rowHeight)).toInt() - 2)

    /** Same drawing interface as V2: camera, scrolling spring, beat light and typing caret survive. */
    fun draw(
        canvas: Canvas, doc: TerminalDocument, cursor: TerminalCursor, scroll: Float,
        camera: TerminalCamera, timeNs: Long, low: Boolean, landscape: Boolean,
        glowLevel: Float, solidCursor: Boolean,
        activeMix: Float = 1f, previousLine: Int = Int.MIN_VALUE,
        generation: Long = 0L, motionEnabled: Boolean = true,
        typing: TerminalTypingTimeline? = null, typingPositionMs: Long = 0L,
        typingSpeed: Float = 1f, typingEnabled: Boolean = false, codeCaret: TerminalCodeCaret? = null,
    ) {
        if (failed) return
        typingTimeline = typing; typingTimeMs = typingPositionMs; typingRate = typingSpeed; typingOn = typingEnabled
        val save = canvas.save()
        try {
            canvas.drawColor(android.graphics.Color.BLACK)
            if (width <= 0 || height <= 0 || charW <= 0f) return
            val resetMotion = generation != motionGeneration
            motionGeneration = generation
            if (resetMotion) caretReady = false
            val mix = activeMix.coerceIn(0f, 1f)
            val textLeft = gutter + 10f * density
            val focusY = height * 0.48f
            val budget = visibleRows()
            val viewportAnchor = doc.viewportAnchor(cursor, budget)
            if (viewportDoc !== doc || viewportLine != cursor.line || viewportCursorRow != viewportAnchor ||
                viewportBudget != budget || viewportIdle != cursor.idle) {
                val next = doc.viewportRows(cursor, budget)
                if (viewportDoc === doc) viewportMotion.change(viewport, next, scroll, cursor.row)
                else viewportMotion.offset.snap(0f)
                viewport = next
                viewportDoc = doc
                viewportLine = cursor.line
                viewportCursorRow = viewportAnchor
                viewportBudget = budget
                viewportIdle = cursor.idle
            }
            val dt = if (lastDrawNs == 0L) 0f else ((timeNs - lastDrawNs) / 1e9f).coerceIn(0f, 0.1f)
            lastDrawNs = timeNs
            if (!motionEnabled || resetMotion) viewportMotion.offset.snap(0f)
            else viewportMotion.offset.step(dt, 0f, 36f, 12f)
            val rowOffset = viewportMotion.offset.value * rowHeight
            val focus = viewportPosition(scroll)
            var activeTop = focusY
            var activeBottom = focusY + rowHeight
            if (!cursor.idle) {
                var found = false
                for (slot in viewport.indices) {
                    val row = doc.rows[viewport[slot]]
                    if (row.line == cursor.line && row.kind == KindLyric) {
                        val top = focusY + (slot - focus) * rowHeight + rowOffset
                        if (!found) { activeTop = top; found = true }
                        activeBottom = top + rowHeight
                    }
                }
            }
            if (!placePage(camera, landscape, activeTop, activeBottom)) return
            canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
            // Painter order: far, middle, editor. LOW keeps one rear plane.
            if (!low) drawDepthPlane(canvas, doc, scroll, 0.95f, -0.66f, 0.17f)
            drawDepthPlane(canvas, doc, scroll, 0.45f, 0.66f, 0.23f)
            canvas.concat(matrix)
            // A lyric can lie outside the old local page while scrolling catches up. The
            // projected safe band already fits it; do not clip it away in pre-projection space.
            val pageTop = min(0f, activeTop)
            val pageBottom = max(height.toFloat(), activeBottom)
            canvas.clipRect(0f, pageTop, pageWidth, pageBottom)
            // A dark editor backing prevents rear code from competing with real words.
            fill.color = color(0x000000, 0.92f)
            canvas.drawRect(0f, pageTop, pageWidth, pageBottom, fill)
            guide.color = 0xFF20252B.toInt()
            canvas.drawLine(gutter, 0f, gutter, height.toFloat(), guide)
            val blink = solidCursor || ((timeNs / BlinkNs) and 1L) == 0L
            for (slot in viewport.indices) {
                val r = viewport[slot]
                val row = doc.rows[r]
                val top = focusY + (slot - focus) * rowHeight + rowOffset
                val active = row.line == cursor.line && row.line >= 0
                val activeLyric = active && row.kind == KindLyric && !cursor.idle
                val neighbor = row.kind == KindLyric && abs(row.line - cursor.line) <= 1
                val distance = abs(top + rowHeight / 2f - height / 2f) / max(1f, height / 2f)
                val edge = (1f - 0.35f * distance).coerceIn(0.55f, 1f)
                val previous = row.line == previousLine && previousLine >= 0
                if (activeLyric || (previous && row.kind == KindLyric)) {
                    val emphasis = if (activeLyric) mix else 1f - mix
                    fill.color = color(0xFFFFFF, (0.055f + 0.02f * (if (low) 0f else glowLevel.coerceIn(0f, 1f))) * emphasis)
                    canvas.drawRect(gutter + density, top, pageWidth, top + rowHeight, fill)
                    fill.color = color(TerminalPalette.Cursor, emphasis)
                    canvas.drawRect(gutter + density, top + 3f * density, gutter + 2.5f * density, top + rowHeight - 3f * density, fill)
                }
                numbers.color = color(TerminalPalette.Gutter, if (activeLyric) 1f else edge * 0.65f)
                canvas.drawText(row.lineNo, gutter - 8f * density, top + baseline, numbers)
                val revealed = revealed(row, r)
                if (!low) {
                    var indent = 0
                    while (indent < revealed && row.text[indent] == ' ') indent++
                    guide.color = color(0x637180, if (active) 0.28f else 0.12f)
                    for (column in 2..indent step 2) {
                        val x = textLeft + (column - 2) * charW
                        canvas.drawLine(x, top, x, top + rowHeight, guide)
                    }
                }
                // The original syntax spans are clipped by a timeline-derived character boundary.
                // No complete future string is painted under the reveal.
                drawRow(canvas, row, textLeft, top + baseline, edge, activeLyric, active, neighbor, mix, previous, revealed)
            }
            // A single eased caret follows measured glyph positions; wrapping resets x rather than
            // sweeping diagonally across the whole editor. No independent cursor animation loop.
            var targetRow = codeCaret?.row ?: cursor.row
            var targetCol = codeCaret?.col ?: cursor.col
            if (targetRow !in viewport) { targetRow=cursor.row;targetCol=cursor.col }
            val caretSlot = viewport.indexOf(targetRow)
            if (caretSlot >= 0 || cursor.idle) {
                val row = doc.rows.getOrNull(targetRow)
                if(row!=null) targetCol=min(targetCol,revealed(row,targetRow))
                val x = textLeft + if (row == null) 0f else xAt(row, targetCol)
                val y = focusY + (viewportPosition(targetRow.toFloat()) - focus) * rowHeight + rowOffset + baseline
                if (!caretReady || !motionEnabled) {
                    caretX.snap(x); caretY.snap(y); caretReady = true
                } else {
                    if (caretRow != targetRow) caretX.snap(x) else caretX.step(dt, x, 400f, 40f)
                    caretY.step(dt, y, 225f, 30f)
                }
                caretRow = targetRow
                if (blink) {
                    fill.color = TerminalPalette.Cursor
                    canvas.drawRect(caretX.value, caretY.value + lyric.ascent(),
                        caretX.value + max(density, lyric.textSize * 0.065f), caretY.value + lyric.descent(), fill)
                }
            }

        } catch (error: RuntimeException) {
            android.util.Log.e("VantraTerminal", "Editor rendering failed", error)
            failed = true
        } finally {
            canvas.restoreToCount(save)
        }
    }

    /** Interpolate the existing row-index scroll spring across folded line-number gaps. */
    private fun viewportPosition(row: Float): Float = viewportMotion.position(viewport, row)

    private fun drawRow(canvas: Canvas, row: TerminalRow, left: Float, y: Float, edge: Float, activeLyric: Boolean, active: Boolean, neighbor: Boolean, mix: Float, previous: Boolean, revealed: Int) {
        var k = 0
        while (k < row.spans.size) {
            val start = row.spans[k]
            val end = min(row.spans[k + 1], revealed)
            if (start >= end) break
            val words = row.spans[k + 3] and TerminalStyle.Lyric != 0
            val paint = if (words) lyric else code
            val alpha = when {
                activeLyric && words -> 0.72f + 0.28f * mix
                previous && words -> (0.58f + 0.42f * (1f - mix)) * edge
                words && neighbor -> 0.58f * edge
                words -> 0.29f * edge
                active -> (0.42f + 0.28f * mix) * edge
                previous -> (0.42f + 0.28f * (1f - mix)) * edge
                row.kind == KindHeader -> 0.65f * edge
                else -> 0.42f * edge
            }
            paint.color = color(row.spans[k + 2], alpha)
            canvas.drawText(row.text, start, end, left + xAt(row, start), y, paint)
            k += TerminalSyntax.Stride
        }
    }

    /** The leading code uses the code font; strings use the lyric font. Both share one baseline. */
    private fun xAt(row: TerminalRow, column: Int): Float {
        val end = column.coerceIn(0, row.text.length)
        if (row.ascii) {
            return if (row.kind != KindLyric) end * charW else
                TerminalPresentation.asciiAdvance(end, row.lyricFrom, row.lyricTo, charW, lyricCharW)
        }
        if (row.kind != KindLyric || end <= row.lyricFrom) return code.measureText(row.text, 0, end)
        var x = code.measureText(row.text, 0, row.lyricFrom)
        x += lyric.measureText(row.text, row.lyricFrom, min(end, row.lyricTo))
        if (end > row.lyricTo) x += code.measureText(row.text, row.lyricTo, end)
        return x
    }

    /** Projection of an entire world plane, using caller-owned matrix storage. */
    private fun projectPlane(z: Float, offsetX: Float, out: Matrix): Boolean {
        val halfW = pageWidth / 2f
        val halfH = height / 2f
        if (!projector.project(-halfW + offsetX, -halfH, z, dst, 0) ||
            !projector.project(halfW + offsetX, -halfH, z, dst, 2) ||
            !projector.project(halfW + offsetX, halfH, z, dst, 4) ||
            !projector.project(-halfW + offsetX, halfH, z, dst, 6)) return false
        src[0] = 0f; src[1] = 0f; src[2] = pageWidth; src[3] = 0f
        src[4] = pageWidth; src[5] = height.toFloat(); src[6] = 0f; src[7] = height.toFloat()
        return out.setPolyToPoly(src, 0, dst, 0, 4)
    }

    private var focalPx = 1000f

    private fun placePage(camera: TerminalCamera, landscape: Boolean, top: Float, bottom: Float): Boolean {
        // A longer portrait lens protects narrow text; landscape admits more lateral depth.
        focalPx = width * (if (landscape) 1.1f else 1.65f)
        projector.setup(width / 2f, height / 2f, focalPx, camera)
        if (!projectPlane(0f, 0f, matrix)) return false
        val x = pageWidth / 2f
        val t = top - height / 2f
        val b = bottom - height / 2f
        if (!projector.project(-x, t, 0f, focusQuad, 0) ||
            !projector.project(x, t, 0f, focusQuad, 2) ||
            !projector.project(x, b, 0f, focusQuad, 4) ||
            !projector.project(-x, b, 0f, focusQuad, 6)) return false
        framing.fit(dst, focusQuad, width.toFloat(), height.toFloat())
        correctFraming(matrix)
        return true
    }

    private fun correctFraming(out: Matrix) {
        out.postScale(framing.scale, framing.scale)
        out.postTranslate(framing.dx, framing.dy)
    }

    private fun drawDepthPlane(canvas: Canvas, doc: TerminalDocument, scroll: Float,
                               depth: Float, lateral: Float, opacity: Float) {
        val z = focalPx * depth
        if (!projectPlane(z, pageWidth * lateral, backgroundMatrix)) return
        correctFraming(backgroundMatrix)
        val depthFade = projector.scaleAt(z).coerceIn(0f, 1f)
        val saved = canvas.save()
        try {
            canvas.concat(backgroundMatrix)
            canvas.clipRect(0f, 0f, pageWidth, height.toFloat())
            guide.color = color(TerminalPalette.Gutter, opacity * depthFade)
            canvas.drawLine(gutter, 0f, gutter, height.toFloat(), guide)
            // Use actual document scaffolding, without duplicating timed lyric strings behind them.
            val centre = floor(scroll).toInt()
            val radius = min(14, visibleRows())
            val first = max(0, centre - radius)
            val last = min(doc.rows.lastIndex, centre + radius)
            for (r in first..last) {
                val row = doc.rows[r]
                if (row.kind == KindLyric) continue
                val y = height * 0.48f + (r - scroll) * rowHeight + baseline
                if (y < 0f || y > height + rowHeight) continue
                numbers.color = color(TerminalPalette.Gutter, opacity * depthFade * 0.65f)
                canvas.drawText(row.lineNo, gutter - 8f * density, y, numbers)
                val revealed = revealed(row, r)
                var k = 0
                while (k < row.spans.size) {
                    val end = min(row.spans[k + 1], revealed)
                    if (row.spans[k] >= end) break
                    code.color = color(row.spans[k + 2], opacity * depthFade)
                    canvas.drawText(row.text, row.spans[k], end,
                        gutter + 10f * density + xAt(row, row.spans[k]), y, code)
                    k += TerminalSyntax.Stride
                }
            }
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    private fun revealed(row: TerminalRow, index: Int): Int =
        typingTimeline?.visibleChars(index, typingTimeMs, typingRate, typingOn) ?: row.text.length

    private fun color(rgb: Int, alpha: Float): Int = ((alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24) or (rgb and 0xFFFFFF)
    private companion object { const val BlinkNs = 530_000_000L }
}
