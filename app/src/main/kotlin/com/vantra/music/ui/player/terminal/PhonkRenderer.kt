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

    init { glow.shader = glowShader }

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
            drawWords(canvas, words, chunk?.id ?: 0, if (preview) 1f else s.timeline.age, left, top, right, bottom, d, ink, fx, bg)
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
                          left: Float, top: Float, right: Float, bottom: Float, d: Float, ink: Int, fx: PhonkFx, bg: Int) {
        val regionW = right - left
        val regionH = bottom - top
        if (regionW <= 0f || regionH <= 0f) return
        ensureLayout(words, id, regionW, regionH)
        val t = (age / 0.13f).coerceIn(0f, 1f)
        val landing = 1f - (1f - t) * (1f - t) * (1f - t)
        val slam = 1f + 0.30f * (1f - landing)
        val blur = max(1f - t, fx.blur * 0.8f)
        val tilt = ((id * 37) % 9 - 4) * 1.1f
        val cx = left + regionW / 2f
        val cy = top + regionH / 2f
        canvas.save()
        canvas.rotate(tilt, cx, cy)
        canvas.scale(slam, slam, cx, cy)
        val blockLeft = cx - blockW / 2f
        val firstBaseline = cy - lines.size * lineH / 2f + lineH * 0.80f
        // the motion blur: faint copies trailing the words while they land and on every hit
        if (blur > 0.06f) {
            val step = baseSize * 0.045f * blur
            val sign = if (id % 2 == 0) 1f else -1f
            for (k in 5 downTo 1) {
                lineSet(canvas, blockLeft, firstBaseline, withAlpha(ink, (70f * (1f - k / 6f)).toInt()), sign * k * step)
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
        canvas.save()
        canvas.translate(cx, cy)
        val pop = 1f + 0.05f * (if (preview) 0f else fx.punch)
        canvas.scale(pop, pop)
        canvas.rotate(-2.4f + 1.2f * sin(s.timeS * 0.9f) + fx.tilt * 40f)
        canvas.skew(0f, -0.02f)
        // jewel case: outer frame, inner frame, the hinge and its tabs
        val r = d * 3f
        stroke.pathEffect = null
        stroke.color = withAlpha(ink, 235)
        stroke.strokeWidth = max(1f, d * 1.1f)
        canvas.drawRoundRect(-half, -half, half, half, r, r, stroke)
        stroke.color = withAlpha(ink, 150)
        stroke.strokeWidth = max(1f, d * 0.7f)
        val inset = cs * 0.035f
        canvas.drawRoundRect(-half + inset, -half + inset, half - inset, half - inset, r, r, stroke)
        val hinge = -half + cs * 0.06f
        canvas.drawLine(hinge, -half + inset, hinge, half - inset, stroke)
        for (k in 0..2) {
            val y = -half * 0.62f + k * cs * 0.4f
            canvas.drawLine(-half - d * 2.5f, y, -half + cs * 0.03f, y, stroke)
        }
        // the disc, spinning, with the cover in harsh black and white (inverted on a white cut)
        val discR = cs * 0.41f
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
        // the label above the case, like the credit in the video
        label.textSize = cs * 0.07f
        label.color = ink
        canvas.drawText("@" + handle(s), -half + cs * 0.05f, -half - cs * 0.025f, label)
        // the waveform under it
        waveform(canvas, half, cs, d, ink, s)
        canvas.restore()
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
        val y = half + cs * 0.12f
        if (dash == null || dashDensity != d) {
            dash = DashPathEffect(floatArrayOf(d * 1.5f, d * 4f), 0f)
            dashDensity = d
        }
        stroke.color = withAlpha(ink, 200)
        stroke.strokeWidth = max(1f, d * 1.2f)
        stroke.pathEffect = dash
        canvas.drawLine(-half, y, half, y, stroke)
        stroke.pathEffect = null
        stroke.strokeWidth = max(1.4f, d * 1.6f)
        val n = s.wave.size
        for (i in 0 until n) {
            val a = s.wave[(s.waveHead + i) % n]
            if (a < 0.04f) continue
            val x = -half + cs * (i + 0.5f) / n
            val up = a * cs * 0.14f
            canvas.drawLine(x, y - up, x, y + up * 0.7f, stroke)
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
