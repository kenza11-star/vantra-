package com.vantra.music.ui.player.terminal

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Everything the renderer needs for one frame of the cinematic look and the beat-driven (phonk) mode. */
internal class TerminalFx {
    /** 0 = editor view, 1 = full close-up that follows the caret */
    var close = 0f

    /** 0..1 pulled back to a tilted wall of code */
    var wall = 0f

    /** warm light orb that comes with the wall shot */
    var orb = 0f

    /** 0..1 how sure we are the music is phonk / montagem style: a steady, heavy kick */
    var phonk = 0f
    var punch = 0f
    var tilt = 0f

    /** background level of the beat flash: a hard cut, 0 = black, 1 = white */
    var flash = 0f
    var lines = 0f
    var split = 0f
    var lineSeed = 0
    var bpm = 0f

    /** progress 0..1 of the shockwave ring of the last hit, -1 when there is none */
    var ring = -1f
    var ringPower = 0f

    fun clear() {
        close = 0f; wall = 0f; orb = 0f; phonk = 0f
        punch = 0f; tilt = 0f; flash = 0f; lines = 0f; split = 0f; bpm = 0f
        ring = -1f; ringPower = 0f
    }
}

/**
 * Chooses the camera shot and drives the beat effects, from the player's own beat tracker.
 *
 * Shots: close-up on the line being typed; a pulled-back, tilted wall of code with a warm orb during
 * instrumentals, climax markers and breakdowns.
 *
 * Phonk mode is detected, never assumed: it needs a run of kicks at a steady 85-200 BPM and heavy
 * bass. Only then do kicks punch the picture, flash it (at most every other kick and never faster
 * than about 2.6 times a second), throw speed lines and split the colours. When the bass drops out
 * (a breakdown) the camera pulls back, and the kick that returns is a drop: one big hit.
 */
internal class TerminalCinema {
    private val gaps = FloatArray(MaxGaps)
    private val sorted = FloatArray(MaxGaps)
    private var gapCount = 0
    private var gapHead = 0
    private var sinceKick = 10f
    private var sinceFlash = 10f
    private var bassAvg = 0f
    private var score = 0f
    private var quiet = 0f
    private var breakdown = 0f
    private var parity = 0
    private var tiltSign = 1f
    private var punch = 0f
    private var flashAge = 99f
    private var flashPeak = 1f
    private var flashTwice = false
    private var flashCycle = 0
    private var ringAge = 99f
    private var ringSpan = 0.45f
    private var ringPower = 0f
    private var lastAmount = 0f
    private var lines = 0f
    private var drop = 0f
    private var seed = 0
    private val closeSpring = Spring()
    private val wallSpring = Spring()
    private val orbSpring = Spring()

    /** tempo of the current run of kicks, 0 until there is one */
    var bpm = 0f
        private set

    /** how many flashes have fired, and how many drops were detected (for tests and the status line) */
    var flashCount = 0
        private set
    var dropCount = 0
        private set

    /** shortest time between two flash triggers so far (seconds), for tests */
    var minFlashGap = 99f
        private set

    /** a seek, a new song, motion switched off: forget the beat history */
    fun reset() {
        gapCount = 0; gapHead = 0
        sinceKick = 10f; sinceFlash = 10f
        bassAvg = 0f; score = 0f; quiet = 0f; breakdown = 0f
        parity = 0
        punch = 0f; lines = 0f; drop = 0f
        flashAge = 99f; ringAge = 99f; lastAmount = 0f
        bpm = 0f
    }

    private fun resetShot() {
        closeSpring.snap(0f); wallSpring.snap(0f); orbSpring.snap(0f)
    }

    private fun smooth(dt: Float, tau: Float): Float = 1f - exp(-dt / tau)

    private fun smoothstep(lo: Float, hi: Float, x: Float): Float {
        val t = ((x - lo) / (hi - lo)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * @param kick strength of a kick detected in this very frame, else 0
     * @param audio whether the beat values are real (the local player's audio is being read)
     * @param motion false when motion is off or reduced: no shots, no effects
     * @param typing whether the typing animation is on (the close-up follows the caret)
     * @param idle no lyric is active (intro, instrumental, outro)
     * @param gain the user's bass response, scales the strength of the beat effects
     * @param shots false keeps the plain editor view: no close-up and no wall shot
     * @param flashScale brightness of the beat flash: 0 off, below 1 soft, 1 full
     */
    fun update(
        dt: Float, kick: Float, bass: Float, bassDrive: Float, energy: Float,
        audio: Boolean, motion: Boolean, typing: Boolean, idle: Boolean, hasLyrics: Boolean,
        climax: Float, jump: Boolean, gain: Float, out: TerminalFx,
        shots: Boolean = true, flashScale: Float = 1f,
    ) {
        val h = dt.coerceIn(0.001f, 0.1f)
        if (!motion) {
            reset(); resetShot(); out.clear()
            return
        }
        if (jump) reset()
        sinceKick += h
        sinceFlash += h
        bassAvg += ((if (audio) bass else 0f) - bassAvg) * smooth(h, 2f)
        punch *= exp(-h / 0.15f)
        flashAge += h
        ringAge += h
        lines *= exp(-h / 0.20f)
        drop *= exp(-h / 0.45f)

        if (audio && kick > 0f) {
            val strength = kick.coerceIn(0f, 1f)
            val gap = sinceKick
            if (gap >= MinGap && gap <= MaxGap) {
                gaps[gapHead] = gap
                gapHead = (gapHead + 1) % MaxGaps
                if (gapCount < MaxGaps) gapCount++
            }
            val returning = gap > DropQuiet && score > 0.5f
            sinceKick = 0f
            punch = max(punch, 0.55f + 0.45f * strength)
            tiltSign = -tiltSign
            parity++
            lines = max(lines, 0.3f + 0.5f * strength)
            // Only music that has proved to be phonk-like flashes: a hard cut, every other kick.
            if (parity % 2 == 0 && sinceFlash >= FlashGap && lastAmount > 0.5f) {
                fire(false)
                hit(0.6f, 0.45f)
            }
            if (returning) {
                drop = 1f; punch = 1.25f; lines = 1f
                breakdown = 0f; quiet = 0f
                fire(true)
                hit(1f, 0.7f)
                dropCount++
            }
        }

        // tempo and steadiness of the recent kicks
        var median = 0f
        var steady = 0f
        if (gapCount >= 4) {
            for (i in 0 until gapCount) sorted[i] = gaps[i]
            for (i in 1 until gapCount) {
                val v = sorted[i]
                var j = i - 1
                while (j >= 0 && sorted[j] > v) { sorted[j + 1] = sorted[j]; j-- }
                sorted[j + 1] = v
            }
            median = sorted[gapCount / 2]
            var near = 0
            for (i in 0 until gapCount) if (abs(gaps[i] - median) <= median * 0.12f) near++
            steady = near.toFloat() / gapCount
        }
        bpm = if (median > 0f) 60f / median else 0f

        val sure = gapCount >= 5 && steady >= 0.65f && median >= 0.30f && median <= 0.70f && bassAvg > 0.2f
        val target = if (sure && sinceKick < HoldKick) 1f else 0f
        // quick to believe, slow to let go: a breakdown must not switch the mode off
        val tau = if (target > score) 1.0f else if (sinceKick > HoldKick) 9f else 2.5f
        score += (target - score) * smooth(h, tau)
        val amount = smoothstep(0.35f, 0.75f, score)

        if (audio && bassDrive < 0.12f && sinceKick > 0.9f) quiet += h else quiet = max(0f, quiet - 3f * h)
        val breakTarget = if (score > 0.4f && quiet > 0.8f) 1f else 0f
        breakdown += (breakTarget - breakdown) * smooth(h, 0.5f)

        // the shot
        val wallTarget = when {
            !hasLyrics || !shots -> 0f
            idle -> 1f
            else -> max(climax.coerceIn(0f, 1f), breakdown * 0.85f)
        }
        val closeTarget = if (hasLyrics && typing && shots) 1f else 0f
        closeSpring.step(h, closeTarget, 30f, 11f)
        wallSpring.step(h, wallTarget, 14f, 8f)
        orbSpring.step(h, wallTarget * (0.35f + 0.25f * energy.coerceIn(0f, 1f)), 10f, 6f)
        val wall = wallSpring.value.coerceIn(0f, 1f)
        out.wall = wall
        out.close = (closeSpring.value.coerceIn(0f, 1f) * (1f - wall)).coerceIn(0f, 1f)
        out.orb = orbSpring.value.coerceIn(0f, 1f)

        // the beat effects, only for music that has proved to be phonk-like (a drop always lands)
        val a = max(amount, drop)
        val g = gain.coerceIn(0f, 1.5f)
        out.phonk = amount
        out.punch = (punch * a * g).coerceIn(0f, 1.3f)
        out.tilt = tiltSign * punch * 0.028f * a * g
        out.flash = (flashPulse(flashAge, flashTwice) * flashPeak * min(1f, g) * flashScale.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        out.lines = (max(lines, drop) * a * g).coerceIn(0f, 1f)
        out.split = (punch * a * g).coerceIn(0f, 1f)
        out.lineSeed = seed
        out.bpm = bpm
        out.ring = if (ringAge < ringSpan) ringAge / ringSpan else -1f
        out.ringPower = ringPower * min(1f, g)
        lastAmount = amount
    }

    /** One flash: a hard cut to white (or light grey every third time), not a fade. */
    private fun fire(twice: Boolean) {
        if (sinceFlash < minFlashGap) minFlashGap = sinceFlash
        flashAge = 0f
        flashPeak = if (twice) 1f else FlashLevels[flashCycle % FlashLevels.size]
        flashTwice = twice
        flashCycle++
        sinceFlash = 0f
        seed++
        flashCount++
    }

    /** The shockwave ring that leaves the centre with a hit. */
    private fun hit(power: Float, seconds: Float) {
        ringAge = 0f
        ringPower = power
        ringSpan = seconds
    }

    /**
     * 1 while the flash is on, 0 otherwise, with no ramps: phonk edits cut between frames. A drop
     * gets one white-black-white stutter; ordinary flashes are single, so the rate stays safe.
     */
    private fun flashPulse(t: Float, twice: Boolean): Float {
        if (t < FlashHold) return 1f
        if (!twice) return 0f
        if (t < FlashHold + FlashDip) return 0f
        if (t < 2f * FlashHold + FlashDip) return 1f
        return 0f
    }

    private companion object {
        const val MaxGaps = 10
        const val MinGap = 0.28f
        const val MaxGap = 0.9f

        /** silence from the kick that makes the next kick a drop */
        const val DropQuiet = 1.2f
        const val HoldKick = 1.2f

        /** flashes never come faster than this, for photosensitive viewers */
        const val FlashGap = 0.40f
        const val FlashHold = 0.075f
        const val FlashDip = 0.04f

        /** background level of successive flashes: white, white, light grey */
        val FlashLevels = floatArrayOf(1f, 1f, 0.62f)
    }
}
