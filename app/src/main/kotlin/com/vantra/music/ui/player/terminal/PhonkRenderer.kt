package com.vantra.music.ui.player.terminal

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Everything one Phonk frame needs: the frame loop writes it, [PhonkRenderer] reads it. */
internal class PhonkState {
    val sync = TerminalSynchronization()
    val clock = TerminalPlayClock()
    val timeline = PhonkTimeline()
    val beat = PhonkBeat()
    val fx = PhonkFx()
    val stage = Spring()
    var chunks: List<PhonkChunk> = emptyList()
    var spin = 0f
    var timeS = 0f
    val wave = FloatArray(WaveSamples)
    var waveHead = 0
    var waveAcc = 0f
    var wavePeak = 0f
    var artist = ""
    var title = ""
    var discontinuity = 0L
    var lastChunks: List<PhonkChunk>? = null
    var settled = 0

    companion object {
        const val WaveSamples = 56
    }
}

/**
 * The Phonk player's picture, modelled on a phonk / montagem lyric video: black stage, the lyric a
 * phrase at a time on the left (big, tilted, motion-blurred as it lands), a CD case with the cover
 * in high-contrast black and white on the right with a label above and a waveform under it. On
 * every beat the whole frame cuts to white, a grey step, and back to black, with the ink and the
 * cover inverted; the picture punches in, shakes and a shockwave ring and its echo leave the disc.
 * While no lyric is sung an eclipse ring waits in the dark.
 */
internal class PhonkRenderer {
    var failed = false
        private set

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = -0.02f
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowShader = RadialGradient(0f, 0f, 1f,
        intArrayOf(0x55FFFFFF, 0x1AFFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    private val matrix = Matrix()
    private val planeM = Matrix()
    private val facePath = android.graphics.Path()
    private val cornerF = FloatArray(8)
    private val cornerB = FloatArray(8)
    private val peaks = FloatArray(PhonkState.WaveSamples)
    private val planeSrc = FloatArray(8)
    private val planeDst = FloatArray(8)
    private val edgeA = FloatArray(2)
    private val edgeB = FloatArray(2)
    private val glare = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glareShader = android.graphics.LinearGradient(-1f, 0f, 1f, 0f,
        intArrayOf(0x00FFFFFF, 0x18FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    private var dash: DashPathEffect? = null
    private var dashDensity = 0f

    private var art: Bitmap? = null
    private var artShader: BitmapShader? = null
    private val grayMatrix = ColorMatrix().apply {
        setSaturation(0f)
        postConcat(ColorMatrix(floatArrayOf(2.3f, 0f, 0f, 0f, -166f, 0f, 2.3f, 0f, 0f, -166f, 0f, 0f, 2.3f, 0f, -166f, 0f, 0f, 0f, 1f, 0f)))
    }
    private val artFilter = ColorMatrixColorFilter(grayMatrix)
    private val artInverted = ColorMatrixColorFilter(ColorMatrix(grayMatrix).apply {
        postConcat(ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)))
    })

    init { glow.shader = glowShader; glare.shader = glareShader }

    // the phrase layout is measured once per phrase and size, not every frame
    private var layoutKey = Int.MIN_VALUE
    private var lines: List<List<String>> = emptyList()
    private var baseSize = 0f
    private var blockW = 0f
    private var lineH = 0f

    /** the cover, cut to a square; null falls back to a drawn spiral disc */
    fun setArt(source: Bitmap?) {
        art = null
        artShader = null
        if (source == null || source.width <= 0 || source.height <= 0) return
        val side = min(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val scaled = if (side > 512) Bitmap.createScaledBitmap(square, 512, 512, true) else square
        art = scaled
        artShader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }

    fun draw(canvas: Canvas, w: Int, h: Int, density: Float, s: PhonkState, preview: Boolean, timeNs: Long) {
        if (failed || w <= 0 || h <= 0) return
        val save = canvas.save()
        try {
            scene(canvas, w.toFloat(), h.toFloat(), density, s, preview, timeNs)
        } catch (error: RuntimeException) {
            failed = true
        } finally {
            canvas.restoreToCount(save)
        }
    }

    private fun scene(canvas: Canvas, w: Float, h: Float, d: Float, s: PhonkState, preview: Boolean, timeNs: Long) {
        val fx = s.fx
        val flash = if (preview) 0f else fx.flash
        val gray = (flash * 255f).toInt().coerceIn(0, 255)
        val bg = Color.rgb(gray, gray, gray)
        canvas.drawColor(bg)
        val light = flash >= 0.5f
        val ink = if (light) Color.BLACK else Color.WHITE
        val frame = (timeNs / 16_000_000L).toInt()
        val punch = if (preview) 0f else fx.punch
        val cx0 = w / 2f
        val cy0 = h / 2f
        canvas.save()
        // every hit: shake, punch in, tilt. Between hits the bass keeps the picture trembling a little.
        if (punch > 0.01f || fx.rumble > 0.02f) {
            val amp = d * (8f * punch + 1.8f * fx.rumble)
            canvas.translate((hash01(frame * 2 + fx.seed) - 0.5f) * 2f * amp, (hash01(frame * 2 + 1 + fx.seed * 7) - 0.5f) * 2f * amp)
            val zoom = 1f + 0.14f * punch
            canvas.scale(zoom, zoom, cx0, cy0)
            canvas.rotate(Math.toDegrees(fx.tilt.toDouble()).toFloat(), cx0, cy0)
        }
        if (fx.lines > 0.02f) rays(canvas, w, h, d, fx.lines, fx.seed, light)

        val portrait = w < h * 0.85f
        val cs: Float
        val cx: Float
        val cy: Float
        val left: Float
        val right: Float
        val top: Float
        val bottom: Float
        if (portrait) {
            cs = w * 0.66f; cx = w / 2f; cy = h * 0.70f
            left = w * 0.07f; right = w * 0.93f; top = h * 0.08f; bottom = cy - cs / 2f - h * 0.08f
        } else {
            cs = min(w * 0.36f, h * 0.70f); cx = w - w * 0.035f - cs / 2f; cy = h * 0.46f
            left = w * 0.04f; right = cx - cs / 2f - w * 0.02f; top = h * 0.10f; bottom = h * 0.90f
        }
        val stage = if (preview) 1f else s.stage.value.coerceIn(0f, 1f)

        // the phrase being sung
        val chunk = if (preview) null else s.chunks.getOrNull(s.timeline.index)
        val words = if (preview) previewWords(s) else chunk?.words
        if (words != null && words.isNotEmpty()) {
            drawWords(canvas, words, chunk?.id ?: 0, if (preview) 1f else s.timeline.age, left, top, right, bottom, d, ink, fx, bg, s.timeS, !preview)
        }

        // the CD case, the label and the waveform
        if (stage > 0.01f) {
            val layer = if (stage < 0.99f) canvas.saveLayerAlpha(0f, 0f, w, h, (stage * 255f).toInt().coerceIn(0, 255)) else -1
            drawCase(canvas, cx, cy, cs * (0.9f + 0.1f * stage), d, ink, light, bg, s, preview)
            if (layer >= 0) canvas.restoreToCount(layer)
        }

        // the eclipse that waits while nobody sings
        if (stage < 0.99f) {
            val ex = if (portrait) w * 0.5f else w * 0.28f
            val ey = if (portrait) h * 0.40f else h * 0.44f
            eclipse(canvas, ex, ey, min(w, h), ink, bg, 1f - stage, punch.coerceIn(0f, 1f))
        }

        // the shockwave: a ring and its echo leave the disc (or the eclipse) on every kick
        if (fx.ring >= 0f || fx.ring2 >= 0f) {
            val rx = if (stage > 0.5f) cx else if (portrait) w * 0.5f else w * 0.28f
            val ry = if (stage > 0.5f) cy else if (portrait) h * 0.40f else h * 0.44f
            if (fx.ring >= 0f) shockwave(canvas, rx, ry, w, h, d, fx.ring, fx.ringPower, ink)
            if (fx.ring2 >= 0f) shockwave(canvas, rx, ry, w, h, d, fx.ring2, fx.ringPower * 0.55f, ink)
        }
        canvas.restore()
    }

    // ---- the words ------------------------------------------------------------------------

    private fun previewWords(s: PhonkState): List<String> {
        val parts = s.title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return if (parts.isEmpty()) listOf("Phonk") else parts.take(2)
    }

    private fun wordScale(word: String, index: Int): Float =
        if (index > 0 && word.lowercase() in SmallWords) 0.72f else 1f

    private fun lineWidthAt(line: List<String>, size: Float): Float {
        var total = 0f
        for (j in line.indices) {
            text.textSize = size * wordScale(line[j], j)
            total += text.measureText(line[j])
            if (j > 0) total += size * 0.24f
        }
        return total
    }

    private fun splitLines(words: List<String>): List<List<String>> {
        if (words.size == 1) return listOf(words)
        val joined = words.sumOf { it.length } + words.size - 1
        if (words.size == 2 && joined <= 9) return listOf(words)
        if (words.size == 2) return listOf(listOf(words[0]), listOf(words[1]))
        val a = words[0].length + 1 + words[1].length
        val b = words[1].length + 1 + words[2].length
        return when {
            a <= 10 && a <= b -> listOf(listOf(words[0], words[1]), listOf(words[2]))
            b <= 10 -> listOf(listOf(words[0]), listOf(words[1], words[2]))
            else -> words.map { listOf(it) }
        }
    }

    private fun ensureLayout(words: List<String>, id: Int, regionW: Float, regionH: Float) {
        val key = ((id * 31 + words.hashCode()) * 31 + regionW.toInt()) * 31 + regionH.toInt()
        if (key == layoutKey && lines.isNotEmpty()) return
        layoutKey = key
        lines = splitLines(words)
        var widest = 0f
        for (line in lines) widest = max(widest, lineWidthAt(line, 100f))
        val byWidth = if (widest > 0f) 100f * regionW / widest else 100f
        val byHeight = regionH * 0.86f / lines.size
        baseSize = min(min(byWidth, byHeight), regionH * 0.42f)
        lineH = baseSize
        blockW = 0f
        for (line in lines) blockW = max(blockW, lineWidthAt(line, baseSize))
    }

    private fun drawWords(canvas: Canvas, words: List<String>, id: Int, age: Float,
                          left: Float, top: Float, right: Float, bottom: Float, d: Float, ink: Int, fx: PhonkFx, bg: Int,
                          timeS: Float, deep: Boolean) {
        val regionW = right - left
        val regionH = bottom - top
        if (regionW <= 0f || regionH <= 0f) return
        ensureLayout(words, id, regionW, regionH)
        val t = (age / 0.13f).coerceIn(0f, 1f)
        val landing = 1f - (1f - t) * (1f - t) * (1f - t)
        val blur = max(1f - t, fx.blur * 0.8f)
        val tilt = ((id * 37) % 9 - 4) * 1.1f
        val cx = left + regionW / 2f
        val cy = top + regionH / 2f
        // the block is a plane in space: turned a little, swaying, kicked on every hit, and on
        // landing it flies in from right in front of the camera
        val sign = if (id % 2 == 0) 1f else -1f
        val kickSign = if (fx.tilt < 0f) -1f else 1f
        val yaw = if (deep) sign * (0.09f + 0.03f * sin(timeS * 1.1f)) + kickSign * fx.punch * 0.05f else sign * 0.08f
        val pitch = if (deep) 0.025f * sin(timeS * 0.9f + 1f) - fx.punch * 0.02f else 0.02f
        val focal = max(regionW, regionH) * 2f
        val fly = -(1f - landing) * focal * 0.22f
        val blockLeft = cx - blockW / 2f
        val firstBaseline = cy - lines.size * lineH / 2f + lineH * 0.80f
        canvas.save()
        canvas.rotate(tilt, cx, cy)
        // the depth of the letters: copies stacked behind the face, fainter the further back
        if (deep && fx.punch > 0.25f) {
            val step = baseSize * 0.04f * fx.punch.coerceAtMost(1f)
            for (k in 3 downTo 1) {
                canvas.save()
                canvas.concat(plane(left, top, right, bottom, yaw, pitch, fly + k * step, focal))
                lineSet(canvas, blockLeft, firstBaseline, withAlpha(ink, (90f * (1f - k / 4f)).toInt()), 0f)
                canvas.restore()
            }
        }
        canvas.save()
        canvas.concat(plane(left, top, right, bottom, yaw, pitch, fly, focal))
        // the motion blur: faint copies trailing the words while they land and on every hit
        if (blur > 0.06f) {
            val step = baseSize * 0.045f * blur
            val dir = if (id % 2 == 0) 1f else -1f
            for (k in 5 downTo 1) {
                lineSet(canvas, blockLeft, firstBaseline, withAlpha(ink, (70f * (1f - k / 6f)).toInt()), dir * k * step)
            }
        }
        // a hit splits the colours a little
        if (fx.punch > 0.3f) {
            val off = baseSize * 0.022f * fx.punch
            lineSet(canvas, blockLeft, firstBaseline, withAlpha(0xFF2D55, 170), -off)
            lineSet(canvas, blockLeft, firstBaseline, withAlpha(0x19D3FF, 170), off)
        }
        lineSet(canvas, blockLeft, firstBaseline, ink, 0f)
        canvas.restore()
        canvas.restore()
    }

    private fun lineSet(canvas: Canvas, blockLeft: Float, firstBaseline: Float, color: Int, dx: Float) {
        text.color = color
        var baseline = firstBaseline
        for (line in lines) {
            var x = blockLeft + dx
            for (j in line.indices) {
                val size = baseSize * wordScale(line[j], j)
                text.textSize = size
                canvas.drawText(line[j], x, baseline, text)
                x += text.measureText(line[j]) + baseSize * 0.24f
            }
            baseline += lineH
        }
    }

    // ---- the CD case ----------------------------------------------------------------------

    private fun drawCase(canvas: Canvas, cx: Float, cy: Float, cs: Float, d: Float, ink: Int, light: Boolean, bg: Int,
                         s: PhonkState, preview: Boolean) {
        val fx = s.fx
        val half = cs / 2f
        val t = if (preview) 0f else s.timeS
        val punch = if (preview) 0f else fx.punch
        val kickSign = if (fx.tilt < 0f) -1f else 1f
        // a real box in space: it turns slowly towards and away from the viewer, and every hit knocks it
        val yaw = -0.46f + 0.16f * sin(t * 0.8f) + kickSign * punch * 0.09f
        val pitch = 0.11f + 0.07f * cos(t * 0.55f) - punch * 0.04f
        val focal = cs * 2.4f
        val depth = cs * 0.075f
        canvas.save()
        canvas.translate(cx, cy)
        val pop = 1f + 0.05f * punch
        canvas.scale(pop, pop)
        canvas.rotate(-1.5f + 1.0f * sin(t * 0.9f))
        val r = d * 3f
        val inset = cs * 0.035f

        // the back plate, dim, and the four edges that give the case its thickness
        canvas.save()
        canvas.concat(plane(-half, -half, half, half, yaw, pitch, depth, focal))
        stroke.pathEffect = null
        stroke.color = withAlpha(ink, 110)
        stroke.strokeWidth = max(1f, d * 0.8f)
        canvas.drawRoundRect(-half, -half, half, half, r, r, stroke)
        canvas.restore()
        // the thickness of the case: four clear plastic sides, lit a little differently, with bright edges
        for (c in 0..3) {
            val x = if (c == 1 || c == 2) half else -half
            val y = if (c >= 2) half else -half
            project(x, y, -depth, yaw, pitch, focal, edgeA)
            cornerF[c * 2] = edgeA[0]; cornerF[c * 2 + 1] = edgeA[1]
            project(x, y, depth, yaw, pitch, focal, edgeB)
            cornerB[c * 2] = edgeB[0]; cornerB[c * 2 + 1] = edgeB[1]
        }
        for (c in 0..3) {
            val n = (c + 1) % 4
            facePath.reset()
            facePath.moveTo(cornerF[c * 2], cornerF[c * 2 + 1])
            facePath.lineTo(cornerF[n * 2], cornerF[n * 2 + 1])
            facePath.lineTo(cornerB[n * 2], cornerB[n * 2 + 1])
            facePath.lineTo(cornerB[c * 2], cornerB[c * 2 + 1])
            facePath.close()
            // 0 top, 1 right, 2 bottom, 3 the hinge side on the left, a little denser
            fill.color = withAlpha(ink, if (c == 3) 78 else if (c == 0) 52 else 34)
            canvas.drawPath(facePath, fill)
            stroke.color = withAlpha(ink, 170)
            stroke.strokeWidth = max(1f, d * 0.8f)
            canvas.drawLine(cornerF[c * 2], cornerF[c * 2 + 1], cornerB[c * 2], cornerB[c * 2 + 1], stroke)
        }

        // the disc in the middle of the case, spinning, the cover in harsh black and white
        val discR = cs * 0.41f
        canvas.save()
        canvas.concat(plane(-half, -half, half, half, yaw, pitch, 0f, focal))
        canvas.save()
        canvas.translate(cs * 0.025f, 0f)
        canvas.rotate(s.spin)
        val shader = artShader
        val bitmap = art
        if (shader != null && bitmap != null) {
            val k = discR * 2f / bitmap.width
            matrix.setScale(k, k)
            matrix.postTranslate(-discR, -discR)
            shader.setLocalMatrix(matrix)
            disc.shader = shader
            disc.colorFilter = if (light) artInverted else artFilter
            canvas.drawCircle(0f, 0f, discR, disc)
            disc.shader = null
            disc.colorFilter = null
        } else {
            spiral(canvas, discR, d, ink)
        }
        canvas.restore()
        canvas.save()
        canvas.translate(cs * 0.025f, 0f)
        stroke.color = withAlpha(ink, 230)
        stroke.strokeWidth = max(1f, d * 1.1f)
        canvas.drawCircle(0f, 0f, discR, stroke)
        fill.color = bg
        canvas.drawCircle(0f, 0f, discR * 0.17f, fill)
        canvas.drawCircle(0f, 0f, discR * 0.17f, stroke)
        stroke.color = withAlpha(ink, 90)
        canvas.drawCircle(0f, 0f, discR * 0.27f, stroke)
        canvas.restore()
        canvas.restore()

        // the front plate: outer frame, inner frame, the hinge and its tabs, and a glare that
        // slides across the plastic as the case turns
        canvas.save()
        canvas.concat(plane(-half, -half, half, half, yaw, pitch, -depth, focal))
        stroke.color = withAlpha(ink, 235)
        stroke.strokeWidth = max(1f, d * 1.1f)
        canvas.drawRoundRect(-half, -half, half, half, r, r, stroke)
        stroke.color = withAlpha(ink, 150)
        stroke.strokeWidth = max(1f, d * 0.7f)
        canvas.drawRoundRect(-half + inset, -half + inset, half - inset, half - inset, r, r, stroke)
        val hinge = -half + cs * 0.06f
        canvas.drawLine(hinge, -half + inset, hinge, half - inset, stroke)
        for (k in 0..2) {
            val y = -half * 0.62f + k * cs * 0.4f
            canvas.drawLine(-half - d * 2.5f, y, -half + cs * 0.03f, y, stroke)
        }
        matrix.setScale(half * 0.45f, half)
        matrix.postTranslate(sin(t * 0.8f + 1.2f) * half * 0.9f + yaw * half, 0f)
        glareShader.setLocalMatrix(matrix)
        canvas.drawRoundRect(-half + inset, -half + inset, half - inset, half - inset, r, r, glare)
        canvas.restore()

        // the label floats in front of the case, like the credit in the video
        canvas.save()
        canvas.concat(plane(-half, -half, half, half, yaw, pitch, -depth * 1.6f, focal))
        label.textSize = cs * 0.07f
        label.color = ink
        canvas.drawText("@" + handle(s), -half + cs * 0.05f, -half - cs * 0.025f, label)
        canvas.restore()
        // the waveform under it, a little in front as well
        canvas.save()
        canvas.concat(plane(-half, -half, half, half, yaw, pitch, -depth * 1.3f, focal))
        waveform(canvas, half, cs, d, ink, s)
        canvas.restore()
        canvas.restore()
    }

    /** where a point lands on the screen (relative to the middle) after the turn and the perspective */
    private fun project(x: Float, y: Float, z: Float, yaw: Float, pitch: Float, focal: Float, out: FloatArray) {
        val cy = cos(yaw)
        val sy = sin(yaw)
        val x1 = x * cy + z * sy
        val z1 = -x * sy + z * cy
        val cp = cos(pitch)
        val sp = sin(pitch)
        val y1 = y * cp - z1 * sp
        val z2 = y * sp + z1 * cp
        val scale = focal / max(focal * 0.2f, focal + z2)
        out[0] = x1 * scale
        out[1] = y1 * scale
    }

    /**
     * The matrix that carries the flat rectangle l,t,r,b to where it lands once it has been turned
     * about its own middle by [yaw] and [pitch] and pushed back by [z] (negative = towards the viewer).
     */
    private fun plane(l: Float, t: Float, r: Float, b: Float, yaw: Float, pitch: Float, z: Float, focal: Float): Matrix {
        val cx = (l + r) / 2f
        val cy = (t + b) / 2f
        planeSrc[0] = l; planeSrc[1] = t
        planeSrc[2] = r; planeSrc[3] = t
        planeSrc[4] = r; planeSrc[5] = b
        planeSrc[6] = l; planeSrc[7] = b
        for (i in 0..3) {
            project(planeSrc[i * 2] - cx, planeSrc[i * 2 + 1] - cy, z, yaw, pitch, focal, edgeA)
            planeDst[i * 2] = cx + edgeA[0]
            planeDst[i * 2 + 1] = cy + edgeA[1]
        }
        planeM.setPolyToPoly(planeSrc, 0, planeDst, 0, 4)
        return planeM
    }

    private fun handle(s: PhonkState): String {
        val name = s.artist.trim().lowercase().replace(Regex("\\s+"), ".").replace(Regex("[^a-z0-9._]"), "")
        return if (name.isEmpty()) "vantra" else name.take(18)
    }

    private fun spiral(canvas: Canvas, r: Float, d: Float, ink: Int) {
        fill.color = Color.BLACK
        canvas.drawCircle(0f, 0f, r, fill)
        stroke.color = withAlpha(Color.WHITE, 230)
        for (i in 1..14) {
            stroke.strokeWidth = max(1f, d * (0.8f + 1.6f * ((i * 7) % 5) / 5f))
            canvas.drawCircle(0f, 0f, r * i / 15f, stroke)
        }
    }

    private fun waveform(canvas: Canvas, half: Float, cs: Float, d: Float, ink: Int, s: PhonkState) {
        val y = half + cs * 0.13f
        val n = s.wave.size
        // each kick becomes a peak that falls off to both sides, like the spikes in the video
        for (i in 0 until n) {
            val a = s.wave[(s.waveHead + i) % n]
            peaks[i] = a
        }
        for (i in 0 until n) {
            var v = peaks[i]
            if (i > 0) v = max(v, peaks[i - 1] * 0.55f)
            if (i < n - 1) v = max(v, peaks[i + 1] * 0.55f)
            if (i > 1) v = max(v, peaks[i - 2] * 0.28f)
            if (i < n - 2) v = max(v, peaks[i + 2] * 0.28f)
            val x = -half + cs * (i + 0.5f) / n
            val ripple = 0.012f + 0.01f * sin(s.timeS * 3f + i * 0.9f)
            val up = (v * 0.16f + ripple) * cs
            if (v < 0.05f) {
                // quiet: a dotted line
                fill.color = withAlpha(ink, 170)
                canvas.drawCircle(x, y, max(1f, d * 0.9f), fill)
            } else {
                // a soft halo under a crisp round-capped line with a bright dot at the tip
                stroke.pathEffect = null
                stroke.strokeWidth = max(2f, d * 4.5f)
                stroke.color = withAlpha(ink, (v * 70f).toInt())
                canvas.drawLine(x, y - up, x, y + up, stroke)
                stroke.strokeWidth = max(1.4f, d * 1.8f)
                stroke.color = withAlpha(ink, 235)
                canvas.drawLine(x, y - up, x, y + up, stroke)
                fill.color = ink
                canvas.drawCircle(x, y - up, max(1.6f, d * 1.4f), fill)
                canvas.drawCircle(x, y + up, max(1.6f, d * 1.4f), fill)
            }
        }
    }

    // ---- light and motion -----------------------------------------------------------------

    /** the black-hole ring of the video's first frame, with its glow; it pulses with the bass */
    private fun eclipse(canvas: Canvas, ex: Float, ey: Float, minDim: Float, ink: Int, bg: Int, alpha: Float, pulse: Float) {
        val r = minDim * 0.055f * (1f + 0.35f * pulse)
        val glowR = minDim * 0.26f * (1f + 0.3f * pulse)
        matrix.setScale(glowR, glowR)
        matrix.postTranslate(ex, ey)
        glowShader.setLocalMatrix(matrix)
        glow.alpha = ((0.55f + 0.45f * pulse) * alpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawCircle(ex, ey, glowR, glow)
        val width = minDim * 0.012f
        stroke.pathEffect = null
        stroke.color = withAlpha(ink, (255f * alpha).toInt())
        stroke.strokeWidth = width
        canvas.drawCircle(ex, ey, r, stroke)
        fill.color = bg
        fill.alpha = (255f * alpha).toInt().coerceIn(0, 255)
        canvas.drawCircle(ex, ey, r - width / 2f, fill)
    }

    /** one shockwave ring: fast out, then settling, thinning and fading as it goes, with a soft halo */
    private fun shockwave(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, d: Float, progress: Float, power: Float, ink: Int) {
        val p = progress.coerceIn(0f, 1f)
        val e = 1f - (1f - p) * (1f - p)
        val radius = hypot(w, h) * (0.04f + 0.62f * e)
        val fade = (1f - p) * (1f - p)
        val strength = (0.35f + 0.55f * power.coerceIn(0f, 1f)) * fade
        stroke.pathEffect = null
        stroke.strokeWidth = d * (3f + 22f * power.coerceIn(0f, 1.4f) * (1f - p))
        stroke.color = withAlpha(ink, (strength * 0.28f * 255f).toInt())
        canvas.drawCircle(x, y, radius, stroke)
        stroke.strokeWidth = d * (1.5f + 9f * power.coerceIn(0f, 1.4f) * (1f - p))
        stroke.color = withAlpha(ink, (strength * 255f).toInt())
        canvas.drawCircle(x, y, radius, stroke)
    }

    private fun rays(canvas: Canvas, w: Float, h: Float, d: Float, amount: Float, seed: Int, light: Boolean) {
        val cx = w / 2f
        val cy = h / 2f
        val rMax = hypot(cx, cy)
        stroke.pathEffect = null
        stroke.strokeWidth = max(1f, d * 1.4f)
        val tone = if (light) 0 else 255
        val step = 2f * Math.PI.toFloat() / RayCount
        for (i in 0 until RayCount) {
            val h1 = hash01(seed * 97 + i * 31)
            val h2 = hash01(seed * 53 + i * 17 + 7)
            val a = (i + h1 * 0.8f) * step
            val r0 = rMax * (0.30f + 0.22f * h2)
            val r1 = rMax * (0.80f + 0.20f * h1)
            stroke.color = Color.argb((amount * (60f + 150f * h2)).toInt().coerceIn(0, 255), tone, tone, tone)
            canvas.drawLine(cx + cos(a) * r0, cy + sin(a) * r0, cx + cos(a) * r1, cy + sin(a) * r1, stroke)
        }
    }

    private fun withAlpha(rgb: Int, alpha: Int): Int = (rgb and 0xFFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun hash01(n: Int): Float {
        var x = n * -1640531535
        x = x xor (x ushr 15)
        x *= 0x2c1b3c6d
        x = x xor (x ushr 12)
        return (x and 0xFFFF) / 65535f
    }

    private companion object {
        const val RayCount = 34

        /** short function words are set smaller inside a phrase, like "you" in "When you" */
        val SmallWords = setOf("a", "an", "the", "of", "in", "on", "to", "at", "be", "is", "it", "you", "and", "or", "my",
            "me", "we", "he", "so", "do", "no", "if", "as", "by", "up", "out", "for", "but", "not", "that", "this", "with")
    }
}
