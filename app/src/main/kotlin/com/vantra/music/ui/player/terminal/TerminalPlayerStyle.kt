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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import com.vantra.music.LocalPlayerConnection
import com.vantra.music.constants.TerminalBeatKey
import com.vantra.music.constants.TerminalIntensity
import com.vantra.music.constants.TerminalIntensityKey
import com.vantra.music.constants.TerminalOrientation
import com.vantra.music.constants.TerminalOrientationKey
import com.vantra.music.constants.TerminalQuality
import com.vantra.music.constants.TerminalQualityKey
import com.vantra.music.constants.TerminalTypingSpeed
import com.vantra.music.constants.TerminalTypingSpeedKey
import com.vantra.music.constants.TerminalBassResponse
import com.vantra.music.constants.TerminalBassResponseKey
import com.vantra.music.constants.TerminalEmotionalZoom
import com.vantra.music.constants.TerminalEmotionalZoomKey
import com.vantra.music.constants.TerminalEmphasisMarkersKey
import com.vantra.music.constants.TerminalTypingKey
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

private val Mono = FontFamily.Monospace
private val Ink = Color(TerminalPalette.Ink)
private val Red = Color(TerminalPalette.Cursor)

/**
 * The Terminal player style: the song's synced lyrics typed out as syntax-highlighted code in a
 * continuous dark editor plane, with the existing camera moving to the music. It sits in the same frame as the other
 * alternate styles (gestures, the ••• menu, the quarter turn), reads the same playback state and
 * lyrics, and keeps all drawing inside [TerminalScene] so a renderer failure can't reach playback.
 */
@Composable
fun TerminalPlayerStyle(actions: StyleActions, modifier: Modifier = Modifier, preview: Boolean = false) {
    val track = rememberStyleTrack()
    val connection = LocalPlayerConnection.current
    val sceneFrame = remember { mutableLongStateOf(0L) }
    // Terminal owns one frame clock. Chrome subscribes to it instead of starting a second loop.
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

    TerminalFrame(rotated=rotated, preview=preview, modifier=modifier) { landscape, menuOpen, setMenuOpen ->
        val insets = terminalInsets(landscape)
        // landscape is a fullscreen code environment: the system bars step aside while it shows
        ImmersiveWhileLandscape(active = landscape && !preview)

        // in landscape the controls step aside while the music plays; a tap brings them back
        var reveal by remember { mutableIntStateOf(0) }
        var controlsOn by remember { mutableStateOf(true) }
        var controlsFocused by remember { mutableStateOf(false) }
        val touchExploration = rememberTerminalTouchExploration()
        val accessibility = LocalAccessibilityManager.current
        val autoHide = TerminalPresentation.autoHide(landscape, playing, menuOpen,
            touchExploration, controlsFocused, preview)
        LaunchedEffect(autoHide, reveal, accessibility) {
            controlsOn = true
            if (autoHide) {
                val timeout = accessibility?.calculateRecommendedTimeoutMillis(ControlsHideMs,
                    containsIcons=true, containsText=true, containsControls=true) ?: ControlsHideMs
                delay(timeout)
                controlsOn = false
            }
        }
        val controlsAlpha by animateFloatAsState(if (controlsOn) 1f else 0f, tween(220), label = "terminalControls")

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .then(if (preview) Modifier else Modifier.semantics {
                    onClick(label = "Show playback controls") { reveal++; true }
                })
                .pointerInput(Unit) {
                    // a tap that takes nothing from the gestures around it (double tap still plays and pauses)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val up = waitForUpOrCancellation(PointerEventPass.Initial)
                        if (up != null && (up.position - down.position).getDistance() < viewConfiguration.touchSlop) reveal++
                    }
                },
        ) {
            val compactLandscape = landscape && maxHeight < 240.dp
            TerminalScene(
                lines = if (lyrics.mediaId == currentMediaId) lyrics.lines else emptyList(),
                lyricsMediaId = lyrics.mediaId,
                trackMediaId = currentMediaId,
                frame = sceneFrame,
                track = track,
                position = position,
                landscape = landscape,
                preview = preview,
                source = source,
                modifier = if (preview) Modifier.fillMaxSize().padding(6.dp) else Modifier.fillMaxSize().padding(
                    start = insets.start + 4.dp,
                    top = insets.top + (if (landscape) 60.dp else 72.dp),
                    end = insets.end + (if (landscape && !compactLandscape) 72.dp else 4.dp),
                    bottom = insets.bottom + (if (compactLandscape) 68.dp else if (landscape) 28.dp else 104.dp),
                ),
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
                )
                if (controlsAlpha > 0.01f) {
                    Box(Modifier.fillMaxSize()
                        .padding(top=if (landscape) insets.top else 0.dp,
                            bottom=if (landscape) insets.bottom else 0.dp)
                        .onFocusChanged { controlsFocused=it.hasFocus }.focusGroup()
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

private const val ControlsHideMs = 3_500L

// ---- the scene --------------------------------------------------------------------------

/** Everything the scene moves, kept off the snapshot system: the frame loop writes it, the draw reads it. */
private class SceneState {
    val camera = TerminalCamera()
    val sync = TerminalSynchronization()
    val transition = TerminalBlockTransition()
    var discontinuity = 0L
    var motionEnabled = true
    val cursor = TerminalCursor()
    val scroll = Spring()
    val codeCaret = TerminalCodeCaret()
    var typingTimeline: TerminalTypingTimeline? = null
    var typingTimeMs = 0L
    var typingSpeed = 1f
    var typingEnabled = false
    val emphasis = TerminalEmphasisFrame()
    val intent = TerminalCameraIntent()
    var glow = 0f
    var low = false
    var timeNs = 0L
    var solidCursor = false
    var followX = 0f
    var accessibleLine: androidx.compose.runtime.MutableIntState? = null
    var accessibleText: MutableState<String>? = null
    var document: TerminalDocument? = null
    var primed = false
}

@Composable
private fun TerminalScene(
    lines: List<LyricsEntry>,
    lyricsMediaId: String?,
    trackMediaId: String?,
    frame: MutableLongState,
    track: StyleTrack,
    position: LivePosition,
    landscape: Boolean,
    preview: Boolean,
    source: MutableState<BeatSource>,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val connection = LocalPlayerConnection.current
    val renderer = remember { TerminalRenderer() }
    val state = remember { SceneState() }
    val failed = remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    val intensity by rememberEnumPreference(TerminalIntensityKey, TerminalIntensity.NORMAL)
    val quality by rememberEnumPreference(TerminalQualityKey, TerminalQuality.AUTO)
    val beatPref by com.vantra.music.utils.rememberPreference(TerminalBeatKey, defaultValue = true)
    val typingPref by com.vantra.music.utils.rememberPreference(TerminalTypingKey, defaultValue = true)
    val typingSpeed by rememberEnumPreference(TerminalTypingSpeedKey, TerminalTypingSpeed.NORMAL)
    val bassResponse by rememberEnumPreference(TerminalBassResponseKey, TerminalBassResponse.NORMAL)
    val emotionalZoom by rememberEnumPreference(TerminalEmotionalZoomKey, TerminalEmotionalZoom.NORMAL)
    val rawMarkers by com.vantra.music.utils.rememberPreference(TerminalEmphasisMarkersKey, defaultValue="{}")
    val markers = remember(rawMarkers,trackMediaId) {
        TerminalEmphasis.parse(TerminalMarkerPreferences.text(rawMarkers,trackMediaId)).markers
    }
    val markersNow by rememberUpdatedState(markers)
    val trackIdNow by rememberUpdatedState(trackMediaId)
    val lowDevice = remember(context) { isLowEndDevice(context) }

    val density = LocalDensity.current
    val cols = remember(size, landscape, density.density, density.fontScale) {
        renderer.layout(size.width, size.height, landscape, density.density, density.fontScale)
    }
    val doc = remember(lines, track.title, track.artist, cols, size.width, density.density, density.fontScale) {
        buildDocument(lines, track, cols, renderer::measureColumns)
    }
    val docNow by rememberUpdatedState(doc)
    val typingTimeline = remember(doc) { TerminalTypingTimeline(doc) }
    val typingNow by rememberUpdatedState(typingTimeline)
    val landscapeNow by rememberUpdatedState(landscape)
    val activePlayer = connection?.service?.playerFlow?.collectAsState()?.value ?: connection?.player
    val lyricsIdNow by rememberUpdatedState(lyricsMediaId)
    val missingDoc = remember(cols) { TerminalDocument.unavailable(cols) }
    val missingDocNow by rememberUpdatedState(missingDoc)
    val missingTyping = remember(missingDoc) { TerminalTypingTimeline(missingDoc) }
    val missingTypingNow by rememberUpdatedState(missingTyping)
    val settingsNow by rememberUpdatedState(TerminalFrameSettings(intensity.scale, quality, beatPref, typingPref, typingSpeed.scale, bassResponse.scale, emotionalZoom.scale))
    val showing = rememberStyleShowing()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Actual transport events include tiny seeks and repeat of the same media ID.
    DisposableEffect(activePlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: androidx.media3.common.Player.PositionInfo,
                newPosition: androidx.media3.common.Player.PositionInfo, reason: Int) { state.discontinuity++ }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) { state.discontinuity++ }
        }
        if (!preview) activePlayer?.addListener(listener)
        onDispose { if (!preview) activePlayer?.removeListener(listener) }
    }

    // A selector card uses the same editor and camera, held on a still frame.
    if (preview) {
        LaunchedEffect(doc, size, landscape) {
            val s = state
            doc.locate(position.positionMs(), typing = false, out = s.cursor)
            s.document = doc
            s.typingTimeline = typingTimeline
            s.typingTimeMs = position.positionMs()
            s.typingEnabled = false
            typingTimeline.locate(s.typingTimeMs,1f,false,s.codeCaret)
            s.accessibleLine?.intValue = s.cursor.line
            s.accessibleText?.value = doc.lines.getOrNull(s.cursor.line)?.text ?: "No synchronized lyrics"
            s.transition.update(0f, s.cursor.line, snap = true)
            s.scroll.snap(doc.focusRow(s.cursor, renderer.visibleRows()))
            s.camera.reset()
            s.camera.baseYaw = if (landscape) 0.10f else 0.06f
            s.camera.basePitch = -0.09f
            s.camera.yaw.snap(s.camera.baseYaw)
            s.camera.pitch.snap(s.camera.basePitch)
            s.low = false
            s.glow = 0.5f
            s.solidCursor = true
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
                            var acc = 0f
                            var calm = 0f
                            var slowFrames = 0
                            var degraded = false
                            while (true) {
                                if (renderer.failed) {
                                    failed.value = true
                                    source.value = BeatSource.NONE
                                    return@collectLatest
                                }
                                val castBeforeWait = conn.service.castConnectionHandler
                                val playingBeforeWait = if (castBeforeWait?.isCasting?.value == true)
                                    castBeforeWait.castIsPlaying.value else player.isPlaying
                                // No audio/typing polling loop while paused: the same loop slows to caret blink rate.
                                val now = if (!playingBeforeWait && calm > 1.5f) {
                                    delay(BlinkPollMs)
                                    System.nanoTime()
                                } else androidx.compose.runtime.withFrameNanos { it }
                                val elapsed = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceAtLeast(0.001f)
                                last = now
                                // Re-read transport after suspension: pause/resume or Cast can change while waiting.
                                val cast = conn.service.castConnectionHandler
                                val casting = cast?.isCasting?.value == true
                                val playing = if (casting) cast?.castIsPlaying?.value == true else player.isPlaying
                                val prefs = settingsNow
                                if (prefs.quality == TerminalQuality.AUTO && playing) {
                                    slowFrames = if (elapsed > 0.026f) slowFrames + 1 else maxOf(0, slowFrames - 1)
                                    if (slowFrames > 90) degraded = true
                                }
                                val low = prefs.quality == TerminalQuality.LOW ||
                                    (prefs.quality == TerminalQuality.AUTO && (lowDevice || degraded))
                                s.low = low
                                acc += elapsed
                                if (low && playing && acc < 0.032f) continue
                                val stepDt = acc.coerceAtMost(0.1f)
                                val transportDt = acc
                                acc = 0f
                                val reduced = AppMotion.durationScale.scaleFactor == 0f
                                val motionOff = reduced || prefs.intensity == 0f
                                val k = if (motionOff) 0f else prefs.intensity
                                s.motionEnabled = !motionOff
                                val posMs = if (casting) cast?.castPosition?.value ?: 0L else player.currentPosition
                                val mediaId = if (casting) conn.mediaMetadata.value?.id else player.currentMediaItem?.mediaId
                                val boundary = s.sync.beginFrame(transportDt, posMs, mediaId,
                                    if (casting) cast else player, s.discontinuity, playing,
                                    if (casting) 1f else player.playbackParameters.speed)
                                if (s.sync.timelineJump) s.camera.clearAudioReaction()

                                // Only the current local player's PCM can establish an audio onset.
                                // Cast is remote: its reported clock drives fallback lyrics, never local FFT data.
                                val enabled = prefs.beat && !motionOff && prefs.bassGain > 0f
                                val spectrum = if (enabled && playing && !casting) conn.service.spectrumOf(player) else null
                                val heard = spectrum?.read(posMs, s.sync.tracker.bands) == true
                                val activeDoc = if (lyricsIdNow != null && lyricsIdNow == mediaId) docNow else missingDocNow
                                val docChanged = s.document !== activeDoc
                                s.sync.react(stepDt, playing, enabled, !activeDoc.isEmpty, heard)
                                if (source.value != s.sync.source) source.value = s.sync.source
                                val lyricMs = posMs + conn.lyricsOffsetMs.value.toLong()
                                activeDoc.locate(lyricMs, prefs.typing && !reduced, s.cursor)
                                s.typingTimeline = if(activeDoc === docNow) typingNow else missingTypingNow
                                s.typingTimeMs = lyricMs
                                s.typingSpeed = prefs.typingSpeed
                                s.typingEnabled = prefs.typing && !reduced
                                s.typingTimeline?.locate(lyricMs,prefs.typingSpeed,s.typingEnabled,s.codeCaret)
                                if(s.typingEnabled) s.typingTimeline?.focusCursor(s.cursor,s.codeCaret)
                                TerminalEmphasis.sample(if(trackIdNow==mediaId) markersNow else emptyList(),
                                    lyricMs,if(motionOff) 0f else prefs.zoomGain,s.emphasis)
                                s.transition.update(stepDt, if (s.cursor.idle) -1 else s.cursor.line,
                                    snap = boundary || docChanged || motionOff)
                                s.camera.baseYaw = if (landscapeNow) 0.10f else 0.06f
                                s.camera.basePitch = -0.09f
                                val line = activeDoc.lines.getOrNull(s.cursor.line)
                                s.followX = if (line == null) 0f else kotlin.math.sin(line.index * 0.65f)
                                val nextTime = activeDoc.lines.getOrNull(s.cursor.line + 1)?.timeMs
                                val interval = if (line == null) 1L else
                                    ((nextTime ?: (line.timeMs + 6_000L)) - line.timeMs).coerceAtLeast(1L)
                                val progress = if (line == null) 0f else
                                    ((lyricMs - line.timeMs).toFloat() / interval).coerceIn(0f, 1f)
                                val target = activeDoc.focusRow(s.cursor, renderer.visibleRows())
                                if (!s.primed || docChanged || boundary || motionOff) {
                                    s.scroll.snap(target)
                                    s.primed = true
                                } else s.scroll.step(stepDt, target, 49f, 14f)
                                s.document = activeDoc
                                if (docChanged || s.accessibleLine?.intValue != s.cursor.line) {
                                    s.accessibleLine?.intValue = s.cursor.line
                                    s.accessibleText?.value = line?.text?.takeIf { it.isNotBlank() }
                                        ?: "No synchronized lyrics"
                                }
                                val audio = s.sync.source == BeatSource.AUDIO
                                s.intent.focusZoom=s.emphasis.zoom
                                s.intent.focusWeight=s.emphasis.weight
                                s.intent.stillness=s.emphasis.stillness
                                s.intent.climax=s.emphasis.climax
                                s.intent.bassGain=prefs.bassGain
                                s.intent.onset=if(audio) s.sync.tracker.kick else 0f
                                s.intent.reconstruct=s.sync.timelineJump && !casting
                                s.camera.update(stepDt, posMs / 1000f, k,
                                    if (audio) s.sync.tracker.bassDrive else 0f,
                                    if (audio) s.sync.tracker.energy else 0f,
                                    s.sync.pulse, landscapeNow, s.followX, progress, s.cursor.idle,
                                    audioReactive = audio, intent=s.intent)
                                val glowTarget = if (audio) s.sync.tracker.bass else 0.1f
                                s.glow += (glowTarget - s.glow) * (1f - kotlin.math.exp(-stepDt / 0.18f))
                                s.solidCursor = playing && s.typingEnabled && s.codeCaret.active
                                s.timeNs = now
                                calm = if (playing || boundary || docChanged || abs(target - s.scroll.value) > 0.02f) 0f else calm + stepDt
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

    val accessibleLine = remember { mutableIntStateOf(Int.MIN_VALUE) }
    val accessibleText = remember { mutableStateOf("No synchronized lyrics") }
    // The loop updates these only when the real active line/document changes, never per character.
    state.accessibleLine = accessibleLine
    state.accessibleText = accessibleText
    Box(
        modifier
            .semantics { contentDescription = accessibleText.value }
            .onSizeChanged { size = it }
            .drawBehind {
                // reading the tick here is what repaints the scene each frame without recomposing anything
                frame.longValue
                drawIntoCanvas { canvas ->
                    val initial = state.document == null
                    renderer.draw(
                        canvas.nativeCanvas, state.document ?: docNow, state.cursor, state.scroll.value, state.camera,
                        state.timeNs, state.low, landscapeNow, state.glow, state.solidCursor,
                        activeMix = state.transition.mix, previousLine = state.transition.previousLine,
                        generation = state.sync.generation, motionEnabled = state.motionEnabled,
                        typing=state.typingTimeline ?: typingNow,
                        typingPositionMs=if(initial) position.positionMs() else state.typingTimeMs,
                        typingSpeed=if(initial) typingSpeed.scale else state.typingSpeed,
                        typingEnabled=if(initial) !preview && typingPref && AppMotion.durationScale.scaleFactor!=0f else state.typingEnabled,
                        codeCaret=state.codeCaret,
                    )
                }
            },
    ) {
        if (failed.value) {
            // the renderer gave up; the music carries on and the words stay readable
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(track.title, color = Ink, style = TextStyle(fontFamily = Mono, fontSize = 18.sp))
                Spacer(Modifier.height(6.dp))
                Text("terminal view unavailable on this device, playback is unaffected", color = Ink.copy(alpha = 0.55f), style = TextStyle(fontFamily = Mono, fontSize = 11.sp))
            }
        }
    }
}

private data class TerminalFrameSettings(val intensity: Float, val quality: TerminalQuality,
    val beat: Boolean, val typing: Boolean, val typingSpeed: Float, val bassGain: Float, val zoomGain: Float)

private const val BlinkPollMs = 265L

/** Real synchronized lyrics only; absent lyrics produce a code-only status document. */
private fun buildDocument(
    lines: List<LyricsEntry>, track: StyleTrack, columns: Int, measure: (String) -> Float,
): TerminalDocument {
    if (lines.isNotEmpty()) {
        val times = LongArray(lines.size) { lines[it].time }
        // a line break or tab can't be drawn in a row; each becomes one space, so every position stays the same
        val texts = lines.map { entry ->
            if (entry.text.any { it == '\n' || it == '\r' || it == '\t' }) entry.text.map { if (it == '\n' || it == '\r' || it == '\t') ' ' else it }.joinToString("") else entry.text
        }
        return TerminalDocument.build(times, texts, columns, track.title, track.artist, measure)
    }
    return TerminalDocument.unavailable(columns)
}

// ---- the chrome around the scene ---------------------------------------------------------

@Composable
private fun TerminalChrome(
    track: StyleTrack,
    position: LivePosition,
    duration: Long,
    hasLyrics: Boolean,
    source: BeatSource,
    landscape: Boolean,
    insetStart: androidx.compose.ui.unit.Dp,
    insetTop: androidx.compose.ui.unit.Dp,
    insetEnd: androidx.compose.ui.unit.Dp,
    insetBottom: androidx.compose.ui.unit.Dp,
    /** landscape keeps only the progress hairline until a tap brings the rest back */
    uiAlpha: Float,
) {
    val chromeAlpha = if (landscape) uiAlpha else 1f
    val seconds = rememberPlayedSeconds(position)
    Box(Modifier.fillMaxSize()) {
        // the editor's tab: artwork, title and artist, over a hairline that is the song's progress
        Column(
            Modifier
                .semantics { if (chromeAlpha < 0.01f) hideFromAccessibility() }
                .align(Alignment.TopStart)
                .padding(start = 18.dp + insetStart, top = insetTop + 14.dp, end = (if (landscape) 76.dp else 70.dp) + insetEnd)
                .fillMaxWidth(),
        ) {
            Row(Modifier.graphicsLayer { alpha = chromeAlpha }, verticalAlignment = Alignment.CenterVertically) {
                if (track.artwork != null) {
                    AsyncImage(
                        model = track.artwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = Ink.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Mono, fontSize = 12.sp))
                    Text(track.artist, color = Ink.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Mono, fontSize = 10.sp))
                }
                Spacer(Modifier.width(10.dp))
                Text("${clock(seconds)} / ${clock(duration / 1000L)}", color = Ink.copy(alpha = 0.5f), style = TextStyle(fontFamily = Mono, fontSize = 10.sp))
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .drawBehind {
                        drawRect(Color.White.copy(alpha = 0.12f))
                        val f = (position.positionMs().toFloat() / duration.coerceAtLeast(1L)).coerceIn(0f, 1f)
                        drawRect(Red, size = androidx.compose.ui.geometry.Size(size.width * f, size.height))
                    },
            )
        }
        // the status line says plainly where the camera's pulse comes from
        Text(
            text = statusLine(source, hasLyrics),
            color = Ink.copy(alpha = 0.4f),
            style = TextStyle(fontFamily = Mono, fontSize = 10.sp),
            modifier = Modifier
                .semantics { if (chromeAlpha < 0.01f) hideFromAccessibility() }
                .align(Alignment.BottomStart)
                .padding(start = 18.dp + insetStart, bottom = insetBottom + 10.dp)
                .graphicsLayer { alpha = chromeAlpha },
        )
    }
}

private fun statusLine(source: BeatSource, hasLyrics: Boolean): String {
    val beat = when (source) {
        BeatSource.AUDIO -> "beat: audio onset"
        BeatSource.LYRIC_TIMING -> "beat: none (lyric timing only)"
        BeatSource.PLAYBACK_TIMING -> "beat: none (playback timing only)"
        BeatSource.NONE -> "beat: off"
    }
    return "$beat  |  lyrics: ${if (hasLyrics) "synced" else "none"}"
}

private fun clock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    return "%d:%02d".format(s / 60, s % 60)
}

// ---- device -------------------------------------------------------------------------------

private fun isLowEndDevice(context: Context): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || am?.isLowRamDevice == true
}

/**
 * Whether the phone is held on its side the way the quarter turn reads upright (turned
 * counter-clockwise), for the Auto orientation. The window itself stays as the app has it; only
 * the Terminal turns, so nothing restarts and playback never notices. It listens only while the
 * style is on screen, and not at all when the system's auto-rotate is off.
 */
@Composable
private fun rememberDeviceSideways(enabled: Boolean): Boolean {
    val context = LocalContext.current
    val showing = rememberStyleShowing()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var sideways by remember { mutableStateOf(false) }
    var autoRotate by remember { mutableStateOf(true) }
    DisposableEffect(context, enabled) {
        val resolver = context.contentResolver
        fun read() {
            autoRotate = runCatching { Settings.System.getInt(resolver,
                Settings.System.ACCELEROMETER_ROTATION, 1) == 1 }.getOrDefault(true)
        }
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { read() }
        }
        if (enabled) {
            read()
            resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, observer)
        }
        onDispose { if (enabled) resolver.unregisterContentObserver(observer) }
    }
    val listener = remember(context) {
        object : OrientationEventListener(context.applicationContext) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN || !autoRotate) return
                val next = when {
                    orientation in 250..290 -> true
                    orientation !in 220..320 -> false
                    else -> sideways
                }
                if (next != sideways) sideways = next
            }
        }
    }
    LaunchedEffect(enabled, autoRotate, listener, lifecycle, showing) {
        try {
            if (!enabled || !autoRotate || !listener.canDetectOrientation()) {
                sideways = false
                return@LaunchedEffect
            }
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    snapshotFlow { showing() }.collect { on -> if (on) listener.enable() else listener.disable() }
                } finally { listener.disable() }
            }
        } finally { listener.disable() }
    }
    DisposableEffect(listener) { onDispose { listener.disable() } }
    return enabled && autoRotate && sideways
}

/**
 * Hides the status and navigation bars while the landscape Terminal is on screen, and brings them
 * back when it leaves (turned portrait, another style, the player collapsed, or the app closing).
 * A swipe from the edge shows them briefly. It changes nothing but the window's bars.
 */
@Composable
private fun ImmersiveWhileLandscape(active: Boolean) {
    val view = LocalView.current
    val showing = rememberStyleShowing()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(active, showing, lifecycle) {
        try {
            if (!active) return@LaunchedEffect
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try { snapshotFlow { showing() }.collect { shown -> on = shown } }
                finally { on = false }
            }
        } finally { on = false }
    }
    DisposableEffect(view, on) {
        val window = view.context.findActivity()?.window
        val controller: WindowInsetsControllerCompat? = window?.let { WindowCompat.getInsetsController(it, view) }
        var behaviour: Int? = null
        var visibleTypes = 0
        var hiddenTypes = 0
        if (on && controller != null) {
            val root = androidx.core.view.ViewCompat.getRootWindowInsets(view)
            val status = WindowInsetsCompat.Type.statusBars()
            val nav = WindowInsetsCompat.Type.navigationBars()
            if (root?.isVisible(status) != false) visibleTypes = visibleTypes or status else hiddenTypes = hiddenTypes or status
            if (root?.isVisible(nav) != false) visibleTypes = visibleTypes or nav else hiddenTypes = hiddenTypes or nav
            behaviour = controller.systemBarsBehavior
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            behaviour?.let { old ->
                controller?.systemBarsBehavior = old
                if (visibleTypes != 0) controller?.show(visibleTypes)
                if (hiddenTypes != 0) controller?.hide(hiddenTypes)
            }
        }
    }
}
