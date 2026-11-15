package com.vantra.music.ui.player.terminal

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/** Retained transport/audio state, independent of Compose. Only freshly sampled PCM may produce
 * an onset envelope. Playback position and actual transport events own all timing decisions.
 */
internal class TerminalSynchronization {
    val tracker = BeatTracker()
    var source = BeatSource.NONE; private set
    var pulse = 0f; private set
    var generation = 0L; private set
    private var lastPosition = Long.MIN_VALUE
    private var lastMediaId: String? = null
    private var lastPlayer: Any? = null
    private var lastEpoch = Long.MIN_VALUE
    private var wasPlaying = false
    private var boundary = false
    var timelineJump = false; private set

    /** True for initial frame, player/track/repeat changes, seek and pause/resume. [epoch] comes
     * from Media3 discontinuity/transition callbacks, so even a tiny seek re-primes the detector.
     */
    fun beginFrame(dt: Float, positionMs: Long, mediaId: String?, player: Any?, epoch: Long,
                   playing: Boolean, speed: Float): Boolean {
        val first = lastPosition == Long.MIN_VALUE
        val expected = if (playing && wasPlaying) dt * 1000f * speed else 0f
        timelineJump = first || mediaId != lastMediaId || player !== lastPlayer || epoch != lastEpoch ||
            abs(positionMs - lastPosition - expected) > 400f
        boundary = timelineJump || playing != wasPlaying
        if (boundary) {
            tracker.resetAll()
            pulse = 0f
            source = BeatSource.NONE
            generation++
        }
        lastPosition = positionMs; lastMediaId = mediaId; lastPlayer = player
        lastEpoch = epoch; wasPlaying = playing
        return boundary
    }

    /** [heard] is AudioSpectrum.read's success at the CURRENT playback position, never a timer. */
    fun react(dt: Float, playing: Boolean, enabled: Boolean, hasLyrics: Boolean, heard: Boolean) {
        val audio = playing && enabled && heard && !boundary
        tracker.update(dt, audio)
        pulse = if (audio) max(tracker.kick, pulse * exp(-dt.coerceIn(0f, 0.1f) / 0.16f)) else 0f
        source = when {
            !playing || !enabled -> BeatSource.NONE
            audio -> BeatSource.AUDIO
            hasLyrics -> BeatSource.LYRIC_TIMING
            else -> BeatSource.PLAYBACK_TIMING
        }
        boundary = false
    }

    /** Hidden/stopped scenes must not replay a pulse when they become visible again. */
    fun suspendAnalysis() {
        tracker.resetAll(); pulse = 0f; source = BeatSource.NONE
        lastPosition = Long.MIN_VALUE
    }
}

/** Crossfade the old and new active block; timing still comes from document.locate. */
internal class TerminalBlockTransition {
    private val spring = Spring(1f)
    var line = Int.MIN_VALUE; private set
    var previousLine = Int.MIN_VALUE; private set
    val mix: Float get() = spring.value.coerceIn(0f, 1f)

    fun update(dt: Float, nextLine: Int, snap: Boolean = false) {
        if (snap || line == Int.MIN_VALUE) {
            line = nextLine; previousLine = Int.MIN_VALUE; spring.snap(1f)
        } else if (line != nextLine) {
            previousLine = line; line = nextLine; spring.snap(0f)
        }
        spring.step(dt, 1f, 100f, 20f)
    }
}
