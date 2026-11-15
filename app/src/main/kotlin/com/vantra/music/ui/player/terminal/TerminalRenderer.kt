package com.vantra.music.ui.player.terminal

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.cos
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
    private val band = Paint()
    // The editor card: smooth (anti-aliased) rounded fill, a hairline edge and soft fades into black.
    private val card = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fadeTop = Paint()
    private val fadeBottom = Paint()
    private var fadeHeight = 0f
    private var shadedH = 0f
    private var shadedRow = 0f
    private var bandLeft = -1f
    private var bandRight = -1f
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
        shade()
        return columnCount
    }

    /** Shaders are rebuilt only when the size or the page geometry changes, never per frame. */
    private fun shade() {
        if (height > 0 && (shadedH != height.toFloat() || shadedRow != rowHeight)) {
            shadedH = height.toFloat(); shadedRow = rowHeight
            fadeHeight = max(rowHeight * 1.8f, height * 0.12f)
            fadeTop.shader = LinearGradient(0f, 0f, 0f, fadeHeight,
                0xFF000000.toInt(), 0x00000000.toInt(), Shader.TileMode.CLAMP)
            fadeBottom.shader = LinearGradient(0f, height - fadeHeight, 0f, height.toFloat(),
                0x00000000.toInt(), 0xFF000000.toInt(), Shader.TileMode.CLAMP)
            // brighter at the top, like light catching the upper edge of the glass
            cardEdge.shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                0x38FFFFFF.toInt(), 0x0DFFFFFF.toInt(), Shader.TileMode.CLAMP)
            cardEdge.strokeWidth = max(1f, density * 0.75f)
        }
        if (bandLeft != gutter || bandRight != pageWidth) {
            bandLeft = gutter; bandRight = pageWidth
            band.shader = LinearGradient(gutter, 0f, pageWidth, 0f,
                0x388BE9FD.toInt(), 0x068BE9FD.toInt(), Shader.TileMode.CLAMP)
        }
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
            // True black: on an AMOLED panel those pixels are off, and nothing tints the background.
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
            // An opaque black card keeps rear code from competing with real words. It is rounded and
            // anti-aliased; the hairline edge and the fades are drawn over the rows further down.
            val radius = 14f * density
            card.color = android.graphics.Color.BLACK
            canvas.drawRoundRect(0f, pageTop, pageWidth, pageBottom, radius, radius, card)
            guide.color = color(0xFFFFFF, 0.07f)
            canvas.drawLine(gutter, 0f, gutter, height.toFloat(), guide)
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
                    band.alpha = ((0.8f + 0.2f * (if (low) 0f else glowLevel.coerceIn(0f, 1f))) * emphasis * 255f)
                        .toInt().coerceIn(0, 255)
                    canvas.drawRect(gutter + density, top, pageWidth, top + rowHeight, band)
                    fill.color = color(TerminalPalette.Cursor, emphasis)
                    canvas.drawRect(gutter + density, top + 3f * density, gutter + 2.5f * density, top + rowHeight - 3f * density, fill)
                }
                numbers.color = color(TerminalPalette.Gutter, if (activeLyric) 1f else edge * 0.65f)
                canvas.drawText(row.lineNo, gutter - 8f * density, top + baseline, numbers)
                val revealed = revealed(row, r)
                if (!low) {
                    var indent = 0
                    while (indent < revealed && row.text[indent] == ' ') indent++
                    guide.color = color(0x8A8F98, if (active) 0.26f else 0.11f)
                    for (column in 2..indent step 2) {
                        val x = textLeft + (column - 2) * charW
                        canvas.drawLine(x, top, x, top + rowHeight, guide)
                    }
                }
                // The original syntax spans are clipped by a timeline-derived character boundary.
                // No complete future string is painted under the reveal.
                // The key going down right now: its letter fades in just below the baseline.
                val strike = codeCaret?.takeIf { typingOn && it.active && r == it.row && it.next > revealed && it.frac < 1f }
                drawRow(canvas, row, textLeft, top + baseline, edge, activeLyric, active, neighbor, mix, previous, revealed,
                    strike?.next ?: 0, strike?.let { easeOut(it.frac) } ?: 1f, !low)
            }
            // Rows dissolve into the black at the top and bottom of the card instead of being cut off.
            canvas.drawRect(0f, pageTop, pageWidth, fadeHeight, fadeTop)
            canvas.drawRect(0f, height - fadeHeight, pageWidth, pageBottom, fadeBottom)
            // Hairline edge, then a quiet window title: three dots and a file name.
            val inset = cardEdge.strokeWidth / 2f
            canvas.drawRoundRect(inset, pageTop + inset, pageWidth - inset, pageBottom - inset, radius, radius, cardEdge)
            val dotR = 2.6f * density
            val dotX = 14f * density
            val barY = pageTop + rowHeight * 0.5f
            card.color = color(0xFFFFFF, 0.20f)
            for (i in 0..2) canvas.drawCircle(dotX + i * dotR * 3.6f, barY, dotR, card)
            code.color = color(TerminalPalette.Comment, 0.55f)
            canvas.drawText("lyrics.kt", dotX + dotR * 3.6f * 3f, barY + code.textSize * 0.35f, code)

            // The caret sits exactly where the typed text ends. While a key is going down it rides
            // along with the letter fading in, so it never trails behind the text. Position is
            // computed from the same row geometry as the text itself, with no spring of its own.
            var targetRow = codeCaret?.row ?: cursor.row
            var targetCol = codeCaret?.col ?: cursor.col
            if (targetRow !in viewport) { targetRow=cursor.row;targetCol=cursor.col }
            val caretSlot = viewport.indexOf(targetRow)
            if (caretSlot >= 0 || cursor.idle) {
                val row = doc.rows.getOrNull(targetRow)
                if(row!=null) targetCol=min(targetCol,revealed(row,targetRow))
                var x = textLeft + if (row == null) 0f else xAt(row, targetCol)
                val inFlight = if (row != null && codeCaret != null && typingOn && codeCaret.active &&
                    targetRow == codeCaret.row && codeCaret.next > targetCol) codeCaret else null
                if (row != null && inFlight != null) {
                    val x1 = textLeft + xAt(row, inFlight.next)
                    x += (x1 - x) * easeOut(inFlight.frac)
                }
                val y = focusY + (viewportPosition(targetRow.toFloat()) - focus) * rowHeight + rowOffset + baseline
                val typingNow = codeCaret != null && typingOn && codeCaret.active
                if (!caretReady || !motionEnabled || typingNow || caretRow != targetRow) caretX.snap(x)
                else caretX.step(dt, x, 400f, 40f)
                caretY.snap(y); caretReady = true
                caretRow = targetRow
                val alpha = if (solidCursor || typingNow) 1f else blinkAlpha(timeNs)
                if (alpha > 0.01f) {
                    val w = max(density * 1.6f, lyric.textSize * 0.075f)
                    val top = caretY.value + lyric.ascent()
                    val bottom = caretY.value + lyric.descent()
                    if (!low) {
                        fill.color = color(TerminalPalette.Cursor, 0.16f * alpha)
                        canvas.drawRect(caretX.value - w, top - w, caretX.value + w * 2f, bottom + w, fill)
                    }
                    fill.color = color(TerminalPalette.Cursor, alpha)
                    canvas.drawRect(caretX.value, top, caretX.value + w, bottom, fill)
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

    private fun drawRow(canvas: Canvas, row: TerminalRow, left: Float, y: Float, edge: Float, activeLyric: Boolean, active: Boolean, neighbor: Boolean, mix: Float, previous: Boolean, revealed: Int, ghostTo: Int, ghostAlpha: Float, glow: Boolean) {
        var k = 0
        while (k < row.spans.size) {
            val start = row.spans[k]
            val full = row.spans[k + 1]
            val end = min(full, revealed)
            // [revealed, ghostTo) is the letter being struck; it is drawn faded, just below its line.
            val ghostStart = max(start, revealed)
            val ghostEnd = if (ghostTo > revealed) min(full, ghostTo) else 0
            if (start >= end && ghostEnd <= ghostStart) break
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
            val halo = glow && activeLyric && words
            if (halo) paint.setShadowLayer(lyric.textSize * 0.6f, 0f, 0f, color(TerminalPalette.Cursor, 0.34f * mix))
            paint.color = color(row.spans[k + 2], alpha)
            if (start < end) canvas.drawText(row.text, start, end, left + xAt(row, start), y, paint)
            if (ghostEnd > ghostStart) {
                paint.color = color(row.spans[k + 2], alpha * ghostAlpha)
                canvas.drawText(row.text, ghostStart, ghostEnd, left + xAt(row, ghostStart),
                    y + (1f - ghostAlpha) * 2.5f * density, paint)
            }
            if (halo) paint.clearShadowLayer()
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

    private fun easeOut(t: Float): Float { val u = 1f - t.coerceIn(0f, 1f); return 1f - u * u }

    /** Soft blink: fully on, a short fade out, off, a short fade back in. */
    private fun blinkAlpha(timeNs: Long): Float {
        val phase = (timeNs % BlinkPeriodNs).toFloat() / BlinkPeriodNs
        val c = 0.5f + 0.5f * cos(phase * 6.2831855f)
        return ((c - 0.12f) / 0.45f).coerceIn(0f, 1f)
    }

    private companion object { const val BlinkPeriodNs = 1_060_000_000L }
}
