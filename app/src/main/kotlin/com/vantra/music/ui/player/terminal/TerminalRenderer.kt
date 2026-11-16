package com.vantra.music.ui.player.terminal

import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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

    // Cinematic shots and the beat-driven mode (see TerminalCinema).
    private val pan = Spring()
    private var panReady = false
    private var inv = 0f
    private var splitNow = 0f
    private var shotWall = 0f
    private val fxMatrix = Matrix()
    private val fxPoint = FloatArray(2)
    private var ctValid = false
    private var ctRow = 0
    private var ctX = 0f
    private var ctTyping = false
    private val orbGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orbCore = Paint(Paint.ANTI_ALIAS_FLAG)
    private val streak = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rays = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val invPage = Matrix()
    private val fadeSide = Paint()
    private val fadeSideShader = LinearGradient(0f, 0f, 1f, 0f, 0x00000000, 0xFF000000.toInt(), Shader.TileMode.CLAMP)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val slam = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private var tintFilter: LightingColorFilter? = null
    private var tintColor = -1
    private val glowShader = RadialGradient(0f, 0f, 1f,
        intArrayOf(0x38FF8A2A, 0x12FF6A1A, 0x00FF6A1A), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    private val coreShader = RadialGradient(0f, 0f, 1f,
        intArrayOf(0xFFFFF4D0.toInt(), 0xFFFFC04D.toInt(), 0xFFFF9A2E.toInt(), 0x00FF8A2A),
        floatArrayOf(0f, 0.42f, 0.72f, 1f), Shader.TileMode.CLAMP)
    private val streakShader = LinearGradient(-1f, 0f, 1f, 0f,
        intArrayOf(0x00FFC070, 0x88FFD9A0.toInt(), 0x00FFC070), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)

    init {
        orbGlow.shader = glowShader
        orbCore.shader = coreShader
        streak.shader = streakShader
        fadeSide.shader = fadeSideShader
    }
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
                0x1FFFFFFF.toInt(), 0x04FFFFFF.toInt(), Shader.TileMode.CLAMP)
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
        fx: TerminalFx? = null,
    ) {
        if (failed) return
        typingTimeline = typing; typingTimeMs = typingPositionMs; typingRate = typingSpeed; typingOn = typingEnabled
        val save = canvas.save()
        try {
            val fxClose = fx?.close ?: 0f
            val fxWall = fx?.wall ?: 0f
            val fxFlash = fx?.flash ?: 0f
            val fxLines = fx?.lines ?: 0f
            val fxSeed = fx?.lineSeed ?: 0
            val shotMix = max(fxClose, fxWall)
            inv = fxFlash
            splitNow = fx?.split ?: 0f
            shotWall = fxWall
            val gray = (fxFlash * 255f).toInt().coerceIn(0, 255)
            val bgColor = android.graphics.Color.rgb(gray, gray, gray)
            // True black: on an AMOLED panel those pixels are off, and nothing tints the background.
            // Only the beat flash of phonk mode lifts it, briefly.
            canvas.drawColor(bgColor)
            if (width <= 0 || height <= 0 || charW <= 0f) return
            val punch = fx?.punch ?: 0f
            val tilt = fx?.tilt ?: 0f
            if (punch > 0.005f || tilt != 0f) {
                canvas.scale(1f + 0.09f * punch, 1f + 0.09f * punch, width / 2f, height / 2f)
                canvas.rotate(Math.toDegrees(tilt.toDouble()).toFloat(), width / 2f, height / 2f)
            }
            // A hit also shakes the picture a little, a new offset every frame.
            if (punch > 0.02f) {
                val frame = (timeNs / 16_000_000L).toInt()
                val sx = (hash01(frame * 2 + fxSeed) - 0.5f) * 2f * 5f * density * punch
                val sy = (hash01(frame * 2 + 1 + fxSeed * 7) - 0.5f) * 2f * 5f * density * punch
                canvas.translate(sx, sy)
            }
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
            val planeFade = 1f - shotMix
            if (planeFade > 0.02f) {
                if (!low) drawDepthPlane(canvas, doc, scroll, 0.95f, -0.66f, 0.17f * planeFade)
                drawDepthPlane(canvas, doc, scroll, 0.45f, 0.66f, 0.23f * planeFade)
            }
            // Speed lines sit behind the words: once in the margins, once on the card.
            if (fxLines > 0.02f) drawSpeedLines(canvas, fxLines, fxSeed)
            val pageSave = canvas.save()
            canvas.concat(matrix)
            // A lyric can lie outside the old local page while scrolling catches up. The
            // projected safe band already fits it; do not clip it away in pre-projection space.
            val pageTop = min(0f, activeTop)
            val pageBottom = max(height.toFloat(), activeBottom)
            // In the close-up the text runs the full width of the screen, past the page margins.
            val ext = pageWidth * 0.10f * fxClose
            canvas.clipRect(-ext, pageTop, pageWidth + ext, pageBottom)
            // An opaque black card keeps rear code from competing with real words. It is rounded and
            // anti-aliased; the hairline edge and the fades are drawn over the rows further down.
            val radius = 14f * density
            card.color = bgColor
            canvas.drawRoundRect(0f, pageTop, pageWidth, pageBottom, radius, radius, card)
            guide.color = color(0xFFFFFF, 0.07f * (1f - fxClose))
            canvas.drawLine(gutter, 0f, gutter, height.toFloat(), guide)
            if (fxLines > 0.02f && matrix.invert(invPage)) {
                canvas.save()
                canvas.concat(invPage)
                drawSpeedLines(canvas, fxLines, fxSeed)
                canvas.restore()
            }
            // Close-up: zoom about the active row and follow the caret along the line, like a
            // camera operator would. Everything below is blended from the plain view by fxClose.
            resolveCaret(doc, cursor, codeCaret, textLeft, fxClose > 0.3f)
            val cols = if (landscape) 26f else 15f
            val closeZoom = ((pageWidth * 0.92f) / (lyricCharW * cols)).coerceIn(1.25f, 3.2f)
            val zoom = 1f + (closeZoom - 1f) * fxClose
            val anchorY = focusY + rowHeight * 0.5f
            val zoomSave = canvas.save()
            if (fxWall > 0.002f) {
                // The wall shot is a little closer than the plain page so its rows can be read.
                val wallZoom = 1f + 0.3f * fxWall
                canvas.translate(pageWidth / 2f, anchorY)
                canvas.scale(wallZoom, wallZoom)
                canvas.translate(-pageWidth / 2f, -anchorY)
            }
            if (fxClose > 0.002f) {
                val minLeft = textLeft - 6f * density
                val wanted = max(minLeft, (if (ctValid) ctX else minLeft) - pageWidth / zoom * 0.5f)
                if (!panReady || !motionEnabled || resetMotion) { pan.snap(wanted); panReady = true }
                else pan.step(dt, wanted, 170f, 26f)
                canvas.translate(pageWidth * 0.04f * fxClose, anchorY)
                canvas.scale(zoom, zoom)
                canvas.translate(-pan.value * fxClose, -anchorY)
            } else panReady = false
            for (slot in viewport.indices) {
                val r = viewport[slot]
                val row = doc.rows[r]
                val top = focusY + (slot - focus) * rowHeight + rowOffset
                val active = row.line == cursor.line && row.line >= 0
                val activeLyric = active && row.kind == KindLyric && !cursor.idle
                val neighbor = row.kind == KindLyric && abs(row.line - cursor.line) <= 1
                val distance = abs(top + rowHeight / 2f - height / 2f) / max(1f, height / 2f)
                val rowsAway = abs(top - activeTop) / max(1f, rowHeight)
                val edge = (1f - 0.35f * distance).coerceIn(0.55f, 1f) * (1f - 0.6f * fxClose * min(1f, rowsAway / 4f))
                val previous = row.line == previousLine && previousLine >= 0
                if (activeLyric || (previous && row.kind == KindLyric)) {
                    val emphasis = if (activeLyric) mix else 1f - mix
                    band.alpha = ((0.8f + 0.2f * (if (low) 0f else glowLevel.coerceIn(0f, 1f))) * emphasis * (1f - fxClose) * 255f)
                        .toInt().coerceIn(0, 255)
                    canvas.drawRect(gutter + density, top, pageWidth * 3f, top + rowHeight, band)
                    fill.color = color(TerminalPalette.Cursor, emphasis * (1f - fxClose))
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
                    strike?.next ?: 0, strike?.let { easeOut(it.frac) } ?: 1f, !low, 1f + 0.9f * fxWall)
                // A faint mirror image under the line being typed, like glass under the text.
                if (activeLyric && fxClose > 0.3f && !low && revealed > 0) {
                    val axis = top + rowHeight
                    canvas.save()
                    canvas.scale(1f, -1f, 0f, axis)
                    drawRow(canvas, row, textLeft, top + baseline, edge, true, active, neighbor, mix, previous, revealed,
                        0, 1f, false, 0.11f * fxClose)
                    canvas.restore()
                }
            }
            // The caret sits exactly where the typed text ends: a red block, like a terminal's.
            // While a key is going down it rides along with the letter fading in, so it never
            // trails behind the text. Its position is computed from the same row geometry as the
            // text itself, with no spring of its own.
            if (ctValid) {
                val y = focusY + (viewportPosition(ctRow.toFloat()) - focus) * rowHeight + rowOffset + baseline
                if (!caretReady || !motionEnabled || ctTyping || caretRow != ctRow) caretX.snap(ctX)
                else caretX.step(dt, ctX, 400f, 40f)
                caretY.snap(y); caretReady = true
                caretRow = ctRow
                val alpha = if (solidCursor || ctTyping) 1f else blinkAlpha(timeNs)
                if (alpha > 0.01f) {
                    val bw = max(density * 3f, lyricCharW * 0.55f)
                    val top = caretY.value + lyric.ascent()
                    val bottom = caretY.value + lyric.descent()
                    if (!low) {
                        card.color = color(TerminalPalette.Cursor, 0.12f * alpha)
                        canvas.drawRect(caretX.value - bw * 0.5f, top - bw * 0.35f, caretX.value + bw * 1.5f, bottom + bw * 0.35f, card)
                    }
                    card.color = color(TerminalPalette.Cursor, alpha)
                    if (!low) card.setShadowLayer(lyric.textSize * 0.5f, 0f, 0f, color(TerminalPalette.Cursor, 0.7f * alpha))
                    canvas.drawRect(caretX.value + density, top, caretX.value + density + bw, bottom, card)
                    if (!low) card.clearShadowLayer()
                }
            }
            canvas.restoreToCount(zoomSave)

            // Rows dissolve into the background at the edges of the card instead of being cut off:
            // top and bottom, and left and right where long lines run off. During a flash the
            // fades take the colour of the flash.
            val tint = tintFor(bgColor)
            fadeTop.colorFilter = tint
            fadeBottom.colorFilter = tint
            fadeSide.colorFilter = tint
            canvas.drawRect(0f, pageTop, pageWidth, fadeHeight, fadeTop)
            canvas.drawRect(0f, height - fadeHeight, pageWidth, pageBottom, fadeBottom)
            val sideW = pageWidth * 0.08f
            val rightEdge = pageWidth + ext
            fxMatrix.setScale(sideW, 1f)
            fxMatrix.postTranslate(rightEdge - sideW, 0f)
            fadeSideShader.setLocalMatrix(fxMatrix)
            canvas.drawRect(rightEdge - sideW, pageTop, rightEdge, pageBottom, fadeSide)
            if (fxClose > 0.3f) {
                fxMatrix.setScale(-sideW, 1f)
                fxMatrix.postTranslate(-ext + sideW, 0f)
                fadeSideShader.setLocalMatrix(fxMatrix)
                canvas.drawRect(-ext, pageTop, -ext + sideW, pageBottom, fadeSide)
            }
            // Hairline edge, then a quiet window title: three dots and a file name. Both give way
            // to the full-bleed cinematic shots.
            val chrome = (1f - shotMix) * (1f - inv)
            if (chrome > 0.02f) {
                cardEdge.alpha = (chrome * 255f).toInt().coerceIn(0, 255)
                val inset = cardEdge.strokeWidth / 2f
                canvas.drawRoundRect(inset, pageTop + inset, pageWidth - inset, pageBottom - inset, radius, radius, cardEdge)
                val dotR = 2.6f * density
                val dotX = 14f * density
                val barY = pageTop + rowHeight * 0.5f
                card.color = color(0xFFFFFF, 0.20f * chrome)
                for (i in 0..2) canvas.drawCircle(dotX + i * dotR * 3.6f, barY, dotR, card)
                code.color = color(TerminalPalette.Comment, 0.55f * chrome)
                canvas.drawText("lyrics.kt", dotX + dotR * 3.6f * 3f, barY + code.textSize * 0.35f, code)
            }
            canvas.restoreToCount(pageSave)

            // Screen-space light and motion on top of the picture: the warm orb of the wall shot
            // and the shockwave ring of a phonk hit.
            val fxOrb = fx?.orb ?: 0f
            if (fxOrb > 0.02f) {
                fxPoint[0] = pageWidth * 0.42f
                fxPoint[1] = activeTop - rowHeight * 2.2f
                matrix.mapPoints(fxPoint)
                drawOrb(canvas, fxOrb, fxPoint[0], fxPoint[1])
            }
            // On every flash the word being typed is slammed across the screen, like the lyric
            // videos of phonk edits: it is only there for the length of the cut.
            if (fxFlash > 0.01f && (fx?.phonk ?: 0f) > 0.5f) drawSlam(canvas, doc, punch, fxSeed)
            val fxRing = fx?.ring ?: -1f
            if (fxRing >= 0f) drawRing(canvas, fxRing, fx?.ringPower ?: 0f)

        } catch (error: RuntimeException) {
            android.util.Log.e("VantraTerminal", "Editor rendering failed", error)
            failed = true
        } finally {
            canvas.restoreToCount(save)
        }
    }

    /** Interpolate the existing row-index scroll spring across folded line-number gaps. */
    private fun viewportPosition(row: Float): Float = viewportMotion.position(viewport, row)

    private fun drawRow(canvas: Canvas, row: TerminalRow, left: Float, y: Float, edge: Float, activeLyric: Boolean, active: Boolean, neighbor: Boolean, mix: Float, previous: Boolean, revealed: Int, ghostTo: Int, ghostAlpha: Float, glow: Boolean, fade: Float = 1f) {
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
            val alpha = fade * when {
                activeLyric && words -> 0.72f + 0.28f * mix
                previous && words -> (0.58f + 0.42f * (1f - mix)) * edge
                words && neighbor -> 0.58f * edge
                words -> 0.29f * edge
                active -> (0.42f + 0.28f * mix) * edge
                previous -> (0.42f + 0.28f * (1f - mix)) * edge
                row.kind == KindHeader -> 0.65f * edge
                else -> 0.42f * edge
            }
            // A phonk hit splits the colours of the words for a moment.
            if (splitNow > 0.03f && activeLyric && words && fade >= 1f && start < end) {
                val off = 2.4f * density * splitNow
                paint.color = color(0xFF2D55, 0.55f * splitNow * alpha)
                canvas.drawText(row.text, start, end, left + xAt(row, start) - off, y, paint)
                paint.color = color(0x19D3FF, 0.55f * splitNow * alpha)
                canvas.drawText(row.text, start, end, left + xAt(row, start) + off, y, paint)
            }
            val halo = glow && activeLyric && words
            if (halo) paint.setShadowLayer(lyric.textSize * 0.6f, 0f, 0f, color(0xFFC890, 0.22f * mix))
            paint.color = ink(row.spans[k + 2], alpha)
            if (start < end) canvas.drawText(row.text, start, end, left + xAt(row, start), y, paint)
            if (ghostEnd > ghostStart) {
                paint.color = ink(row.spans[k + 2], alpha * ghostAlpha)
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
        focalPx = width * (if (landscape) 1.1f else 1.65f) * (1f - 0.45f * shotWall)
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

    /** Text colour for the beat flash: while the background lifts to white the ink goes to black. */
    private fun ink(rgb: Int, alpha: Float): Int {
        if (inv <= 0.01f) return color(rgb, alpha)
        // dim (soft) flashes keep their light ink; bright ones, white or light grey, go to black ink
        val k = 1f - ((inv - 0.40f) / 0.15f).coerceIn(0f, 1f)
        val r = (((rgb shr 16) and 0xFF) * k).toInt()
        val g = (((rgb shr 8) and 0xFF) * k).toInt()
        val b = ((rgb and 0xFF) * k).toInt()
        return color((r shl 16) or (g shl 8) or b, alpha)
    }

    private fun hash01(n: Int): Float {
        var x = n * -1640531535
        x = x xor (x ushr 15)
        x *= 0x2c1b3c6d
        x = x xor (x ushr 12)
        return (x and 0xFFFF) / 65535f
    }

    /** Radial speed lines from the middle of the screen, a fresh pattern for every hit. */
    private fun drawSpeedLines(canvas: Canvas, amount: Float, seed: Int) {
        val cx = width / 2f
        val cy = height / 2f
        val rMax = hypot(cx, cy)
        rays.strokeWidth = max(1f, density * 1.4f)
        val tone = if (inv > 0.5f) 0 else 255
        val step = 2f * Math.PI.toFloat() / RayCount
        for (i in 0 until RayCount) {
            val h1 = hash01(seed * 97 + i * 31)
            val h2 = hash01(seed * 53 + i * 17 + 7)
            val a = (i + h1 * 0.8f) * step
            val r0 = rMax * (0.34f + 0.22f * h2)
            val r1 = rMax * (0.80f + 0.20f * h1)
            val c = cos(a)
            val s = sin(a)
            rays.color = android.graphics.Color.argb((amount * (50f + 130f * h2)).toInt().coerceIn(0, 255), tone, tone, tone)
            canvas.drawLine(cx + c * r0, cy + s * r0, cx + c * r1, cy + s * r1, rays)
        }
    }

    /** The warm orb of the wall shot: a wide glow, the bright core and a thin horizontal streak. */
    private fun drawOrb(canvas: Canvas, orb: Float, px: Float, py: Float) {
        val m = min(width, height).toFloat()
        // A soft warm bloom and a small core, kept low so it reads as a distant light, not a sun.
        val glowR = m * 0.62f
        fxMatrix.setScale(glowR, glowR)
        fxMatrix.postTranslate(px, py)
        glowShader.setLocalMatrix(fxMatrix)
        orbGlow.alpha = (orb * 0.8f * 255f).toInt().coerceIn(0, 255)
        canvas.drawCircle(px, py, glowR, orbGlow)
        val r = m * 0.030f * (0.85f + 0.15f * orb)
        fxMatrix.setScale(r, r)
        fxMatrix.postTranslate(px, py)
        coreShader.setLocalMatrix(fxMatrix)
        orbCore.alpha = (orb * 0.9f * 255f).toInt().coerceIn(0, 255)
        canvas.drawCircle(px, py, r, orbCore)
        val half = m * 0.30f * orb
        fxMatrix.setScale(half, 1f)
        fxMatrix.postTranslate(px, py)
        streakShader.setLocalMatrix(fxMatrix)
        streak.alpha = (orb * 110f).toInt().coerceIn(0, 255)
        canvas.drawRect(px - half, py - 1.0f * density, px + half, py + 1.0f * density, streak)
    }

    /** The ring that leaves the centre with every hit: fast at first, then settling, thinning as it goes. */
    private fun drawRing(canvas: Canvas, progress: Float, power: Float) {
        val m = max(width, height).toFloat()
        val p = progress.coerceIn(0f, 1f)
        val e = 1f - (1f - p) * (1f - p)
        val tone = if (inv > 0.5f) 0 else 255
        ringPaint.strokeWidth = density * (2f + 10f * power * (1f - p))
        ringPaint.color = android.graphics.Color.argb(
            ((1f - p) * (0.30f + 0.50f * power) * 255f).toInt().coerceIn(0, 255), tone, tone, tone)
        canvas.drawCircle(width / 2f, height / 2f, m * (0.08f + 0.62f * e), ringPaint)
    }

    /** The last word typed on the active line, as big as the screen allows, tilted a little. */
    private fun drawSlam(canvas: Canvas, doc: TerminalDocument, punch: Float, seed: Int) {
        if (!ctValid) return
        val row = doc.rows.getOrNull(ctRow) ?: return
        val from = row.lyricFrom
        var end = min(revealed(row, ctRow), row.lyricTo)
        while (end > from && !row.text[end - 1].isLetterOrDigit()) end--
        var start = end
        while (start > from && (row.text[start - 1].isLetterOrDigit() || row.text[start - 1] == '\'')) start--
        if (end - start < 1) return
        // a very short word borrows the one before it
        if (end - start < 4 && start > from) {
            var p = start - 1
            while (p > from && !row.text[p - 1].isLetterOrDigit()) p--
            while (p > from && row.text[p - 1].isLetterOrDigit()) p--
            start = p
        }
        val word = row.text.substring(start, end)
        slam.textSize = min(width, height) * 0.30f * (1f + 0.10f * punch.coerceIn(0f, 1f))
        val wide = slam.measureText(word)
        val room = width * 0.88f
        if (wide > room) slam.textSize = slam.textSize * room / wide
        val tone = if (inv > 0.5f) 0 else 255
        slam.color = android.graphics.Color.argb(255, tone, tone, tone)
        canvas.save()
        canvas.rotate(if (seed % 2 == 0) -4f else 3f, width / 2f, height / 2f)
        canvas.drawText(word, width / 2f, height / 2f + slam.textSize * 0.33f, slam)
        canvas.restore()
    }

    /** Colour filter that turns the black fades into the colour of the flash. */
    private fun tintFor(bg: Int): LightingColorFilter? {
        if (bg == android.graphics.Color.BLACK) return null
        if (tintColor != bg || tintFilter == null) {
            tintFilter = LightingColorFilter(0x000000, bg and 0xFFFFFF)
            tintColor = bg
        }
        return tintFilter
    }

    /** Where the caret goes this frame (page space), shared by the follow-cam and the caret itself. */
    private fun resolveCaret(doc: TerminalDocument, cursor: TerminalCursor, codeCaret: TerminalCodeCaret?, textLeft: Float, clampToLyric: Boolean) {
        ctValid = false
        var targetRow = codeCaret?.row ?: cursor.row
        var targetCol = codeCaret?.col ?: cursor.col
        // In the close-up the caret stays on the words, like the camera, instead of running off
        // to the closing code underneath them once the line is typed.
        if (clampToLyric && !cursor.idle) { targetRow = cursor.row; targetCol = cursor.col }
        if (targetRow !in viewport) { targetRow = cursor.row; targetCol = cursor.col }
        if (viewport.indexOf(targetRow) < 0 && !cursor.idle) return
        val row = doc.rows.getOrNull(targetRow)
        if (row != null) targetCol = min(targetCol, revealed(row, targetRow))
        var x = textLeft + if (row == null) 0f else xAt(row, targetCol)
        val inFlight = if (row != null && codeCaret != null && typingOn && codeCaret.active &&
            targetRow == codeCaret.row && codeCaret.next > targetCol) codeCaret else null
        if (row != null && inFlight != null) {
            val x1 = textLeft + xAt(row, inFlight.next)
            x += (x1 - x) * easeOut(inFlight.frac)
        }
        ctRow = targetRow
        ctX = x
        ctTyping = codeCaret != null && typingOn && codeCaret.active && (!clampToLyric || targetRow == codeCaret.row)
        ctValid = true
    }

    /** Soft blink: fully on, a short fade out, off, a short fade back in. */
    private fun blinkAlpha(timeNs: Long): Float {
        val phase = (timeNs % BlinkPeriodNs).toFloat() / BlinkPeriodNs
        val c = 0.5f + 0.5f * cos(phase * 6.2831855f)
        return ((c - 0.12f) / 0.45f).coerceIn(0f, 1f)
    }

    private companion object {
        const val BlinkPeriodNs = 1_060_000_000L
        const val RayCount = 28
    }
}
