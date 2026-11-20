package com.vantra.music.ui.player.geometry

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.vantra.music.LocalPlayerConnection
import com.vantra.music.R
import com.vantra.music.constants.GeometryBestKey
import com.vantra.music.constants.GeometryEasyWinsKey
import com.vantra.music.constants.GeometryStreakKey
import com.vantra.music.constants.TerminalOrientation
import com.vantra.music.constants.TerminalOrientationKey
import com.vantra.music.lyrics.LyricsEntry
import com.vantra.music.ui.player.styles.LocalStyleInteractive
import com.vantra.music.ui.player.styles.StyleActions
import com.vantra.music.ui.player.styles.StyleInsets
import com.vantra.music.ui.player.styles.StyleMenu
import com.vantra.music.ui.player.styles.StyleTones
import com.vantra.music.ui.player.styles.TurnedFlingFix
import com.vantra.music.ui.player.styles.quarterTurn
import com.vantra.music.ui.player.styles.rememberDurationMs
import com.vantra.music.ui.player.styles.rememberStyleLyrics
import com.vantra.music.ui.player.styles.rememberStyleShowing
import com.vantra.music.ui.player.styles.rememberStyleTrack
import com.vantra.music.ui.player.styles.turnFit
import com.vantra.music.ui.player.terminal.BeatSource
import com.vantra.music.ui.player.terminal.ImmersiveWhileLandscape
import com.vantra.music.ui.player.terminal.TerminalPlayClock
import com.vantra.music.ui.player.terminal.TerminalSynchronization
import com.vantra.music.ui.player.terminal.terminalInsets
import com.vantra.music.ui.utils.AppMotion
import com.vantra.music.utils.rememberEnumPreference
import com.vantra.music.utils.rememberPreference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** a bit of the burst when the cube breaks, or of the dust it leaves behind, in blocks */
private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val size: Float, val hot: Boolean)

/** everything the running game keeps between frames; plain fields, the canvas is redrawn by a frame counter */
private class GeometryRuntime {
    val world = GeometryWorld()
    val sync = TerminalSynchronization()
    val clock = TerminalPlayClock()
    val scale = TimeScale()
    var director = GeometryDirector("")
    var mediaId: String? = null
    var pressed = false
    /** a tap shorter than a frame still jumps: set when a finger goes down, cleared after the frame that saw it */
    var tapLatch = false
    var accumulator = 0f
    var songMs = 0L
    var timeS = 0f
    var attempt = 1
    var bestPercent = 0
    var deadAtS = -10f
    var kickPulse = 0f
    var bass = 0f
    var shake = 0f
    var trailClock = 0f
    var won = false
    var winAtS = -10f
    var discontinuity = 0L
    var hit = 0f
    /** 0 as a cube, 1 as a ship: the roof slides in and out with it */
    var shipFade = 0f
    /** where the wave has just been, a ring of x and y pairs */
    val waveTrail = FloatArray(96)
    var waveHead = 0
    var waveCount = 0
    var hard = false
    var hardEvery = 0
    val particles = ArrayList<Particle>()
    var lyricIndex = -1
    var lyricLayout: TextLayoutResult? = null
    var lyricLayoutKey = ""

    fun reset(newDirector: GeometryDirector? = null) {
        world.reset()
        accumulator = 0f
        deadAtS = -10f
        particles.clear()
        won = false
        if (newDirector != null) director = newDirector else director.restartRun()
    }

    fun burst(count: Int, hot: Boolean) {
        for (i in 0 until count) {
            val a = (i * 2.399f) % (2f * PI.toFloat())
            val v = 4f + (i % 7) * 1.7f
            particles.add(Particle(world.x, world.y + 0.5f, cos(a) * v + Gd.Speed * 0.3f, sin(a) * v + 3f, 0.9f, 0.22f + (i % 3) * 0.1f, hot))
        }
    }
}

private const val OverlayMs = 4000L

/**
 * The Geometry player style: a rhythm platformer built out of the song that is playing. A cube runs
 * through a level whose obstacles land on the song's beats; a finger held down jumps. Calm songs
 * play in slow motion, fast ones run quicker, and the lyrics are written on the wall behind. Winning
 * a song lights the flame streak (at once for hard songs, every second win for calm ones).
 */
@Composable
fun GeometryPlayerStyle(actions: StyleActions, modifier: Modifier = Modifier, preview: Boolean = false) {
    val track = rememberStyleTrack()
    val connection = LocalPlayerConnection.current
    val lyrics = rememberStyleLyrics(readings = !preview)
    val duration = rememberDurationMs()
    var orientation by rememberEnumPreference(TerminalOrientationKey, TerminalOrientation.AUTO)
    val config = LocalConfiguration.current
    val wide = config.screenWidthDp > config.screenHeightDp
    // the game wants a wide picture: turned sideways unless the screen already is, or the phone is held upright on purpose
    val rotated = orientation != TerminalOrientation.PORTRAIT && !wide
    val (streak, setStreak) = rememberPreference(GeometryStreakKey, defaultValue = 0)
    val (easyWins, setEasyWins) = rememberPreference(GeometryEasyWinsKey, defaultValue = 0)
    val streakNow by rememberUpdatedState(streak)
    val easyNow by rememberUpdatedState(easyWins)
    val setStreakNow by rememberUpdatedState(setStreak)
    val setEasyNow by rememberUpdatedState(setEasyWins)
    val (bestRaw, setBestRaw) = rememberPreference(GeometryBestKey, defaultValue = "")
    val bestRawNow by rememberUpdatedState(bestRaw)
    val setBestNow by rememberUpdatedState(setBestRaw)
    val durationNow by rememberUpdatedState(duration)
    val linesNow by rememberUpdatedState(lyrics.lines)
    val lyricsIdNow by rememberUpdatedState(lyrics.mediaId)
    val titleNow by rememberUpdatedState(track)

    val game = remember { GeometryRuntime() }
    val frame = remember { mutableLongStateOf(0L) }
    var overlay by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val showing = rememberStyleShowing()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activePlayer = connection?.service?.playerFlow?.collectAsState()?.value ?: connection?.player
    val playing = connection?.isPlaying?.collectAsState()?.value == true
    val angle = animateFloatAsState(if (rotated) 90f else 0f, tween(360), label = "geometryTurn")
    TurnedFlingFix(active = rotated && !preview)
    val textMeasurer = rememberTextMeasurer()

    // a selector card: the game plays itself on a loop
    if (preview) {
        LaunchedEffect(Unit) {
            val s = game
            s.scale.reset()
            var last = 0L
            var next = 6f
            while (true) {
                val now = androidx.compose.runtime.withFrameNanos { it }
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                last = now
                s.timeS += dt
                s.kickPulse = max(0f, s.kickPulse - dt * 3f)
                if (s.world.x >= next - 6f) {
                    val patterns = listOf(Pattern.SPIKE, Pattern.DOUBLE, Pattern.BLOCK, Pattern.SPIKE_ON_BLOCK, Pattern.STAIR)
                    val p = patterns[((s.world.x / 4.45f).toInt()) % patterns.size]
                    for (c in p.cells) s.world.obstacles.add(Obstacle(c.kind, next + c.dx, c.y, c.w, c.h))
                    next += 4.45f * 2f
                }
                s.accumulator += dt
                while (s.accumulator >= Gd.Step) {
                    s.accumulator -= Gd.Step
                    // the bot jumps when something is a takeoff away
                    val press = s.world.obstacles.any { it.x - s.world.x in 0.2f..Gd.TakeoffBlocks && (it.spike || it.h <= 2f) } &&
                        s.world.grounded
                    s.world.step(press)
                }
                if (!s.world.alive) { s.world.reset(); next = 6f }
                frame.longValue = now
            }
        }
    } else {
        LaunchedEffect(connection, activePlayer, lifecycle, showing) {
            val conn = connection ?: return@LaunchedEffect
            val player = activePlayer ?: return@LaunchedEffect
            val s = game
            val listener = object : androidx.media3.common.Player.Listener {
                override fun onPositionDiscontinuity(oldPosition: androidx.media3.common.Player.PositionInfo,
                    newPosition: androidx.media3.common.Player.PositionInfo, reason: Int) { s.discontinuity++ }
                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) { s.discontinuity++ }
            }
            player.addListener(listener)
            try {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    try {
                        snapshotFlow { showing() }.collectLatest { shown ->
                            s.sync.suspendAnalysis()
                            if (!shown) return@collectLatest
                            var last = 0L
                            while (true) {
                                val cast = conn.service.castConnectionHandler
                                val casting = cast?.isCasting?.value == true
                                val isPlaying = if (casting) cast?.castIsPlaying?.value == true else player.isPlaying
                                val now = if (!isPlaying && s.particles.isEmpty() && s.hit < 0.01f) {
                                    delay(120); System.nanoTime()
                                } else androidx.compose.runtime.withFrameNanos { it }
                                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceIn(0f, 0.5f)
                                last = now
                                val stepDt = dt.coerceAtMost(0.1f)
                                val posMs = if (casting) cast?.castPosition?.value ?: 0L else player.currentPosition
                                val mediaId = if (casting) conn.mediaMetadata.value?.id else player.currentMediaItem?.mediaId
                                val speed = if (casting) 1f else player.playbackParameters.speed
                                s.sync.beginFrame(dt, posMs, mediaId, if (casting) cast else player, s.discontinuity, isPlaying, speed)

                                // a different song is a different level
                                if (mediaId != s.mediaId) {
                                    s.mediaId = mediaId
                                    s.attempt = 1
                                    s.bestPercent = GeometryBest.get(bestRawNow, mediaId ?: "")
                                    s.scale.reset()
                                    s.reset(GeometryDirector(mediaId ?: ""))
                                    overlay = null
                                }

                                // the beat: the local player's audio, as the other beat styles read it
                                val spectrum = if (isPlaying && !casting && AppMotion.durationScale.scaleFactor != 0f)
                                    conn.service.spectrumOf(player) else null
                                val heard = spectrum != null && spectrum.read(posMs, s.sync.tracker.bands)
                                s.sync.react(stepDt, isPlaying, true, false, heard)
                                val tracker = s.sync.tracker
                                val audio = s.sync.source == BeatSource.AUDIO
                                val songMs = s.clock.update(stepDt, posMs, isPlaying, speed, s.sync.timelineJump)
                                s.songMs = songMs
                                s.timeS += stepDt
                                val kick = if (audio) tracker.kick else 0f
                                if (kick > 0f) {
                                    s.director.onKick(songMs)
                                    s.kickPulse = max(s.kickPulse, kick)
                                    s.shake = max(s.shake, kick * 0.7f)
                                }
                                s.kickPulse = max(0f, s.kickPulse - stepDt * 2.6f)
                                s.shake = max(0f, s.shake - stepDt * 5f)
                                s.hit = max(0f, s.hit - stepDt * 2.2f)
                                s.bass += ((if (audio) tracker.bass else 0f) - s.bass) * min(1f, stepDt * 8f)
                                if (isPlaying) s.scale.update(stepDt, s.director.grid.bpm, if (audio) tracker.energy else 0.3f)

                                val world = s.world
                                val ts = s.scale.value
                                if (s.hardEvery++ % 30 == 0) s.hard = GeometryRewards.isHard(s.director.grid.bpm, titleNow.title, titleNow.artist, titleNow.album)
                                val hard = s.hard
                                if (world.alive && isPlaying && !s.won) {
                                    s.director.plan(songMs, durationNow, hard, s.scale.calm, ts)
                                    s.director.spawnDue(world, songMs, 2600L, ts)
                                }

                                // the cube: fixed steps of game time, slowed or quickened by the song
                                if (isPlaying && world.alive) {
                                    s.accumulator += stepDt * ts
                                    var guard = 0
                                    while (s.accumulator >= Gd.Step && world.alive && guard++ < 64) {
                                        s.accumulator -= Gd.Step
                                        when (world.step(s.pressed || s.tapLatch)) {
                                            StepResult.DIED -> {
                                                s.deadAtS = s.timeS
                                                s.hit = 1f
                                                s.shake = 1f
                                                s.burst(26, true)
                                                val percent = if (durationNow > 0) (songMs * 100 / durationNow).toInt() else 0
                                                if (percent > s.bestPercent) {
                                                    s.bestPercent = percent
                                                    s.mediaId?.let { setBestNow(GeometryBest.put(bestRawNow, it, percent)) }
                                                }
                                            }
                                            else -> Unit
                                        }
                                    }
                                    // dust behind the cube while it runs along the ground
                                    s.trailClock += stepDt
                                    if (world.grounded && s.trailClock > 0.05f && world.alive) {
                                        s.trailClock = 0f
                                        s.particles.add(Particle(world.x - 0.5f, if (world.flipped) Gd.Ceiling - 0.05f else 0.05f, -2f - ts * 2f, if (world.flipped) -1.5f else 1.5f, 0.45f, 0.14f, false))
                                    } else if (world.ship && s.trailClock > 0.03f && world.alive) {
                                        // the ship's exhaust, brighter while a finger pushes it up
                                        s.trailClock = 0f
                                        s.particles.add(Particle(world.x - 0.7f, world.y + 0.35f, -5f - ts * 2f, if (s.pressed) -1.5f else 0.5f, 0.35f, 0.16f, true))
                                    }
                                }

                                // after a moment the cube starts over from the beginning of the song
                                if (!world.alive && s.timeS - s.deadAtS > 0.75f) {
                                    s.attempt++
                                    s.reset()
                                    s.scale.reset()
                                    player.seekTo(0)
                                    if (!player.isPlaying) player.play()
                                }

                                // the end of the song, still alive: a win
                                if (world.alive && !s.won && durationNow > 0 && songMs >= durationNow - 700) {
                                    s.won = true
                                    s.winAtS = s.timeS
                                    val result = GeometryRewards.win(hard, streakNow, easyNow)
                                    setStreakNow(result.streak)
                                    setEasyNow(result.easyWins)
                                    s.burst(40, false)
                                    s.bestPercent = 100
                                    s.mediaId?.let { setBestNow(GeometryBest.put(bestRawNow, it, 100)) }
                                    overlay = if (result.gained) "LEVEL COMPLETE\n🔥 ${result.streak}"
                                    else "LEVEL COMPLETE\nwin once more for 🔥"
                                }
                                if (s.won && s.timeS - s.winAtS > OverlayMs / 1000f) overlay = null

                                if (world.wave && world.alive && isPlaying) {
                                    s.waveTrail[s.waveHead * 2] = world.x
                                    s.waveTrail[s.waveHead * 2 + 1] = world.y + 0.5f
                                    s.waveHead = (s.waveHead + 1) % 48
                                    s.waveCount = min(48, s.waveCount + 1)
                                } else if (!world.wave) s.waveCount = 0
                                s.shipFade += ((if (world.ship || world.wave || world.flipped || world.ball || world.ufo) 1f else 0f) - s.shipFade) * min(1f, stepDt * 6f)
                                s.tapLatch = false
                                // dust and pieces fly
                                var i = 0
                                while (i < s.particles.size) {
                                    val p = s.particles[i]
                                    p.life -= stepDt
                                    p.x += p.vx * stepDt
                                    p.y += p.vy * stepDt
                                    p.vy -= 30f * stepDt
                                    if (p.y < 0f) { p.y = 0f; p.vy = -p.vy * 0.4f }
                                    if (p.life <= 0f) s.particles.removeAt(i) else i++
                                }
                                // the wall's line: the last lyric that has begun
                                val lines = if (lyricsIdNow != null) linesNow else emptyList()
                                s.lyricIndex = lineAt(lines, songMs + conn.lyricsOffsetMs.value.toLong())
                                frame.longValue = now
                            }
                        }
                    } finally {
                        s.sync.suspendAnalysis()
                    }
                }
            } finally {
                player.removeListener(listener)
                s.sync.suspendAnalysis()
            }
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().quarterTurn(rotated).graphicsLayer {
            val rest = if (rotated) 90f else 0f
            rotationZ = angle.value - rest
            val fit = turnFit(size.width, size.height, rest, rotationZ)
            scaleX = fit; scaleY = fit
        }) {
            val landscape = rotated
            val insets = com.vantra.music.ui.player.terminal.terminalInsets(landscape)
            ImmersiveWhileLandscape(active = landscape && !preview)
            BackHandler(enabled = menuOpen) { menuOpen = false }
            Canvas(
                Modifier.fillMaxSize().pointerInput(preview) {
                    if (preview) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            // a finger down is a held jump; the whole screen is the button
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val down = event.changes.any { it.pressed }
                            if (down && !game.pressed) game.tapLatch = true
                            game.pressed = down
                            event.changes.forEach { it.consume() }
                        }
                    }
                },
            ) {
                frame.longValue
                drawGame(game, durationNow, textMeasurer, linesNow, preview)
            }
            if (!preview) {
                // the flame streak, and how far the calm songs have got towards the next flame
                Row(
                    Modifier.align(Alignment.TopStart).padding(start = insets.start + 16.dp, top = if (landscape) 12.dp else insets.top + 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("🔥 $streak", color = Color.White.copy(alpha = 0.92f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (easyWins > 0) Text("½", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                }
                overlay?.let {
                    Text(it, Modifier.align(Alignment.Center), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center)
                }
                Row(
                    Modifier.align(Alignment.TopEnd).padding(end = insets.end + 12.dp, top = if (landscape) 8.dp else insets.top + 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { connection?.togglePlayPause() }, modifier = Modifier.size(44.dp)) {
                        Icon(painterResource(if (playing) R.drawable.pause else R.drawable.play), if (playing) "Pause" else "Play",
                            tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { menuOpen = !menuOpen }, modifier = Modifier.size(44.dp)) {
                        Icon(painterResource(R.drawable.more_horiz), if (menuOpen) "Close menu" else "Open menu",
                            tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
                    }
                }
                val tones = remember {
                    StyleTones(played = Color.Black, remaining = Color.Black, ink = Color.White,
                        inkSoft = Color.White.copy(alpha = 0.62f), chip = Color.White.copy(alpha = 0.12f))
                }
                StyleMenu(
                    open = menuOpen,
                    onOpenChange = { menuOpen = it },
                    tones = tones,
                    actions = actions,
                    rotated = landscape,
                    onRotate = {
                        orientation = if (orientation == TerminalOrientation.PORTRAIT) TerminalOrientation.LANDSCAPE else TerminalOrientation.PORTRAIT
                    },
                    atBottom = false,
                    end = insets.end + 12.dp + 4.dp,
                    edge = (if (landscape) 8.dp else insets.top + 8.dp) + 4.dp,
                    buttonSize = 40.dp,
                    showButton = false,
                    pill = true,
                )
            }
        }
    }
}

/** the index of the last lyric line that has begun at [ms], or -1 */
private fun lineAt(lines: List<LyricsEntry>, ms: Long): Int {
    if (lines.isEmpty()) return -1
    var lo = 0
    var hi = lines.size - 1
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lines[mid].time <= ms) { found = mid; lo = mid + 1 } else hi = mid - 1
    }
    return found
}

private fun hsv(h: Float, s: Float, v: Float): Color = Color.hsv(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))

private val spikePath = Path()
private val shipPath = Path()

/**
 * One frame: the wall with the lyric and drifting shapes, the ground, the obstacles, the cube,
 * the dust and the progress bar. The wall changes colour with the song and flashes on the beat;
 * the picture shakes a little on a kick and breaks up when the cube does.
 */
private fun DrawScope.drawGame(g: GeometryRuntime, durationMs: Long, textMeasurer: TextMeasurer, lines: List<LyricsEntry>, preview: Boolean) {
    val w = size.width
    val h = size.height
    val unit = h / 10f
    val groundY = h - unit * 2.2f
    val playerX = w * 0.27f
    val world = g.world
    val ts = g.scale.value
    val pulse = g.kickPulse.coerceIn(0f, 1f)
    val hue = 205f + 55f * sin(g.timeS * 0.07f) + (if (g.won) 80f else 0f) + (if (!world.alive) -150f else 0f)
    val slow = (1f - ts).coerceIn(0f, 0.5f)
    fun sx(wx: Float) = playerX + (wx - world.x) * unit
    fun sy(wy: Float) = groundY - wy * unit

    translate(left = (sin(g.timeS * 91f)) * g.shake * unit * 0.12f, top = (cos(g.timeS * 77f)) * g.shake * unit * 0.12f) {
        // the wall
        drawRect(Brush.verticalGradient(listOf(hsv(hue, 0.7f, 0.30f + 0.12f * pulse), hsv(hue + 35f, 0.8f, 0.62f + 0.14f * pulse)),
            startY = 0f, endY = groundY), size = Size(w, groundY))
        // big shapes drifting slowly behind, the way the genre's walls scroll
        for (i in 0 until 7) {
            val span = unit * 14f
            val x = ((i * unit * 5.3f - world.x * unit * 0.22f) % span + span) % span - unit * 3f
            val side = unit * (2.2f + (i % 3) * 1.3f)
            drawRect(Color.White.copy(alpha = 0.035f + 0.04f * pulse), Offset(x, groundY - side - (i % 4) * unit * 1.6f), Size(side, side), style = Stroke(unit * 0.07f))
        }
        // the lyric, written on the wall
        val idx = g.lyricIndex
        if (idx in lines.indices) {
            val text = lines[idx].text
            val key = "$idx|${w.toInt()}"
            if (g.lyricLayoutKey != key || g.lyricLayout == null) {
                g.lyricLayoutKey = key
                g.lyricLayout = textMeasurer.measure(
                    text, TextStyle(fontSize = (unit * 1.05f).toSp(), fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = (unit * 1.15f).toSp()),
                    overflow = TextOverflow.Ellipsis, maxLines = 3, constraints = Constraints(maxWidth = (w * 0.84f).toInt()),
                )
            }
            g.lyricLayout?.let { layout ->
                val tx = (w - layout.size.width) / 2f - (world.x * unit * 0.04f) % (unit * 0.4f)
                val ty = (groundY - layout.size.height) / 2f - unit * 0.3f
                drawText(layout, color = Color.White.copy(alpha = 0.13f + 0.20f * pulse), topLeft = Offset(tx, ty))
            }
        }
        // slow motion: a soft vignette and a violet wash
        if (slow > 0.05f) drawRect(Color(0xFF3A1B6E).copy(alpha = slow * 0.5f), size = Size(w, h))

        // the ground
        drawRect(hsv(hue + 10f, 0.8f, 0.16f), Offset(0f, groundY), Size(w, h - groundY))
        val tile = unit * 2f
        val off = (world.x * unit) % tile
        var gx = -off
        while (gx < w) {
            drawLine(Color.White.copy(alpha = 0.07f), Offset(gx, groundY), Offset(gx, h), unit * 0.04f)
            gx += tile
        }
        drawRect(Color.White.copy(alpha = 0.85f), Offset(0f, groundY - unit * 0.05f), Size(w, unit * 0.1f + unit * 0.12f * pulse))
        // the ship flies between a floor and a roof; the roof slides down from the top as it turns into one
        if (g.shipFade > 0.01f) {
            val roof = sy(Gd.Ceiling)
            val slide = (1f - g.shipFade) * (roof + unit * 2f)
            drawRect(hsv(hue + 10f, 0.8f, 0.16f), Offset(0f, -unit * 3f - slide), Size(w, roof + unit * 3f))
            drawRect(Color.White.copy(alpha = 0.85f), Offset(0f, roof - slide - unit * 0.05f), Size(w, unit * 0.1f + unit * 0.12f * pulse))
        }

        // "Attempt N" slides by at the start of a run
        if (!preview && world.x < 14f) {
            val layout = textMeasurer.measure("Attempt ${g.attempt}", TextStyle(fontSize = (unit * 0.9f).toSp(), fontWeight = FontWeight.Black))
            drawText(layout, color = Color.White, topLeft = Offset(sx(2.4f), groundY - unit * 5.2f))
        }

        // the obstacles
        for (o in world.obstacles) {
            val x0 = sx(o.x)
            if (x0 > w + unit || x0 + o.w * unit < -unit) continue
            when (o.kind) {
                Kind.SPIKE -> {
                    val y1 = sy(o.y)
                    spikePath.reset()
                    spikePath.moveTo(x0, y1)
                    spikePath.lineTo(x0 + unit, y1)
                    spikePath.lineTo(x0 + unit / 2f, y1 - unit)
                    spikePath.close()
                    drawPath(spikePath, Color.Black)
                    drawPath(spikePath, Color.White.copy(alpha = 0.9f), style = Stroke(unit * 0.07f))
                }
                Kind.BLOCK -> {
                    val top = sy(o.y + o.h)
                    drawRect(hsv(hue + 20f, 0.7f, 0.12f), Offset(x0, top), Size(o.w * unit, o.h * unit))
                    drawRect(Color.White.copy(alpha = 0.9f), Offset(x0, top), Size(o.w * unit, o.h * unit), style = Stroke(unit * 0.07f))
                    // the inner panels, a square per block
                    var bx = 0
                    while (bx < o.w.toInt()) {
                        var by = 0
                        while (by < o.h.toInt()) {
                            drawRect(Color.White.copy(alpha = 0.22f), Offset(x0 + (bx + 0.18f) * unit, top + (by + 0.18f) * unit), Size(unit * 0.64f, unit * 0.64f), style = Stroke(unit * 0.04f))
                            by++
                        }
                        bx++
                    }
                }
                Kind.SPIKE_DOWN -> {
                    val y2 = sy(o.y + 1f)
                    spikePath.reset()
                    spikePath.moveTo(x0, y2)
                    spikePath.lineTo(x0 + unit, y2)
                    spikePath.lineTo(x0 + unit / 2f, y2 + unit)
                    spikePath.close()
                    drawPath(spikePath, Color.Black)
                    drawPath(spikePath, Color.White.copy(alpha = 0.9f), style = Stroke(unit * 0.07f))
                }
                Kind.PORTAL_SHIP, Kind.PORTAL_CUBE, Kind.PORTAL_FLIP, Kind.PORTAL_UNFLIP, Kind.PORTAL_WAVE, Kind.PORTAL_BALL, Kind.PORTAL_UFO -> {
                    // a tall beam across the whole level: pink turns the cube into a ship, cyan gives the cube back,
                    // orange turns gravity upside down and green puts it right again
                    val colour = when (o.kind) {
                        Kind.PORTAL_SHIP -> Color(0xFFFF3DAA)
                        Kind.PORTAL_FLIP -> Color(0xFFFFB02E)
                        Kind.PORTAL_UNFLIP -> Color(0xFF45F08C)
                        Kind.PORTAL_WAVE -> Color(0xFFB36BFF)
                        Kind.PORTAL_BALL -> Color(0xFFFF5252)
                        Kind.PORTAL_UFO -> Color(0xFFFFE08A)
                        else -> Color(0xFF38E8FF)
                    }
                    val beamTop = sy(Gd.Ceiling)
                    val mid = x0 + unit / 2f
                    drawRect(colour.copy(alpha = 0.10f + 0.12f * pulse), Offset(mid - unit * 0.6f, beamTop), Size(unit * 1.2f, groundY - beamTop))
                    drawLine(colour, Offset(mid, beamTop), Offset(mid, groundY), unit * 0.12f)
                    drawLine(Color.White.copy(alpha = 0.8f), Offset(mid, beamTop), Offset(mid, groundY), unit * 0.04f)
                }
                Kind.PAD -> {
                    // a yellow slab on the ground with a glow that breathes with the beat
                    val top = sy(o.y + o.h)
                    drawRect(Color(0xFFFFE23A).copy(alpha = 0.18f + 0.25f * pulse), Offset(x0 - unit * 0.1f, top - unit * 0.55f), Size(o.w * unit + unit * 0.2f, unit * 0.55f + o.h * unit))
                    drawRect(Color(0xFFFFE23A), Offset(x0 + unit * 0.1f, top), Size(o.w * unit - unit * 0.2f, o.h * unit))
                    drawRect(Color.White.copy(alpha = 0.9f), Offset(x0 + unit * 0.1f, top), Size(o.w * unit - unit * 0.2f, o.h * unit), style = Stroke(unit * 0.04f))
                }
                Kind.ORB -> {
                    // a yellow ball with a ring that swells and fades, asking for a tap
                    val c = Offset(x0 + unit / 2f, sy(o.y + 0.5f))
                    val ring = (g.timeS * 1.6f) % 1f
                    drawCircle(Color(0xFFFFE23A).copy(alpha = 0.22f), unit * 0.62f, c)
                    drawCircle(Color(0xFFFFE23A), unit * 0.34f, c)
                    drawCircle(Color.White, unit * 0.34f, c, style = Stroke(unit * 0.06f))
                    drawCircle(Color.White.copy(alpha = 0.6f * (1f - ring)), unit * (0.4f + 0.45f * ring), c, style = Stroke(unit * 0.05f))
                }
            }
        }

        // dust and pieces
        for (p in g.particles) {
            val a = (p.life * 1.8f).coerceIn(0f, 1f)
            val s = p.size * unit
            drawRect((if (p.hot) Color(0xFF38E8FF) else Color.White).copy(alpha = a), Offset(sx(p.x) - s / 2f, sy(p.y) - s / 2f), Size(s, s))
        }

        // the cube
        if (world.alive && world.ball) {
            // the ball: a round body with spokes, rolling along whichever side it is on
            val cx = playerX
            val cy = sy(world.y + 0.5f)
            rotate(world.rotation, Offset(cx, cy)) {
                drawCircle(Color(0xFF30E6FF), unit * 0.5f, Offset(cx, cy))
                drawCircle(Color.White, unit * 0.5f, Offset(cx, cy), style = Stroke(unit * 0.07f))
                drawLine(Color.White.copy(alpha = 0.85f), Offset(cx - unit * 0.42f, cy), Offset(cx + unit * 0.42f, cy), unit * 0.06f)
                drawLine(Color.White.copy(alpha = 0.85f), Offset(cx, cy - unit * 0.42f), Offset(cx, cy + unit * 0.42f), unit * 0.06f)
                drawCircle(Color(0xFF0B3C5C), unit * 0.16f, Offset(cx, cy))
            }
        } else if (world.alive && world.ufo) {
            // the ufo: a saucer with a small dome on top that bobs a little as it is tapped
            val cx = playerX
            val cy = sy(world.y + 0.5f)
            val tilt = (-world.vy * 1.2f).coerceIn(-14f, 14f)
            rotate(tilt, Offset(cx, cy)) {
                drawArc(Color(0xFF30E6FF), 180f, 180f, true, Offset(cx - unit * 0.3f, cy - unit * 0.5f), Size(unit * 0.6f, unit * 0.6f))
                drawArc(Color.White, 180f, 180f, true, Offset(cx - unit * 0.3f, cy - unit * 0.5f), Size(unit * 0.6f, unit * 0.6f), style = Stroke(unit * 0.05f))
                drawOval(Color(0xFF30E6FF), Offset(cx - unit * 0.66f, cy - unit * 0.12f), Size(unit * 1.32f, unit * 0.5f))
                drawOval(Color.White, Offset(cx - unit * 0.66f, cy - unit * 0.12f), Size(unit * 1.32f, unit * 0.5f), style = Stroke(unit * 0.06f))
            }
        } else if (world.alive && world.wave) {
            // the wave: a bright trail of where it has been and a small arrow pointing along its way
            val cx = playerX
            val cy = sy(world.y + 0.5f)
            var prevX = 0f
            var prevY = 0f
            for (i in 0 until g.waveCount) {
                val idx = ((g.waveHead - g.waveCount + i) % 48 + 48) % 48
                val px = sx(g.waveTrail[idx * 2])
                val py = sy(g.waveTrail[idx * 2 + 1])
                if (i > 0) {
                    val fade = i / g.waveCount.toFloat()
                    drawLine(Color(0xFF30E6FF).copy(alpha = 0.15f + 0.6f * fade), Offset(prevX, prevY), Offset(px, py), unit * (0.08f + 0.22f * fade))
                }
                prevX = px; prevY = py
            }
            val lean = if (world.vy > 0.5f) -45f else if (world.vy < -0.5f) 45f else 0f
            rotate(lean, Offset(cx, cy)) {
                shipPath.reset()
                shipPath.moveTo(cx - unit * 0.32f, cy - unit * 0.26f)
                shipPath.lineTo(cx + unit * 0.42f, cy)
                shipPath.lineTo(cx - unit * 0.32f, cy + unit * 0.26f)
                shipPath.close()
                drawPath(shipPath, Color(0xFF30E6FF))
                drawPath(shipPath, Color.White, style = Stroke(unit * 0.06f))
            }
        } else if (world.alive && world.ship) {
            // the ship leans with the way it is going, a small cube riding on top
            val cx = playerX
            val cy = sy(world.y + 0.5f)
            val lean = (atan2(world.vy, Gd.Speed) * 180f / PI.toFloat()).coerceIn(-45f, 45f)
            rotate(-lean, Offset(cx, cy)) {
                shipPath.reset()
                shipPath.moveTo(cx - unit * 0.62f, cy - unit * 0.16f)
                shipPath.lineTo(cx + unit * 0.74f, cy + unit * 0.06f)
                shipPath.lineTo(cx - unit * 0.62f, cy + unit * 0.34f)
                shipPath.lineTo(cx - unit * 0.34f, cy + unit * 0.09f)
                shipPath.close()
                drawPath(shipPath, Color(0xFF30E6FF))
                drawPath(shipPath, Color.White, style = Stroke(unit * 0.07f))
                drawRect(Color(0xFF30E6FF), Offset(cx - unit * 0.22f, cy - unit * 0.52f), Size(unit * 0.5f, unit * 0.5f))
                drawRect(Color.White, Offset(cx - unit * 0.22f, cy - unit * 0.52f), Size(unit * 0.5f, unit * 0.5f), style = Stroke(unit * 0.05f))
                drawRect(Color(0xFF0B3C5C), Offset(cx - unit * 0.1f, cy - unit * 0.4f), Size(unit * 0.26f, unit * 0.26f))
            }
        } else if (world.alive) {
            val cx = playerX
            val cy = sy(world.y + 0.5f)
            rotate(world.rotation, Offset(cx, cy)) {
                val half = unit / 2f
                drawRect(Color(0xFF30E6FF), Offset(cx - half, cy - half), Size(unit, unit))
                drawRect(Color.White, Offset(cx - half, cy - half), Size(unit, unit), style = Stroke(unit * 0.08f))
                drawRect(Color(0xFF0B3C5C), Offset(cx - half * 0.58f, cy - half * 0.58f), Size(unit * 0.58f, unit * 0.58f))
                drawRect(Color.White.copy(alpha = 0.9f), Offset(cx - half * 0.34f, cy - half * 0.34f), Size(unit * 0.16f, unit * 0.16f))
                drawRect(Color.White.copy(alpha = 0.9f), Offset(cx + half * 0.04f, cy - half * 0.34f), Size(unit * 0.16f, unit * 0.16f))
            }
        }

        // progress: a bar across the top with the percentage
        if (!preview && durationMs > 0) {
            val fraction = (g.songMs.toFloat() / durationMs).coerceIn(0f, 1f)
            val bw = w * 0.34f
            val bx = (w - bw) / 2f
            drawRect(Color.Black.copy(alpha = 0.45f), Offset(bx, unit * 0.45f), Size(bw, unit * 0.26f))
            drawRect(Color(0xFF3CFF8F), Offset(bx, unit * 0.45f), Size(bw * fraction, unit * 0.26f))
            drawRect(Color.White.copy(alpha = 0.8f), Offset(bx, unit * 0.45f), Size(bw, unit * 0.26f), style = Stroke(unit * 0.03f))
            val label = textMeasurer.measure("${(fraction * 100).toInt()}%", TextStyle(fontSize = (unit * 0.42f).toSp(), fontWeight = FontWeight.Bold))
            drawText(label, color = Color.White, topLeft = Offset(bx + bw + unit * 0.3f, unit * 0.3f))
            if (g.bestPercent > 0) {
                val best = textMeasurer.measure("Best ${g.bestPercent}%", TextStyle(fontSize = (unit * 0.36f).toSp(), fontWeight = FontWeight.Bold))
                drawText(best, color = Color.White.copy(alpha = 0.7f), topLeft = Offset(bx, unit * 0.85f))
            }
        }
    }
    // the cube breaking: the whole picture flashes white and fades
    if (g.hit > 0.01f) drawRect(Color.White.copy(alpha = g.hit * 0.55f), size = Size(w, h))
}
