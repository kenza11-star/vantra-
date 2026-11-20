package com.vantra.music.ui.player.geometry

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The Geometry style's game, kept free of Android so it can be tested: a cube that runs by itself
 * through a level that is built out of the song, jumping whenever a finger is down. Everything is
 * measured in blocks (one block is the cube's size) and seconds of game time; the physics are the
 * ones of the genre's classic cube, so a jump is about 2.2 blocks high and 4 blocks long at normal speed.
 */
internal object Gd {
    /** blocks per second at normal speed */
    const val Speed = 10.386f
    const val Gravity = 114.98f
    const val JumpVelocity = 22.36f
    const val TerminalVelocity = 30f
    /** the fixed step every simulation runs at (game seconds) */
    const val Step = 1f / 240f
    /** how many degrees a spinning cube turns per second while in the air */
    const val SpinRate = 460f
    /** where the cube leaves the ground, in blocks before a single spike, when the jump is taken on the beat */
    const val TakeoffBlocks = 1.9f
    /** the speed a jump pad throws the cube up at (a pad lifts it ~4.4 blocks, twice a jump) */
    const val PadVelocity = 32f
    /** a pad only acts on a cube that isn't already flying up faster than this */
    const val PadGuard = 20f
    /** the ship: how hard a held finger pushes it up and how hard it falls, and its top speed, in blocks per second */
    const val ShipUp = 62f
    const val ShipDown = 52f
    const val ShipMax = 10f
    /** the roof of the level: the ship (and a cube that has flown up) stop under it */
    const val Ceiling = 7f
    /** the ball falls a little slower than a cube; a tap on a floor or roof sends it to the other one */
    const val BallGravity = 91.98f
    /** the ufo: a gentle fall, and every tap, in the air too, throws it up a little */
    const val UfoGravity = 70f
    const val UfoJump = 14f
    const val UfoMaxUp = 14f
    const val UfoMaxDown = 12f
}

/** what a piece of a level is */
internal enum class Kind { SPIKE, SPIKE_DOWN, BLOCK, PAD, ORB, PORTAL_SHIP, PORTAL_CUBE, PORTAL_FLIP, PORTAL_UNFLIP, PORTAL_WAVE, PORTAL_BALL, PORTAL_UFO }

/** one piece of a pattern, [dx] blocks to the right of where the pattern is anchored */
internal class Cell(val kind: Kind, val dx: Float, val y: Float, val w: Float, val h: Float)

/** the shapes a level is built from; each one has been proven passable by a simulation of [Gd]'s physics */
internal enum class Pattern(val hard: Boolean, val ship: Boolean, vararg val cells: Cell) {
    SPIKE(false, false, Cell(Kind.SPIKE, 0f, 0f, 1f, 1f)),
    DOUBLE(false, false, Cell(Kind.SPIKE, 0f, 0f, 1f, 1f), Cell(Kind.SPIKE, 1f, 0f, 1f, 1f)),
    TRIPLE(true, false, Cell(Kind.SPIKE, 0f, 0f, 1f, 1f), Cell(Kind.SPIKE, 1f, 0f, 1f, 1f), Cell(Kind.SPIKE, 2f, 0f, 1f, 1f)),
    BLOCK(false, false, Cell(Kind.BLOCK, 0f, 0f, 1f, 1f)),
    BLOCK_SPIKE(true, false, Cell(Kind.BLOCK, 0f, 0f, 1f, 1f), Cell(Kind.SPIKE, 2f, 0f, 1f, 1f)),
    STAIR(false, false, Cell(Kind.BLOCK, 0f, 0f, 1f, 1f), Cell(Kind.BLOCK, 1f, 0f, 1f, 2f)),
    SPIKE_ON_BLOCK(false, false, Cell(Kind.BLOCK, 0f, 0f, 1f, 1f), Cell(Kind.SPIKE, 0f, 1f, 1f, 1f)),
    PLATFORM(false, false, Cell(Kind.BLOCK, 0f, 0f, 3f, 1f)),
    BLOCK_GAP_SPIKE(true, false, Cell(Kind.BLOCK, 0f, 0f, 1f, 1f), Cell(Kind.SPIKE, 3f, 0f, 1f, 1f)),
    // a pad throws the cube over a run of spikes, or over a tall block, without a tap
    PAD_SPIKES(false, false, Cell(Kind.PAD, 0f, 0f, 1f, 0.25f), Cell(Kind.SPIKE, 3f, 0f, 1f, 1f), Cell(Kind.SPIKE, 4f, 0f, 1f, 1f), Cell(Kind.SPIKE, 5f, 0f, 1f, 1f)),
    PAD_BLOCK(false, false, Cell(Kind.PAD, 0f, 0f, 1f, 0.25f), Cell(Kind.BLOCK, 3f, 0f, 1f, 3f)),
    PAD_WIDE(true, false, Cell(Kind.PAD, 0f, 0f, 1f, 0.25f), Cell(Kind.SPIKE, 2.5f, 0f, 1f, 1f), Cell(Kind.SPIKE, 3.5f, 0f, 1f, 1f),
        Cell(Kind.SPIKE, 4.5f, 0f, 1f, 1f), Cell(Kind.SPIKE, 5.5f, 0f, 1f, 1f)),
    // an orb in the air: a jump, then a second tap on the orb carries the cube over spikes too wide for one jump
    ORB_SPIKES4(true, false, Cell(Kind.SPIKE, 0f, 0f, 1f, 1f), Cell(Kind.SPIKE, 1f, 0f, 1f, 1f), Cell(Kind.SPIKE, 2f, 0f, 1f, 1f),
        Cell(Kind.SPIKE, 3f, 0f, 1f, 1f), Cell(Kind.ORB, 0f, 1.8f, 1f, 1f)),
    ORB_SPIKES5(true, false, Cell(Kind.SPIKE, 0f, 0f, 1f, 1f), Cell(Kind.SPIKE, 1f, 0f, 1f, 1f), Cell(Kind.SPIKE, 2f, 0f, 1f, 1f),
        Cell(Kind.SPIKE, 3f, 0f, 1f, 1f), Cell(Kind.SPIKE, 4f, 0f, 1f, 1f), Cell(Kind.ORB, 0.5f, 2.2f, 1f, 1f)),
    // the ship section: a portal turns the cube into a ship that rises while a finger is held and falls when it is lifted,
    // gates (pillars from the floor and the roof, spikes on both) are flown through, and a second portal gives the cube back
    PORTAL_IN(false, false, Cell(Kind.PORTAL_SHIP, 0f, 0f, 1f, Gd.Ceiling)),
    // the wave: a portal turns the cube into a wave that climbs at 45 degrees while a finger is held and dives at 45 when it is lifted
    PORTAL_WAVE(false, false, Cell(Kind.PORTAL_WAVE, 0f, 0f, 1f, Gd.Ceiling)),
    PORTAL_OUT(false, true, Cell(Kind.PORTAL_CUBE, 0f, 0f, 1f, Gd.Ceiling)),
    // gravity portals: the cube runs along the roof, upside down, until a second portal puts it back
    PORTAL_FLIP(false, false, Cell(Kind.PORTAL_FLIP, 0f, 0f, 1f, Gd.Ceiling)),
    PORTAL_UNFLIP(false, false, Cell(Kind.PORTAL_UNFLIP, 0f, 0f, 1f, Gd.Ceiling)),
    GATE_LOW(false, true, Cell(Kind.BLOCK, 0f, 0f, 1f, 3f)),
    GATE_HIGH(false, true, Cell(Kind.BLOCK, 0f, 4f, 1f, 3f)),
    GATE_STEPS(false, true, Cell(Kind.BLOCK, 0f, 0f, 1f, 1f), Cell(Kind.BLOCK, 1f, 0f, 1f, 2f), Cell(Kind.BLOCK, 2f, 0f, 1f, 3f)),
    GATE_SPIKES(false, true, Cell(Kind.SPIKE, 0f, 0f, 1f, 1f), Cell(Kind.SPIKE, 1f, 0f, 1f, 1f), Cell(Kind.SPIKE, 2f, 0f, 1f, 1f),
        Cell(Kind.SPIKE_DOWN, 0f, 6f, 1f, 1f), Cell(Kind.SPIKE_DOWN, 1f, 6f, 1f, 1f), Cell(Kind.SPIKE_DOWN, 2f, 6f, 1f, 1f)),
    GATE_ZIG(true, true, Cell(Kind.BLOCK, 0f, 0f, 1f, 2f), Cell(Kind.BLOCK, 2.5f, 5f, 1f, 2f)),
    GATE_SQUEEZE(true, true, Cell(Kind.BLOCK, 0f, 0f, 2f, 2f), Cell(Kind.BLOCK, 0f, 4f, 2f, 3f)),
    // the ufo: every tap, in the air too, is a small hop; it flies the same gates as the ship
    PORTAL_UFO(false, false, Cell(Kind.PORTAL_UFO, 0f, 0f, 1f, Gd.Ceiling)),
    // the ball: a tap sends it from the floor to the roof and back; these are what it has to change sides for
    PORTAL_BALL(false, false, Cell(Kind.PORTAL_BALL, 0f, 0f, 1f, Gd.Ceiling)),
    BALL_LOW(false, false, Cell(Kind.BLOCK, 0f, 0f, 1f, 3f)),
    BALL_HIGH(false, false, Cell(Kind.BLOCK, 0f, 4f, 1f, 3f)),
    BALL_SPIKES_FLOOR(false, false, Cell(Kind.SPIKE, 0f, 0f, 1f, 1f), Cell(Kind.SPIKE, 1f, 0f, 1f, 1f), Cell(Kind.SPIKE, 2f, 0f, 1f, 1f)),
    BALL_SPIKES_ROOF(false, false, Cell(Kind.SPIKE_DOWN, 0f, 6f, 1f, 1f), Cell(Kind.SPIKE_DOWN, 1f, 6f, 1f, 1f), Cell(Kind.SPIKE_DOWN, 2f, 6f, 1f, 1f)),
    BALL_ZIG(true, false, Cell(Kind.SPIKE, 0f, 0f, 1f, 1f), Cell(Kind.SPIKE_DOWN, 2.5f, 6f, 1f, 1f));

    /** the widest the pattern reaches to the right of its anchor */
    val reach: Float = cells.maxOf { it.dx + it.w }
    val isPortal: Boolean = cells.any { it.kind == Kind.PORTAL_SHIP || it.kind == Kind.PORTAL_CUBE || it.kind == Kind.PORTAL_FLIP || it.kind == Kind.PORTAL_UNFLIP || it.kind == Kind.PORTAL_WAVE || it.kind == Kind.PORTAL_BALL || it.kind == Kind.PORTAL_UFO }
    /** 1 for the shapes of a ball section, 0 for everything else */
    val section: Int = if (name.startsWith("BALL_")) 1 else 0
    /** made of spikes and blocks only, so it can be turned upside down for a gravity section */
    val flippable: Boolean = cells.all { it.kind == Kind.SPIKE || it.kind == Kind.BLOCK }
}

/** something in the level: a spike (the hitbox is a sliver in the middle of its triangle) or a solid block */
internal class Obstacle(val kind: Kind, val x: Float, val y: Float, val w: Float, val h: Float) {
    val spike: Boolean get() = kind == Kind.SPIKE
}

/** a pattern the song asks for, arriving at the cube's place at [arrivalMs] of the song */
internal class Spec(val arrivalMs: Long, val pattern: Pattern, val mirrored: Boolean = false)

/** what happened in a [GeometryWorld.step] */
internal enum class StepResult { NONE, JUMPED, LANDED, DIED }

/** the cube and its level */
internal class GeometryWorld {
    var x = 0f
    var y = 0f
    var vy = 0f
    var grounded = true
    /** degrees, a cube that comes down square again */
    var rotation = 0f
    var alive = true
    /** whether a finger was down on the last step: an orb needs a fresh tap, not a held one */
    var held = false
    /** flying as a ship instead of running as a cube */
    var ship = false
    /** running on the roof, upside down */
    var flipped = false
    /** flying as a wave: straight up or straight down at the speed it runs at */
    var wave = false
    /** a ball: it does not jump, a tap sends it to the other side */
    var ball = false
    /** a ufo: every tap is a small hop */
    var ufo = false
    val obstacles = ArrayList<Obstacle>()

    fun reset() {
        x = 0f; y = 0f; vy = 0f; grounded = true; rotation = 0f; alive = true; held = false; ship = false; flipped = false; wave = false; ball = false; ufo = false
        obstacles.clear()
    }

    /** one fixed step of [Gd.Step] game seconds, [press] being whether a finger is down */
    fun step(press: Boolean, prune: Boolean = true): StepResult {
        if (!alive) return StepResult.NONE
        var result = StepResult.NONE
        val dt = Gd.Step
        val rising = press && !held
        held = press
        // the ball changes sides on a fresh tap while it rolls on the floor or the roof
        if (ball && rising && grounded) {
            flipped = !flipped
            grounded = false
            vy = 0f
        }
        val flying = ship || ufo
        // a cube running on the roof is the same cube upside down: it is looked at in a mirror, the
        // level's pieces turned over with it
        val mirror = flipped && !flying && !wave
        if (mirror) { y = Gd.Ceiling - 1f - y; vy = -vy }
        fun unmirror() { if (mirror) { y = Gd.Ceiling - 1f - y; vy = -vy } }
        fun die(): StepResult { unmirror(); alive = false; return StepResult.DIED }
        var newFlip: Boolean? = null
        // pads and orbs act on where the cube is now (the ship ignores them)
        if (!flying && !wave && !ball) for (o in obstacles) {
            if (o.x > x + 2f || o.x + o.w < x - 2f) continue
            val oy = if (mirror) Gd.Ceiling - o.y - o.h else o.y
            if (o.kind == Kind.PAD) {
                if (vy < Gd.PadGuard && x + 0.5f > o.x && x - 0.5f < o.x + o.w && y < oy + o.h && y + 1f > oy) {
                    vy = Gd.PadVelocity; grounded = false; result = StepResult.JUMPED
                }
            } else if (o.kind == Kind.ORB) {
                if (rising && vy < Gd.JumpVelocity * 0.75f && abs(x - (o.x + 0.5f)) < 1f && abs(y + 0.5f - (oy + 0.5f)) < 1f) {
                    vy = Gd.JumpVelocity; grounded = false; result = StepResult.JUMPED
                }
            }
        }
        val previousY = y
        val previousX = x
        var wasGrounded = false
        if (wave) {
            // no inertia at all: a held finger is a climb at 45 degrees, a lifted one a dive; floor and roof are slid along
            vy = if (press) Gd.Speed else -Gd.Speed
            y += vy * dt
            x += Gd.Speed * dt
            grounded = false
            if (y <= 0f) { y = 0f; vy = 0f }
            if (y >= Gd.Ceiling - 1f) { y = Gd.Ceiling - 1f; vy = 0f }
        } else if (ufo) {
            // gravity pulls it down gently and every fresh tap throws it up
            vy -= Gd.UfoGravity * dt
            if (rising) vy = Gd.UfoJump
            vy = vy.coerceIn(-Gd.UfoMaxDown, Gd.UfoMaxUp)
            y += vy * dt
            x += Gd.Speed * dt
            grounded = false
            if (y <= 0f) { y = 0f; vy = max(vy, 0f) }
            if (y >= Gd.Ceiling - 1f) { y = Gd.Ceiling - 1f; vy = min(vy, 0f) }
        } else if (ship) {
            // a held finger pushes up, a lifted one lets it fall; the floor and the roof stop it without hurting
            vy += (if (press) Gd.ShipUp else -Gd.ShipDown) * dt
            vy = vy.coerceIn(-Gd.ShipMax, Gd.ShipMax)
            y += vy * dt
            x += Gd.Speed * dt
            grounded = false
            if (y <= 0f) { y = 0f; vy = max(vy, 0f) }
            if (y >= Gd.Ceiling - 1f) { y = Gd.Ceiling - 1f; vy = min(vy, 0f) }
        } else {
            if (grounded && press && !ball) {
                vy = Gd.JumpVelocity
                grounded = false
                result = StepResult.JUMPED
            }
            vy = max(vy - (if (ball) Gd.BallGravity else Gd.Gravity) * dt, -Gd.TerminalVelocity)
            y += vy * dt
            x += Gd.Speed * dt
            wasGrounded = grounded
            grounded = false
            if (y <= 0f) {
                y = 0f; vy = 0f; grounded = true
            }
        }
        for (o in obstacles) {
            if (o.x > x + 1f || o.x + o.w < x - 1f) continue
            val oy = if (mirror) Gd.Ceiling - o.y - o.h else o.y
            val kind = if (!mirror) o.kind else when (o.kind) {
                Kind.SPIKE -> Kind.SPIKE_DOWN
                Kind.SPIKE_DOWN -> Kind.SPIKE
                else -> o.kind
            }
            when (kind) {
                Kind.SPIKE, Kind.SPIKE_DOWN -> {
                    val centre = o.x + o.w / 2f
                    val up = kind == Kind.SPIKE
                    val low = if (up) oy + SpikeBottom else oy + 1f - SpikeTop
                    val high = if (up) oy + SpikeTop else oy + 1f - SpikeBottom
                    // the wave is a small thing: only its middle can be hurt
                    val halfWidth = if (wave) WaveHalf else 0.5f
                    val bottom = if (wave) y + 0.5f - WaveHalf else y
                    val topEdge = if (wave) y + 0.5f + WaveHalf else y + 1f
                    if (abs(x - centre) < halfWidth + SpikeHalfWidth && bottom < high && topEdge > low) return die()
                }
                Kind.BLOCK -> {
                    val top = oy + o.h
                    val outer = x + 0.5f > o.x && x - 0.5f < o.x + o.w && y < top && y + 1f > oy
                    if (wave) {
                        if (x + WaveHalf > o.x && x - WaveHalf < o.x + o.w && y + 0.5f + WaveHalf > oy && y + 0.5f - WaveHalf < top) return die()
                    } else if (outer) {
                        if (previousY >= top - LandTolerance && vy <= 0f) {
                            y = top; vy = 0f; grounded = !flying
                        } else if (flying && previousY + 1f <= oy + LandTolerance && vy >= 0f) {
                            // the ship's nose under a hanging block slides along it
                            y = oy - 1f; vy = 0f
                        } else {
                            val inner = x + 0.15f > o.x && x - 0.15f < o.x + o.w && y + 0.65f > oy && y + 0.35f < top
                            if (inner) return die()
                        }
                    }
                }
                Kind.PORTAL_SHIP, Kind.PORTAL_CUBE, Kind.PORTAL_WAVE, Kind.PORTAL_BALL, Kind.PORTAL_UFO -> {
                    val centre = o.x + 0.5f
                    if (previousX < centre && centre <= x) {
                        ship = o.kind == Kind.PORTAL_SHIP
                        wave = o.kind == Kind.PORTAL_WAVE
                        ball = o.kind == Kind.PORTAL_BALL
                        ufo = o.kind == Kind.PORTAL_UFO
                        grounded = false
                        newFlip = false
                    }
                }
                Kind.PORTAL_FLIP, Kind.PORTAL_UNFLIP -> {
                    val centre = o.x + 0.5f
                    if (previousX < centre && centre <= x && !flying && !wave && !ball) newFlip = o.kind == Kind.PORTAL_FLIP
                }
                else -> Unit
            }
        }
        if (flying || wave) {
            rotation = 0f
        } else if (ball) {
            // the ball rolls on whichever side it is on
            rotation += 420f * dt * (if (flipped) -1f else 1f)
        } else if (grounded) {
            val target = round(rotation / 90f) * 90f
            rotation += (target - rotation) * min(1f, dt * 28f)
            if (!wasGrounded && result == StepResult.NONE) result = StepResult.LANDED
        } else {
            rotation += Gd.SpinRate * dt
        }
        unmirror()
        // a gravity portal: from now on up is down (or the other way), and the cube starts to fall
        val turn = newFlip
        if (turn != null && turn != flipped) {
            flipped = turn
            vy = 0f
            grounded = false
        } else if (turn != null) flipped = turn
        // forget what is far behind
        var i = 0
        while (prune && i < obstacles.size) {
            if (obstacles[i].x + obstacles[i].w < x - 8f) obstacles.removeAt(i) else i++
        }
        return result
    }

    companion object {
        const val SpikeHalfWidth = 0.125f
        const val SpikeBottom = 0.08f
        const val SpikeTop = 0.52f
        const val LandTolerance = 0.30f
        const val WaveHalf = 0.2f
    }
}

/**
 * What the song's beat is doing: its period and where the next beats fall, learnt from the kicks the
 * audio analysis reports. Until it has heard enough kicks it says it isn't [confident].
 */
internal class BeatGrid {
    private val kicks = LongArray(24)
    private var count = 0
    var periodMs = 0f; private set
    var anchorMs = 0f; private set
    var confident = false; private set

    val bpm: Float get() = if (periodMs > 0f) 60000f / periodMs else 0f

    fun reset() { count = 0; periodMs = 0f; anchorMs = 0f; confident = false }

    fun onKick(songMs: Long) {
        if (count > 0 && songMs - kicks[(count - 1) % kicks.size] < 220) return
        kicks[count % kicks.size] = songMs
        count++
        learn(songMs)
    }

    private fun learn(songMs: Long) {
        val n = min(count, kicks.size)
        if (n < 7) return
        val intervals = ArrayList<Float>(n - 1)
        for (i in 1 until n) {
            val a = kicks[(count - n + i - 1) % kicks.size]
            val b = kicks[(count - n + i) % kicks.size]
            var d = (b - a).toFloat()
            // a kick missed or a half-beat found: fold the interval into one beat's range
            while (d > 760f) d /= 2f
            while (d < 330f && d > 0f) d *= 2f
            if (d in 330f..760f) intervals.add(d)
        }
        if (intervals.size < 5) return
        intervals.sort()
        val median = intervals[intervals.size / 2]
        val agreeing = intervals.count { abs(it - median) <= median * 0.12f }
        if (agreeing < intervals.size * 0.55f) return
        val mean = intervals.filter { abs(it - median) <= median * 0.12f }.average().toFloat()
        periodMs = if (periodMs == 0f) mean else periodMs + (mean - periodMs) * 0.35f
        if (!confident) {
            confident = true
            anchorMs = songMs.toFloat()
        } else {
            // the beat nearest this kick moves a little towards it, the way a ear keeps time
            val steps = round((songMs - anchorMs) / periodMs)
            val predicted = anchorMs + steps * periodMs
            val error = songMs - predicted
            if (abs(error) < periodMs * 0.3f) anchorMs = predicted + error * 0.3f
        }
    }

    /** the first beat strictly after [afterMs] on the grid ([fallbackPeriodMs] if none has been heard) */
    fun nextBeatAfter(afterMs: Long, fallbackPeriodMs: Float): Long {
        val period = if (periodMs > 0f) periodMs else fallbackPeriodMs
        val anchor = if (periodMs > 0f) anchorMs else 0f
        val n = ceil((afterMs + 1 - anchor) / period)
        return (anchor + n * period).toLong()
    }
}

/**
 * Keeps track of where the cube can be once every pattern placed so far is behind it, and only lets a
 * new pattern in if the cube can still get through it from one of those places. That way a level is
 * always playable, however the patterns are chained, and the check is the game's own physics. The
 * search goes one decision (four steps) at a time over every place the cube could be, merging the
 * ones that are alike, so a state is never mistaken for one that is further along.
 */
internal class Playability {
    private class St(
        val x: Float, val y: Float, val vy: Float, val grounded: Boolean, val held: Boolean,
        val ship: Boolean, val flipped: Boolean, val wave: Boolean, val ball: Boolean, val ufo: Boolean,
    )

    private var states: List<St> = listOf(St(0f, 0f, 0f, true, false, false, false, false, false, false))
    private val world = GeometryWorld()

    private fun flags(s: St): Long =
        (if (s.grounded) 1L else 0L) or (if (s.held) 2L else 0L) or (if (s.ship) 4L else 0L) or (if (s.flipped) 8L else 0L) or
            (if (s.wave) 16L else 0L) or (if (s.ball) 32L else 0L) or (if (s.ufo) 64L else 0L)

    /** two states are alike when they are at the same place, about the same height and speed, in the same mode */
    private fun key(s: St): Long {
        val coarse = s.ship || s.wave || s.ball || s.ufo
        val x = (s.x * 10f).roundToInt().toLong() and 0xFFFFF
        val y = (if (coarse) (s.y * 4f).roundToInt() else (s.y * 10f).roundToInt()).toLong() and 0xFF
        val v = (if (coarse) (s.vy * 0.8f).roundToInt() + 64 else (s.vy * 2.5f).roundToInt() + 128).toLong() and 0x1FF
        return (x shl 30) or (y shl 22) or (v shl 12) or flags(s)
    }

    private fun outKey(s: St): Long =
        ((s.y * 2f).roundToInt().toLong() shl 20) or (((s.vy / 2f).roundToInt() + 64).toLong() shl 12) or flags(s)

    /** [cells] is the new pattern, in the level's own distance; [end] where the cube is counted as through it */
    fun tryAdd(cells: List<Obstacle>, end: Float): Boolean {
        world.obstacles.clear()
        world.obstacles.addAll(cells)
        val orbs = cells.filter { it.kind == Kind.ORB }
        val out = HashMap<Long, St>()
        var frontier: List<St> = states
        var budget = 40_000
        while (frontier.isNotEmpty() && budget > 0) {
            val next = ArrayList<St>()
            val seen = HashSet<Long>()
            for (s in frontier) {
                if (s.x >= end) { out[outKey(s)] = s; continue }
                if (!seen.add(key(s))) continue
                budget--
                // in the air a tap only matters near an orb, or for a mode that answers to taps anywhere
                val orbNear = orbs.any { abs(it.x + 0.5f - s.x) < 2.5f }
                val options = if (s.grounded || orbNear || s.ship || s.wave || s.ufo) booleanArrayOf(true, false) else booleanArrayOf(false)
                for (press in options) {
                    world.x = s.x; world.y = s.y; world.vy = s.vy; world.grounded = s.grounded
                    world.alive = true; world.rotation = 0f; world.held = s.held; world.ship = s.ship
                    world.flipped = s.flipped; world.wave = s.wave; world.ball = s.ball; world.ufo = s.ufo
                    var ok = true
                    for (i in 0 until 4) {
                        if (world.step(press, prune = false) == StepResult.DIED) { ok = false; break }
                    }
                    if (ok) next.add(St(world.x, world.y, world.vy, world.grounded, world.held, world.ship, world.flipped, world.wave, world.ball, world.ufo))
                }
            }
            frontier = next
        }
        if (out.isEmpty()) return false
        states = out.values.take(300)
        return true
    }
}

/**
 * Builds the level out of the song as it plays and keeps it, so that the next attempt meets the same
 * obstacles: patterns are planned a few seconds ahead on the beat grid, then handed to the world as
 * they come within sight. A calm song gets a pattern every other beat and the easy shapes, a hard one
 * (phonk, montagem, fast songs) nearly every beat and all of them.
 */
internal class GeometryDirector(private val songKey: String) {
    val specs = ArrayList<Spec>()
    val grid = BeatGrid()
    private val playable = Playability()
    private var spawned = 0
    private var plannedUntilMs = 0L
    private var beatCounter = 0
    private var noGridSinceMs = -1L
    // the level's own distance, in blocks, of the beat being planned, and how far the last pattern reaches
    private var beatT = Long.MIN_VALUE
    private var beatX = 0f
    private var boundary = -1e9f
    // the ship sections: whether the level is in one, how many gates it still wants, and cube patterns since the last one
    private var inShip = false
    private var shipLeft = 0
    private var cubeSince = 0
    private var inFlip = false
    private var inBall = false
    private var flipLeft = 0

    /** a new attempt: the level kept, the world's start over */
    fun restartRun() { spawned = 0 }

    fun onKick(songMs: Long) = grid.onKick(songMs)

    /**
     * Plans beats up to [HorizonMs] ahead of [songMs]. [calm] says the song is in a breakdown, where
     * nothing is placed; [hard] picks the shapes and how often they come. A pattern is only placed if
     * the cube can still get through it from wherever the earlier ones leave it.
     */
    fun plan(songMs: Long, durationMs: Long, hard: Boolean, calm: Boolean, timeScale: Float) {
        if (songMs < IntroMs && specs.isEmpty()) plannedUntilMs = max(plannedUntilMs, IntroMs - 1)
        // without a heard beat for a while the level falls back to a steady one so the game still starts
        if (!grid.confident) {
            if (noGridSinceMs < 0) noGridSinceMs = songMs
            if (songMs - noGridSinceMs < FallbackAfterMs) return
        } else noGridSinceMs = -1
        val horizon = songMs + HorizonMs
        val end = if (durationMs > 0) durationMs - OutroMs else Long.MAX_VALUE
        var t = grid.nextBeatAfter(max(plannedUntilMs, IntroMs - 1), FallbackPeriodMs)
        var guard = 0
        // a beat or two a call: checking a pattern takes a little while, so the level fills in over a few frames
        while (t < horizon && t < end && guard++ < 2) {
            plannedUntilMs = t
            val period = if (grid.periodMs > 0f) grid.periodMs else FallbackPeriodMs
            if (beatT == Long.MIN_VALUE) beatX = 0f else beatX += (t - beatT) / period * BeatBlocks
            beatT = t
            val counter = beatCounter++
            val density = if (hard) 0.5f + 0.35f * min(1f, (t - IntroMs) / 40_000f) else 0.9f
            val everyOther = !hard && counter % 2 != 0
            val random = Random(songKey.hashCode() * 31 + counter * 7919)
            if (!calm && !everyOther && random.nextFloat() < density) {
                val anchor = beatX + Gd.TakeoffBlocks
                var forced: Pattern? = null
                var pool: List<Pattern> = emptyList()
                var mirrored = false
                if (inShip) {
                    if (shipLeft <= 0) forced = Pattern.PORTAL_OUT
                    else pool = if (inBall) Pattern.entries.filter { it.section == 1 && (hard || !it.hard) }
                        else Pattern.entries.filter { it.ship && !it.isPortal && (hard || !it.hard) }
                } else if (inFlip) {
                    if (flipLeft <= 0) forced = Pattern.PORTAL_UNFLIP
                    else { pool = Pattern.entries.filter { !it.ship && !it.isPortal && it.section == 0 && it.flippable && (hard || !it.hard) }; mirrored = true }
                } else if (cubeSince >= (if (hard) 6 else 9) && t < end - 14_000L && random.nextFloat() < 0.35f) {
                    forced = when (random.nextInt(5)) {
                        0 -> Pattern.PORTAL_IN
                        1 -> Pattern.PORTAL_WAVE
                        2 -> Pattern.PORTAL_UFO
                        3 -> Pattern.PORTAL_BALL
                        else -> Pattern.PORTAL_FLIP
                    }
                } else {
                    pool = Pattern.entries.filter { !it.ship && !it.isPortal && it.section == 0 && (hard || !it.hard) }
                }
                for (attempt in 0 until (if (forced != null) 1 else 5)) {
                    val candidate = forced ?: pool[random.nextInt(pool.size)]
                    // the last pattern's pieces still reach this far
                    if (anchor < boundary) continue
                    val cells = candidate.cells.map {
                        val m = if (mirrored) mirror(it) else it
                        Obstacle(m.kind, anchor + m.dx, m.y, m.w, m.h)
                    }
                    val reachEnd = anchor + candidate.reach + 0.7f
                    if (playable.tryAdd(cells, reachEnd)) {
                        val takeoffMs = (Gd.TakeoffBlocks / (Gd.Speed * timeScale.coerceAtLeast(0.3f)) * 1000f).toLong()
                        specs.add(Spec(t + takeoffMs, candidate, mirrored))
                        boundary = reachEnd
                        when {
                            candidate == Pattern.PORTAL_IN || candidate == Pattern.PORTAL_WAVE || candidate == Pattern.PORTAL_UFO || candidate == Pattern.PORTAL_BALL -> {
                                inBall = candidate == Pattern.PORTAL_BALL
                                inShip = true; shipLeft = (if (hard) 5 else 3) + random.nextInt(3); cubeSince = 0 }
                            candidate == Pattern.PORTAL_OUT -> { inShip = false; cubeSince = 0 }
                            candidate == Pattern.PORTAL_FLIP -> { inFlip = true; flipLeft = (if (hard) 5 else 3) + random.nextInt(3); cubeSince = 0 }
                            candidate == Pattern.PORTAL_UNFLIP -> { inFlip = false; cubeSince = 0 }
                            candidate.ship -> shipLeft--
                            mirrored -> flipLeft--
                            else -> cubeSince++
                        }
                        break
                    }
                }
            }
            t = grid.nextBeatAfter(t, FallbackPeriodMs)
        }
    }

    /** the pattern turned upside down: spikes hang from the roof and blocks stand on it */
    private fun mirror(c: Cell): Cell = Cell(
        when (c.kind) { Kind.SPIKE -> Kind.SPIKE_DOWN; Kind.SPIKE_DOWN -> Kind.SPIKE; else -> c.kind },
        c.dx, Gd.Ceiling - c.y - c.h, c.w, c.h,
    )

    /** puts every planned pattern that has come within [leadMs] of arriving into [world] */
    fun spawnDue(world: GeometryWorld, songMs: Long, leadMs: Long, timeScale: Float) {
        while (spawned < specs.size) {
            val spec = specs[spawned]
            val ahead = spec.arrivalMs - songMs
            if (ahead > leadMs) break
            spawned++
            // one that is already on top of the cube would be unfair; skip it
            if (ahead < 600) continue
            val anchor = world.x + Gd.Speed * timeScale * ahead / 1000f
            for (c in spec.pattern.cells) {
                val m = if (spec.mirrored) mirror(c) else c
                world.obstacles.add(Obstacle(m.kind, anchor + m.dx, m.y, m.w, m.h))
            }
        }
    }

    companion object {
        const val IntroMs = 5000L
        const val OutroMs = 4000L
        const val HorizonMs = 3200L
        const val FallbackAfterMs = 4000L
        const val FallbackPeriodMs = 545f
        /** blocks between two beats: the game's speed follows the song's tempo, so this never changes */
        const val BeatBlocks = Gd.Speed * 60f / 140f
    }
}

/**
 * How fast the game runs against the song. Everything in it (speed, gravity, the jump) is scaled by
 * this, so a slow song plays out in slow motion with the same shapes and a fast one runs quicker; it
 * follows the song's tempo and eases further when the song drops into a breakdown.
 */
internal class TimeScale {
    var value = 0.8f; private set
    private var energyFast = 0f
    private var energySlow = 0f
    var calm = false; private set

    fun update(dt: Float, bpm: Float, energy: Float) {
        energyFast += (energy - energyFast) * (1f - exp(-dt / 0.5f))
        energySlow += (energy - energySlow) * (1f - exp(-dt / 8f))
        calm = energySlow > 0.05f && energyFast < energySlow * 0.55f
        val tempo = if (bpm > 0f) (bpm / 140f).coerceIn(0.55f, 1.45f) else 0.8f
        val target = if (calm) tempo * 0.7f else tempo
        value += (target - value) * (1f - exp(-dt / 0.8f))
    }

    fun reset() { value = 0.8f; energyFast = 0f; energySlow = 0f; calm = false }
}

/** the flame streak: a hard song counts at once, a calm one has to be won twice */
internal object GeometryRewards {
    class Result(val streak: Int, val easyWins: Int, val gained: Boolean)

    fun win(hard: Boolean, streak: Int, easyWins: Int): Result = when {
        hard -> Result(streak + 1, easyWins, true)
        easyWins + 1 >= 2 -> Result(streak + 1, 0, true)
        else -> Result(streak, easyWins + 1, false)
    }

    private val hardWords = listOf("phonk", "montagem", "funk", "drift", "brazil", "slap house", "tecno", "mtg", "automotivo")

    /** hard when the song is fast or the way it is tagged says so */
    fun isHard(bpm: Float, title: String, artist: String, album: String): Boolean {
        if (bpm >= 130f) return true
        val text = "$title $artist $album".lowercase()
        return hardWords.any { it in text }
    }
}

/** the best percentage reached on each song, kept as text in one preference: one "id|percent" per line */
internal object GeometryBest {
    private const val MaxEntries = 150

    fun parse(raw: String): LinkedHashMap<String, Int> {
        val map = LinkedHashMap<String, Int>()
        for (line in raw.split('\n')) {
            val cut = line.lastIndexOf('|')
            if (cut <= 0) continue
            val value = line.substring(cut + 1).toIntOrNull() ?: continue
            map[line.substring(0, cut)] = value
        }
        return map
    }

    fun get(raw: String, id: String): Int = parse(raw)[id] ?: 0

    /** [raw] with the best of [id] raised to [percent]; a lower number changes nothing, the oldest songs are forgotten past 150 */
    fun put(raw: String, id: String, percent: Int): String {
        val map = parse(raw)
        if (percent <= (map[id] ?: 0)) return raw
        map.remove(id)
        map[id] = percent
        while (map.size > MaxEntries) map.remove(map.keys.first())
        return map.entries.joinToString("\n") { "${it.key}|${it.value}" }
    }
}
