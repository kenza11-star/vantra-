package com.vantra.music.ui.player.terminal

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.OrientationEventListener
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.vantra.music.LocalPlayerConnection
import com.vantra.music.constants.TerminalBassResponse
import com.vantra.music.constants.TerminalBassResponseKey
import com.vantra.music.constants.TerminalBeatKey
import com.vantra.music.constants.TerminalBeatSync
import com.vantra.music.constants.TerminalBeatSyncKey
import com.vantra.music.constants.TerminalEmotionalZoom
import com.vantra.music.constants.TerminalEmotionalZoomKey
import com.vantra.music.constants.TerminalEmphasisMarkersKey
import com.vantra.music.constants.TerminalFlashMode
import com.vantra.music.constants.TerminalFlashModeKey
import com.vantra.music.constants.TerminalIntensity
import com.vantra.music.constants.TerminalIntensityKey
import com.vantra.music.constants.TerminalOrientation
import com.vantra.music.constants.TerminalOrientationKey
import com.vantra.music.constants.TerminalQuality
import com.vantra.music.constants.TerminalQualityKey
import com.vantra.music.constants.TerminalShotMode
import com.vantra.music.constants.TerminalShotModeKey
import com.vantra.music.constants.TerminalTypingKey
import com.vantra.music.constants.TerminalTypingSpeed
import com.vantra.music.constants.TerminalTypingSpeedKey
import com.vantra.music.lyrics.LyricsEntry
import com.vantra.music.ui.player.findActivity
import com.vantra.music.ui.player.styles.LivePosition
import com.vantra.music.ui.player.styles.StyleActions
import com.vantra.music.ui.player.styles.StyleTrack
import com.vantra.music.ui.player.styles.rememberDurationMs
import com.vantra.music.ui.player.styles.rememberPlayedSeconds
import com.vantra.music.ui.player.styles.rememberStyleLyrics
import com.vantra.music.ui.player.styles.rememberStyleShowing
import com.vantra.music.ui.player.styles.rememberStyleTrack
import com.vantra.music.ui.player.styles.withLyricsOffset
import com.vantra.music.ui.utils.AppMotion
import com.vantra.music.utils.rememberEnumPreference
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/** the settings the Phonk loop reads each frame */
private data class PhonkSettings(val intensity: Float, val beat: Boolean, val bassGain: Float, val flashScale: Float, val beatLeadMs: Long)

/**
 * The Phonk player style: a phonk / montagem lyric video. The synced lyrics are cut into short
 * phrases and slammed onto a black stage one beat at a time next to a CD case showing the cover,
 * and every kick cuts the whole picture to white, grey and back to black with a shockwave.
 * It sits in the same frame as the Terminal (gestures, the ... menu, the quarter turn, the
 * controls that step aside while the music plays) and shares the beat, flash and sync settings of
 * the Terminal options. All drawing stays inside [PhonkScene], so a renderer failure can't reach
 * playback.
 */
@Composable
fun PhonkPlayerStyle(actions: StyleActions, modifier: Modifier = Modifier, preview: Boolean = false) {
    val track = rememberStyleTrack()
    val connection = LocalPlayerConnection.current
    val sceneFrame = remember { mutableLongStateOf(0L) }
    val position = remember(connection) {
        LivePosition(read = {
            val cast = connection?.service?.castConnectionHandler
            if (cast?.isCasting?.value == true) cast.castPosition.value
            else connection?.player?.currentPosition ?: 0L
        }, tick = { sceneFrame.longValue })
    }.withLyricsOffset()
    val duration = rememberDurationMs()
    val lyrics = rememberStyleLyrics(readings = !preview)
    var orientation by rememberEnumPreference(TerminalOrientationKey, TerminalOrientation.AUTO)
    val sideways = rememberDeviceSideways(enabled = !preview && orientation == TerminalOrientation.AUTO)
    val rotated = when (orientation) {
        TerminalOrientation.AUTO -> sideways
        TerminalOrientation.PORTRAIT -> false
        TerminalOrientation.LANDSCAPE -> true
    }
    val source = remember { mutableStateOf(BeatSource.NONE) }
    val playing = connection?.isPlaying?.collectAsState()?.value == true
    val currentMediaId = connection?.mediaMetadata?.collectAsState()?.value?.id

    TerminalFrame(rotated = rotated, preview = preview, modifier = modifier) { landscape, menuOpen, setMenuOpen ->
        val insets = terminalInsets(landscape)
        ImmersiveWhileLandscape(active = landscape && !preview)

        // the picture is the whole point: the controls step aside while the music plays, in portrait too
        var reveal by remember { mutableIntStateOf(0) }
        var controlsOn by remember { mutableStateOf(true) }
        var controlsFocused by remember { mutableStateOf(false) }
        val touchExploration = rememberTerminalTouchExploration()
        val accessibility = LocalAccessibilityManager.current
        val autoHide = playing && !menuOpen && !touchExploration && !controlsFocused && !preview
        LaunchedEffect(autoHide, reveal, accessibility) {
            controlsOn = true
            if (autoHide) {
                val timeout = accessibility?.calculateRecommendedTimeoutMillis(ControlsHideMs,
                    containsIcons = true, containsText = true, containsControls = true) ?: ControlsHideMs
                delay(timeout)
                controlsOn = false
            }
        }
        val controlsAlpha by animateFloatAsState(if (controlsOn) 1f else 0f, tween(220), label = "phonkControls")

        // the red progress line only shows for a moment after a tap
        var tapShown by remember { mutableStateOf(false) }
        LaunchedEffect(reveal) {
            if (reveal > 0) {
                tapShown = true
                delay(ControlsHideMs)
                tapShown = false
            }
        }
        val hairlineAlpha by animateFloatAsState(if (tapShown || menuOpen) 1f else 0f, tween(220), label = "phonkHairline")

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .then(if (preview) Modifier else Modifier.semantics {
                    onClick(label = "Show playback controls") { reveal++; true }
                })
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val up = waitForUpOrCancellation(PointerEventPass.Initial)
                        if (up != null && (up.position - down.position).getDistance() < viewConfiguration.touchSlop) reveal++
                    }
                },
        ) {
            PhonkScene(
                lines = if (lyrics.mediaId == currentMediaId) lyrics.lines else emptyList(),
                lyricsMediaId = lyrics.mediaId,
                frame = sceneFrame,
                track = track,
                preview = preview,
                source = source,
                modifier = Modifier.fillMaxSize(),
            )
            if (!preview) {
                TerminalChrome(
                    track = track,
                    position = position,
                    duration = duration,
                    hasLyrics = lyrics.mediaId == currentMediaId && lyrics.lines.isNotEmpty(),
                    source = source.value,
                    landscape = landscape,
                    insetStart = insets.start,
                    insetTop = insets.top,
                    insetEnd = insets.end,
                    insetBottom = insets.bottom,
                    uiAlpha = controlsAlpha,
                    hairlineAlpha = hairlineAlpha,
                )
                if (controlsAlpha > 0.01f) {
                    Box(Modifier.fillMaxSize()
                        .padding(top = if (landscape) insets.top else 0.dp,
                            bottom = if (landscape) insets.bottom else 0.dp)
                        .onFocusChanged { controlsFocused = it.hasFocus }.focusGroup()
                        .graphicsLayer { alpha = controlsAlpha }) {
                        TerminalControls(landscape, insets, menuOpen, setMenuOpen, actions) {
                            orientation = if (rotated) TerminalOrientation.PORTRAIT else TerminalOrientation.LANDSCAPE
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhonkScene(
    lines: List<LyricsEntry>,
    lyricsMediaId: String?,
    frame: MutableLongState,
    track: StyleTrack,
    preview: Boolean,
    source: MutableState<BeatSource>,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val connection = LocalPlayerConnection.current
    val renderer = remember { PhonkRenderer() }
    val state = remember { PhonkState() }
    val failed = remember { mutableStateOf(false) }
    val density = LocalDensity.current.density

    val intensity by rememberEnumPreference(TerminalIntensityKey, TerminalIntensity.NORMAL)
    val beatPref by com.vantra.music.utils.rememberPreference(TerminalBeatKey, defaultValue = true)
    val bassResponse by rememberEnumPreference(TerminalBassResponseKey, TerminalBassResponse.NORMAL)
    val flashMode by rememberEnumPreference(TerminalFlashModeKey, TerminalFlashMode.FULL)
    val beatSync by rememberEnumPreference(TerminalBeatSyncKey, TerminalBeatSync.EXACT)
    val settingsNow by rememberUpdatedState(PhonkSettings(intensity.scale, beatPref,
        bassResponse.scale * intensity.scale.coerceAtLeast(0.5f), flashMode.scale, beatSync.leadMs))
    val showing = rememberStyleShowing()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activePlayer = connection?.service?.playerFlow?.collectAsState()?.value ?: connection?.player
    val chunks = remember(lines) { PhonkChunker.chunks(lines) }
    val chunksNow by rememberUpdatedState(chunks)
    val lyricsIdNow by rememberUpdatedState(lyricsMediaId)
    state.artist = track.artist
    state.title = track.title

    // the cover for the CD, loaded off the main thread; the disc falls back to a drawn spiral
    LaunchedEffect(track.artwork) {
        val url = track.artwork
        if (url.isNullOrBlank()) {
            renderer.setArt(null)
            return@LaunchedEffect
        }
        val bitmap = try {
            withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context).data(url).size(512, 512).allowHardware(false).build()
                context.imageLoader.execute(request).image?.toBitmap()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            null
        }
        renderer.setArt(bitmap)
        frame.longValue++
    }

    DisposableEffect(activePlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: androidx.media3.common.Player.PositionInfo,
                newPosition: androidx.media3.common.Player.PositionInfo, reason: Int) { state.discontinuity++ }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) { state.discontinuity++ }
        }
        if (!preview) activePlayer?.addListener(listener)
        onDispose { if (!preview) activePlayer?.removeListener(listener) }
    }

    if (preview) {
        // a selector card is a still frame: the stage is up, nothing flashes
        LaunchedEffect(track.title, track.artist) {
            state.stage.snap(1f)
            frame.longValue++
        }
    } else {
        LaunchedEffect(connection, activePlayer, lifecycle, showing) {
            val conn = connection ?: return@LaunchedEffect
            val player = activePlayer ?: return@LaunchedEffect
            val s = state
            try {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    try {
                        snapshotFlow { showing() }.collectLatest { shown ->
                            s.sync.suspendAnalysis()
                            source.value = BeatSource.NONE
                            if (!shown) return@collectLatest
                            var last = 0L
                            while (true) {
                                if (renderer.failed) {
                                    failed.value = true
                                    source.value = BeatSource.NONE
                                    return@collectLatest
                                }
                                val castBefore = conn.service.castConnectionHandler
                                val playingBefore = if (castBefore?.isCasting?.value == true)
                                    castBefore.castIsPlaying.value else player.isPlaying
                                // at rest (paused, nothing moving) the loop drops to a slow poll
                                val now = if (!playingBefore && s.settled > 40) {
                                    delay(BlinkPollMs)
                                    System.nanoTime()
                                } else androidx.compose.runtime.withFrameNanos { it }
                                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceIn(0f, 0.5f)
                                last = now
                                val stepDt = dt.coerceAtMost(0.1f)
                                val cast = conn.service.castConnectionHandler
                                val casting = cast?.isCasting?.value == true
                                val playing = if (casting) cast?.castIsPlaying?.value == true else player.isPlaying
                                val prefs = settingsNow
                                val reduced = AppMotion.durationScale.scaleFactor == 0f
                                val motionOn = !reduced && prefs.intensity > 0f
                                val posMs = if (casting) cast?.castPosition?.value ?: 0L else player.currentPosition
                                val mediaId = if (casting) conn.mediaMetadata.value?.id else player.currentMediaItem?.mediaId
                                val boundary = s.sync.beginFrame(dt, posMs, mediaId,
                                    if (casting) cast else player, s.discontinuity, playing,
                                    if (casting) 1f else player.playbackParameters.speed)
                                if (s.sync.timelineJump) s.beat.reset()

                                // the beat: the local player's audio, read a little ahead or behind if asked to
                                val enabled = prefs.beat && motionOn && prefs.bassGain > 0f
                                val spectrum = if (enabled && playing && !casting) conn.service.spectrumOf(player) else null
                                val heard = spectrum != null &&
                                    ((prefs.beatLeadMs != 0L && spectrum.read(posMs + prefs.beatLeadMs, s.sync.tracker.bands)) ||
                                        spectrum.read(posMs, s.sync.tracker.bands))
                                val list = chunksNow
                                val hasLyrics = list.isNotEmpty() && lyricsIdNow != null && lyricsIdNow == mediaId
                                s.sync.react(stepDt, playing, enabled, hasLyrics, heard)
                                if (source.value != s.sync.source) source.value = s.sync.source

                                // which phrase is up: the lyric time says when, the kick says which frame
                                val steadyMs = s.clock.update(stepDt, posMs, playing,
                                    if (casting) 1f else player.playbackParameters.speed, s.sync.timelineJump)
                                val lyricMs = steadyMs + conn.lyricsOffsetMs.value.toLong()
                                val tracker = s.sync.tracker
                                val audioKick = if (s.sync.source == BeatSource.AUDIO) tracker.kick else 0f
                                val visible = if (hasLyrics) list else emptyList()
                                val changed = s.timeline.step(visible, lyricMs, stepDt, audioKick > 0f,
                                    s.sync.timelineJump || visible !== s.lastChunks)
                                s.lastChunks = visible
                                s.chunks = visible
                                // with no audio to hear, the phrases themselves keep the beat
                                val kick = if (audioKick > 0f) audioKick
                                    else if (changed && s.timeline.index >= 0 && playing && s.sync.source != BeatSource.AUDIO) 0.6f else 0f
                                s.beat.update(stepDt, kick, if (s.sync.source == BeatSource.AUDIO) tracker.bass else 0f,
                                    prefs.flashScale, prefs.bassGain, motionOn, s.fx)
                                s.stage.step(stepDt, if (s.timeline.index >= 0) 1f else 0f, 38f, 12f)
                                s.timeS += stepDt
                                // the disc turns with the music and jumps on a kick
                                val energy = if (s.sync.source == BeatSource.AUDIO) tracker.energy else 0.2f
                                if (motionOn) s.spin += stepDt * (18f + 80f * energy) + (if (kick > 0f) 22f * kick else 0f)
                                // the waveform: a new sample twenty times a second, kicks make the spikes
                                s.wavePeak = max(s.wavePeak, kick * 0.9f + (if (s.sync.source == BeatSource.AUDIO) tracker.bass * 0.35f else 0f))
                                s.waveAcc += stepDt
                                while (s.waveAcc >= 0.05f) {
                                    s.waveAcc -= 0.05f
                                    s.wave[s.waveHead] = if (playing && motionOn) s.wavePeak.coerceIn(0f, 1f) else 0f
                                    s.waveHead = (s.waveHead + 1) % s.wave.size
                                    s.wavePeak = 0f
                                }
                                s.settled = if (!playing && s.fx.isQuiet() && abs(s.stage.value - (if (s.timeline.index >= 0) 1f else 0f)) < 0.01f) s.settled + 1 else 0
                                frame.longValue = now
                            }
                        }
                    } finally {
                        s.sync.suspendAnalysis()
                        source.value = BeatSource.NONE
                    }
                }
            } finally {
                s.sync.suspendAnalysis()
                source.value = BeatSource.NONE
            }
        }
    }

    Box(
        modifier
            .semantics { contentDescription = "Phonk lyric view: ${track.title}" }
            .drawBehind {
                // reading the tick here is what repaints the scene each frame without recomposing anything
                frame.longValue
                drawIntoCanvas { canvas ->
                    renderer.draw(canvas.nativeCanvas, size.width.toInt(), size.height.toInt(), density, state, preview, System.nanoTime())
                }
            },
    ) {
        if (failed.value) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(track.title, color = Color.White, style = TextStyle(fontSize = 22.sp))
                Spacer(Modifier.height(6.dp))
                Text("phonk view unavailable on this device, playback is unaffected", color = Color.White.copy(alpha = 0.55f), style = TextStyle(fontSize = 12.sp))
            }
        }
    }
}
