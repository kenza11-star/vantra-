package com.vantra.music.ui.player.liquid

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vantra.music.LocalPlayerConnection
import com.vantra.music.constants.LiquidLyricStyle
import com.vantra.music.constants.LiquidLyricStyleKey
import com.vantra.music.constants.PlayerStyle
import com.vantra.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.vantra.music.lyrics.LyricsEntry
import com.vantra.music.lyrics.LyricsUtils
import com.vantra.music.ui.player.YouTubeVideoBackground
import com.vantra.music.ui.player.flagship.rememberAmbientClock
import com.vantra.music.ui.player.rememberSongVideoLookup
import com.vantra.music.ui.player.styles.LivePosition
import com.vantra.music.ui.player.styles.StyleActions
import com.vantra.music.ui.player.styles.StyleControls
import com.vantra.music.ui.player.styles.StyleFrame
import com.vantra.music.ui.player.styles.StyleSongs
import com.vantra.music.ui.player.styles.rememberDurationMs
import com.vantra.music.ui.player.styles.rememberLivePosition
import com.vantra.music.ui.player.styles.rememberStyleCarousel
import com.vantra.music.ui.player.styles.rememberStyleLyrics
import com.vantra.music.ui.player.styles.rememberStyleShowing
import com.vantra.music.ui.player.styles.rememberStyleTones
import com.vantra.music.ui.player.styles.rememberStyleTrack
import com.vantra.music.ui.player.styles.styleInsets
import com.vantra.music.ui.player.styles.withLyricsOffset
import com.vantra.music.ui.screens.constellation.CGraph
import com.vantra.music.ui.screens.constellation.buildGraph
import com.vantra.music.ui.theme.InterFontFamily
import com.vantra.music.utils.rememberEnumPreference
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.delay

private val Cream = Color(0xFFF6EBD8)
private val Violet = Color(0xFFB79CFF)

// a quick start that settles softly: the cover card melting into the scene, the words arriving
private val Settle = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private const val Tau = 6.2831855f
private const val LoopSeconds = 100f
private const val IntroMs = 1250
private const val IntroHoldMs = 320L
private const val ControlsHideMs = 4_200L
private const val StarWindow = 14

// how far a patch of liquid smears what lies under it, in dp: a patch, never the whole picture
private const val LiquidBlurDp = 20f

/**
 * The Liquid player style: the laptop lock-screen look. Under everything the song's own music
 * video plays (a song with no video shows its cover instead), kept sharp, with a few thick patches of
 * liquid drifting over it, and a violet light along the screen's edges. Over it a big serif clock, and the words of the song
 * set in the lyric look picked in the selector's Edit sheet (or from the Aa button): a star map
 * that grows word by word, big kinetic lines, karaoke or one quiet line. It stands upright, and can be
 * turned sideways from the ••• menu; every new song melts in from a small cover card.
 */
@Composable
fun LiquidPlayerStyle(actions: StyleActions, modifier: Modifier = Modifier, preview: Boolean = false) {
    val tones = rememberStyleTones()
    val track = rememberStyleTrack()
    val connection = LocalPlayerConnection.current
    val position = rememberLivePosition(animated = !preview).withLyricsOffset()
    val lyrics = rememberStyleLyrics(readings = !preview)
    val carousel = rememberStyleCarousel(preview)
    val showing = rememberStyleShowing()
    val shown by remember { derivedStateOf { preview || showing() } }
    val playing = connection?.isPlaying?.collectAsState()?.value ?: false
    val info = connection?.uiState?.trackInfo?.collectAsState()?.value
    val metadata = connection?.mediaMetadata?.collectAsState()?.value
    val mediaId = info?.mediaId.orEmpty()
    val cover = info?.thumbnailUrl ?: track.artwork
    val isVideoSong = metadata?.id == mediaId && metadata?.isVideoSong == true

    // the song's video, once found: its own when it is a video, the official one otherwise; none, and the cover stays
    val lookup = rememberSongVideoLookup(
        mediaId = mediaId,
        title = track.title,
        artist = track.artist,
        isVideoSong = isVideoSong,
        enabled = !preview && shown && mediaId.isNotBlank(),
    )

    val time = rememberAmbientClock(running = shown)
    var lyricStyle by rememberEnumPreference(LiquidLyricStyleKey, LiquidLyricStyle.CONSTELLATION)

    // every song starts as a small cover card that melts into the scene
    val intro = remember(mediaId, preview) { Animatable(if (preview) 1f else 0f) }
    LaunchedEffect(mediaId, preview, shown) {
        if (preview || !shown) return@LaunchedEffect
        intro.snapTo(0f)
        delay(IntroHoldMs)
        intro.animateTo(1f, tween(IntroMs, easing = Settle))
    }

    val rawLyrics = connection?.currentLyrics?.collectAsState(initial = null)?.value?.lyrics
    val durationMs = rememberDurationMs()
    val graph = remember(rawLyrics, mediaId, durationMs) {
        buildGraph(rawLyrics, durationMs, listOf(track.title, track.artist), mediaId.ifEmpty { track.title })
    }
    val web = remember(graph) { webLinks(graph) }
    val entries = remember(rawLyrics) { syncedLines(rawLyrics) }

    StyleFrame(
        style = PlayerStyle.LIQUID,
        preview = preview,
        modifier = modifier,
        tones = tones,
        background = {
            LiquidBackdrop(
                cover = cover,
                video = lookup.video,
                playing = playing && shown,
                preview = preview,
                time = time,
                intro = { intro.value },
            )
        },
    ) { landscape, menuOpen, setMenuOpen, rotated, setRotated ->
        val insets = styleInsets(landscape)
        var controlsOn by remember { mutableStateOf(true) }
        var touched by remember { mutableLongStateOf(0L) }
        var picking by remember { mutableStateOf(false) }
        LaunchedEffect(controlsOn, touched, menuOpen, picking, playing) {
            if (controlsOn && !menuOpen && !picking && playing && !preview) {
                delay(ControlsHideMs)
                controlsOn = false
            }
        }
        val controlsAlpha by animateFloatAsState(
            targetValue = if (controlsOn || menuOpen || picking || preview) 1f else 0f,
            animationSpec = tween(380),
            label = "liquidControls",
        )

        // taps show or hide the controls; a double tap still plays and pauses
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(connection) {
                    detectTapGestures(
                        onDoubleTap = { connection?.togglePlayPause() },
                        onTap = { controlsOn = !controlsOn; touched++ },
                    )
                },
        )

        val sceneAlpha = { smooth((intro.value - 0.30f) / 0.70f) }

        LiquidClock(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = insets.top + if (preview) 4.dp else 14.dp)
                .graphicsLayer { alpha = sceneAlpha() },
            small = preview,
        )

        LyricsLayer(
            style = lyricStyle,
            graph = graph,
            web = web,
            entries = entries,
            position = position,
            time = time,
            preview = preview,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = sceneAlpha() },
        )

        if (!preview) {
            IntroCard(
                cover = cover,
                title = track.title,
                artist = track.artist,
                progress = { intro.value },
                modifier = Modifier.align(Alignment.Center),
            )

            if (controlsAlpha > 0.01f) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = controlsAlpha }) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 22.dp + insets.start, bottom = 74.dp + insets.bottom),
                    ) {
                        StyleSongs(carousel, track, lyrics) { song, slotModifier ->
                            Column(slotModifier) {
                                Text(
                                    text = song.track.title,
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = song.track.artist,
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    LyricStylePicker(
                        selected = lyricStyle,
                        onSelect = { lyricStyle = it; touched++ },
                        open = picking,
                        onOpenChange = { picking = it; touched++ },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 22.dp + insets.start, bottom = 22.dp + insets.bottom),
                    )
                    StyleControls(tones, landscape, insets, menuOpen, setMenuOpen, actions, rotated, setRotated, menuTop = insets.top + 20.dp)
                }
            }
        }
    }
}

// ---- the picture under everything ---------------------------------------------------------

/**
 * The song's video if it has one, else its cover, kept sharp and whole. A few thick patches of
 * liquid ([LiquidFx]) drift over it, smearing only what they cover; the picture itself barely
 * moves. A new song starts melted all over and the liquid draws back into its patches as the
 * first picture settles.
 */
@Composable
private fun LiquidBackdrop(
    cover: String?,
    video: com.vantra.music.ui.player.SongVideo?,
    playing: Boolean,
    preview: Boolean,
    time: () -> Float,
    intro: () -> Float,
) {
    val fx = remember { LiquidFx() }
    Box(Modifier.fillMaxSize().background(Color(0xFF14111A))) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = time()
                    val k = intro()
                    val w = Tau / LoopSeconds
                    // a breath of drift, never still: every number turns a whole number of times in the 100 s loop
                    val s = 1.05f + 0.012f * sin(t * w * 5f) + (1f - k) * 0.10f
                    scaleX = s
                    scaleY = s
                    translationX = size.width * 0.010f * sin(t * w * 3f)
                    translationY = size.height * 0.010f * cos(t * w * 4f)
                    // a new effect on every frame, so the liquid flows instead of stepping
                    renderEffect = fx.build(size.width, size.height, t, LiquidBlurDp.dp.toPx(), 1f - k)
                },
        ) {
            Crossfade(targetState = cover, animationSpec = tween(1100), label = "liquidCover") { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (!preview && video != null) {
                YouTubeVideoBackground(
                    video = video,
                    isPlaying = playing,
                    glassState = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // phones without the shader get plain frosted patches in the same places
        if (Build.VERSION.SDK_INT < 33) FrostPatches(time)
        LiquidEdgeLight(time)
    }
}

/** frosted patches over the picture, on the paths the liquid takes, for Android 12 and older */
@Composable
private fun FrostPatches(time: () -> Float) {
    val blob = remember { FloatArray(3) }
    Canvas(Modifier.fillMaxSize()) {
        val t = time()
        val aspect = size.height / size.width
        for (i in 0 until LiquidBlobCount) {
            liquidBlob(i, t, aspect, blob)
            val center = Offset(blob[0] * size.width, blob[1] * size.width)
            val radius = blob[2] * size.width * 1.15f
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0x2EFFFFFF),
                    0.7f to Color(0x14FFFFFF),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}

/**
 * The light the screen throws: violet along the edges (strongest at the bottom), a thin pale rim,
 * and a light shade so the words stay readable on a bright picture. It breathes slowly.
 */
@Composable
private fun LiquidEdgeLight(time: () -> Float) {
    Canvas(Modifier.fillMaxSize()) {
        val w = Tau / LoopSeconds
        val breathe = 0.88f + 0.12f * sin(time() * w * 4f)
        drawRect(
            Brush.verticalGradient(
                0f to Color(0x14000000),
                0.5f to Color.Transparent,
                1f to Color(0x59000000),
            ),
        )
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.80f to Color.Transparent,
                1f to Violet.copy(alpha = 0.58f * breathe),
            ),
        )
        drawRect(
            Brush.verticalGradient(
                0f to Violet.copy(alpha = 0.24f * breathe),
                0.08f to Color.Transparent,
            ),
        )
        drawRect(
            Brush.horizontalGradient(
                0f to Violet.copy(alpha = 0.36f * breathe),
                0.13f to Color.Transparent,
                0.87f to Color.Transparent,
                1f to Violet.copy(alpha = 0.36f * breathe),
            ),
        )
        drawRect(Color.White.copy(alpha = 0.16f * breathe), style = Stroke(width = 3.dp.toPx()))
    }
}

// ---- the small cover card each song starts as --------------------------------------------

@Composable
private fun IntroCard(
    cover: String?,
    title: String,
    artist: String,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val active by remember(progress) { derivedStateOf { progress() < 0.999f } }
    if (!active) return
    Column(
        modifier = modifier.graphicsLayer {
            val p = progress()
            val grow = 1f + p * 1.9f
            scaleX = grow
            scaleY = grow
            alpha = (1f - p * 1.55f).coerceIn(0f, 1f)
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(116.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp).width(116.dp),
        )
        Text(
            text = artist,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(116.dp),
        )
    }
}

// ---- the clock ----------------------------------------------------------------------------

@Composable
private fun LiquidClock(modifier: Modifier, small: Boolean) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 50L)
        }
    }
    val date = remember(now) { now.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())) }
    val clock = remember(now) { now.format(DateTimeFormatter.ofPattern("HH:mm")) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = date,
            color = Cream.copy(alpha = 0.62f),
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = if (small) 6.sp else 13.sp,
        )
        Text(
            text = clock,
            color = Cream.copy(alpha = 0.52f),
            fontFamily = FontFamily.Serif,
            fontSize = if (small) 22.sp else 54.sp,
            style = TextStyle(shadow = Shadow(Color(0x40000000), Offset(0f, 3f), 10f)),
        )
    }
}

// ---- the lyric look picker -----------------------------------------------------------------

@Composable
private fun LyricStylePicker(
    selected: LiquidLyricStyle,
    onSelect: (LiquidLyricStyle) -> Unit,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (open) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LiquidLyricStyle.entries.forEach { option ->
                    val on = option == selected
                    Text(
                        text = option.title,
                        color = if (on) Color.Black else Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (on) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.18f))
                            .clickable { onSelect(option) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .clickable { onOpenChange(!open) }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Aa", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(selected.title, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
        }
    }
}

// ---- the words ---------------------------------------------------------------------------

@Composable
private fun LyricsLayer(
    style: LiquidLyricStyle,
    graph: CGraph,
    web: List<IntArray>,
    entries: List<LyricsEntry>,
    position: LivePosition,
    time: () -> Float,
    preview: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 96)
    // lines need synced lyrics; without them every look falls back to the star map, which drifts the title and artist through the stars
    val look = if (style != LiquidLyricStyle.CONSTELLATION && entries.isEmpty()) LiquidLyricStyle.CONSTELLATION else style
    val scale = if (preview) 0.42f else 1f
    Canvas(modifier) {
        val pos = if (preview) 9_000L else position.positionMs()
        when (look) {
            LiquidLyricStyle.CONSTELLATION -> drawStarMap(graph, web, pos, measurer, scale, time())
            LiquidLyricStyle.KINETIC -> drawKinetic(entries, pos, measurer, scale)
            LiquidLyricStyle.KARAOKE -> drawKaraoke(entries, pos, measurer, scale)
            LiquidLyricStyle.MINIMAL -> drawMinimal(entries, pos, measurer, scale)
        }
    }
}

private fun lyricText(sizeSp: Float, weight: FontWeight, color: Color = Color.White, shadow: Shadow? = null) = TextStyle(
    fontFamily = InterFontFamily,
    fontSize = sizeSp.sp,
    fontWeight = weight,
    letterSpacing = (-0.5).sp,
    color = color,
    shadow = shadow,
)

private fun smooth(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun easeOut(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return 1f - (1f - x) * (1f - x) * (1f - x)
}

/** the star map: every word a node with a crosshair, joined by thin threads to the words before it */
private fun DrawScope.drawStarMap(
    graph: CGraph,
    web: List<IntArray>,
    pos: Long,
    measurer: TextMeasurer,
    fontScale: Float,
    timeSec: Float,
) {
    val words = graph.words
    if (words.isEmpty()) return
    var lo = 0
    var hi = words.lastIndex
    var cur = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (words[mid].startMs <= pos) { cur = mid; lo = mid + 1 } else hi = mid - 1
    }
    if (cur < 0) return
    val first = max(0, cur - StarWindow)
    // the stars fade out in a long instrumental, and the map fades back with the next word
    val gap = (pos - words[cur].endMs).coerceAtLeast(0L)
    val idle = 1f - ((gap - 2500f) / 3000f).coerceIn(0f, 1f)
    if (idle <= 0f) return

    fun alphaOf(j: Int): Float {
        val age = cur - j
        return if (age == 0) 1f else (1f - age / (StarWindow + 1f)).pow(1.3f) * 0.85f
    }
    fun ease(j: Int): Float = easeOut((pos - words[j].startMs) / 420f)
    fun at(j: Int) = Offset(words[j].x * size.width, words[j].y * size.height)

    val w = Tau / LoopSeconds
    // the whole map breathes a little, so it never sits dead still
    val pan = Offset(sin(timeSec * w * 3f) * size.width * 0.012f, cos(timeSec * w * 2f) * size.height * 0.012f)
    withTransform({ translate(pan.x, pan.y) }) {
        val hair = 1.0.dp.toPx() * (if (fontScale < 1f) 0.6f else 1f)
        for (j in first..cur) {
            val e = ease(j)
            for (k in web[j]) {
                if (k < first) continue
                val a = at(k)
                val b = at(j)
                val end = Offset(a.x + (b.x - a.x) * e, a.y + (b.y - a.y) * e)
                drawLine(Color.White.copy(alpha = min(alphaOf(k), alphaOf(j)) * 0.62f * idle), a, end, strokeWidth = hair)
            }
        }
        val small = lyricText(11f * fontScale, FontWeight.Medium)
        for (j in first until cur) {
            val p = at(j)
            val al = alphaOf(j) * idle
            drawNode(p, 6.dp.toPx() * fontScale.coerceAtLeast(0.6f), al, hair)
            val layout = measurer.measure(words[j].text, small)
            val x = (p.x + 9.dp.toPx() * fontScale).coerceAtMost(size.width - layout.size.width - 8.dp.toPx())
            val y = (p.y - 6.dp.toPx() * fontScale - layout.size.height / 2f).coerceAtLeast(4.dp.toPx())
            drawText(layout, color = Color.White.copy(alpha = al * 0.9f), topLeft = Offset(x, y))
        }

        // the word being sung: a bright node, and the word itself big and bold beside it
        val e = ease(cur)
        val c = at(cur)
        val glowR = 34.dp.toPx() * fontScale
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.30f * e * idle), Color.Transparent), center = c, radius = glowR),
            radius = glowR,
            center = c,
        )
        drawNode(c, (6f + 4f * e).dp.toPx() * fontScale.coerceAtLeast(0.6f), idle, hair * 1.3f)
        val big = lyricText(
            30f * fontScale + 6f * fontScale * e,
            FontWeight.ExtraBold,
            shadow = Shadow(Color(0x66FFFFFF), Offset.Zero, 22f * fontScale),
        )
        val layout = measurer.measure(words[cur].text, big)
        val margin = 14.dp.toPx()
        val x = (c.x - layout.size.width / 2f).coerceIn(margin, max(margin, size.width - layout.size.width - margin))
        val below = c.y + 14.dp.toPx() * fontScale
        val y = if (below + layout.size.height > size.height - 70.dp.toPx()) c.y - 14.dp.toPx() * fontScale - layout.size.height else below
        val s = 0.9f + 0.1f * e
        withTransform({ scale(s, s, Offset(x + layout.size.width / 2f, y + layout.size.height / 2f)) }) {
            drawText(layout, color = Color.White.copy(alpha = (0.2f + 0.8f * e) * idle), topLeft = Offset(x, y))
        }
    }
}

/** a ring with a small plus inside: the node the lock-screen's star map pins each word on */
private fun DrawScope.drawNode(c: Offset, r: Float, alpha: Float, stroke: Float) {
    val color = Color.White.copy(alpha = alpha)
    drawCircle(color, radius = r, center = c, style = Stroke(stroke))
    val a = r * 0.55f
    drawLine(color, Offset(c.x - a, c.y), Offset(c.x + a, c.y), stroke)
    drawLine(color, Offset(c.x, c.y - a), Offset(c.x, c.y + a), stroke)
}

/** every word also joins the nearest one or two before it, so the threads make a web and not a chain */
private fun webLinks(graph: CGraph): List<IntArray> {
    val w = graph.words
    return List(w.size) { j ->
        val base = graph.links[j]
        var best = -1
        var bestD = Float.MAX_VALUE
        var second = -1
        var secondD = Float.MAX_VALUE
        for (k in max(0, j - 8) until j) {
            if (base.contains(k)) continue
            val dx = w[k].x - w[j].x
            val dy = (w[k].y - w[j].y) * 0.6f
            val d = dx * dx + dy * dy
            if (d < bestD) {
                second = best; secondD = bestD
                best = k; bestD = d
            } else if (d < secondD) {
                second = k; secondD = d
            }
        }
        val extra = ArrayList<Int>(2)
        if (best >= 0 && bestD < 0.09f) extra.add(best)
        if (second >= 0 && secondD < 0.05f) extra.add(second)
        if (extra.isEmpty()) base else base + extra.toIntArray()
    }
}

// ---- the line looks -------------------------------------------------------------------------

/** the lines of a synced lyric; empty for no lyrics or for lyrics with no times, which only the star map can set */
private fun syncedLines(raw: String?): List<LyricsEntry> {
    if (raw.isNullOrBlank() || raw == LYRICS_NOT_FOUND) return emptyList()
    val parsed = runCatching { LyricsUtils.parseLyricsCached(raw) }.getOrDefault(emptyList())
    if (parsed.isEmpty() || LyricsUtils.isUnsynced(parsed)) return emptyList()
    return parsed.filter { it.text.isNotBlank() }
}

private fun lineAt(lines: List<LyricsEntry>, pos: Long): Int {
    var lo = 0
    var hi = lines.lastIndex
    var cur = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lines[mid].time <= pos) { cur = mid; lo = mid + 1 } else hi = mid - 1
    }
    return cur
}

private fun lineEnd(lines: List<LyricsEntry>, i: Int): Long =
    min(lines.getOrNull(i + 1)?.time ?: (lines[i].time + 5_000L), lines[i].time + 9_000L)

private fun DrawScope.layoutLine(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidthFraction: Float,
    align: TextAlign = TextAlign.Center,
): TextLayoutResult = measurer.measure(
    text = text,
    style = style.copy(textAlign = align),
    constraints = Constraints(maxWidth = (size.width * maxWidthFraction).toInt().coerceAtLeast(1)),
)

/** the sung line big in the middle with its words rising in one by one, the last line floating away above it */
private fun DrawScope.drawKinetic(lines: List<LyricsEntry>, pos: Long, measurer: TextMeasurer, fontScale: Float) {
    val cur = lineAt(lines, pos)
    if (cur < 0) return
    val line = lines[cur]
    val end = lineEnd(lines, cur)
    val sinceStart = (pos - line.time).coerceAtLeast(0L)
    val idle = 1f - ((pos - end - 1500f) / 2500f).coerceIn(0f, 1f)
    val style = lyricText(34f * fontScale, FontWeight.ExtraBold)
    val layout = layoutLine(measurer, line.text, style, 0.78f)
    val origin = Offset((size.width - layout.size.width) / 2f, (size.height - layout.size.height) / 2f + 10.dp.toPx())

    // the line before it, fading up and out as this one arrives
    if (cur > 0) {
        val out = easeOut(sinceStart / 600f)
        if (out < 1f) {
            val prev = layoutLine(measurer, lines[cur - 1].text, lyricText(34f * fontScale, FontWeight.ExtraBold), 0.78f)
            drawText(
                prev,
                color = Color.White.copy(alpha = (1f - out) * 0.35f),
                topLeft = Offset((size.width - prev.size.width) / 2f, origin.y - 34.dp.toPx() * out - prev.size.height),
            )
        }
    }

    // each word has a moment: its own timing when the lyrics carry it, otherwise spread over the line by length
    val ranges = Regex("\\S+").findAll(line.text).map { it.range }.toList()
    val timed = line.words?.filter { it.text.isNotBlank() }.orEmpty()
    val total = ranges.sumOf { it.last - it.first + 3 }.coerceAtLeast(1)
    var used = 0
    for ((i, r) in ranges.withIndex()) {
        val word = line.text.substring(r.first, r.last + 1)
        val startMs = if (timed.size == ranges.size) (timed[i].startTime * 1000.0).toLong()
        else line.time + (end - line.time) * used / total
        used += r.last - r.first + 3
        val e = easeOut((pos - startMs) / 380f)
        val box = layout.getBoundingBox(r.first)
        val wl = measurer.measure(word, style)
        val rise = (1f - e) * 18.dp.toPx()
        val sung = if (pos >= startMs) 1f else 0f
        withTransform({
            scale(0.92f + 0.08f * e, 0.92f + 0.08f * e, Offset(origin.x + box.left + wl.size.width / 2f, origin.y + box.top + rise + wl.size.height / 2f))
        }) {
            drawText(
                wl,
                color = Color.White.copy(alpha = (0.2f + 0.8f * e * sung) * idle),
                topLeft = Offset(origin.x + box.left, origin.y + box.top + rise),
            )
        }
    }
}

/** two lines low on the screen, the sung one filling with light from the left */
private fun DrawScope.drawKaraoke(lines: List<LyricsEntry>, pos: Long, measurer: TextMeasurer, fontScale: Float) {
    val cur = lineAt(lines, pos)
    if (cur < 0) return
    val end = lineEnd(lines, cur)
    val idle = 1f - ((pos - end - 1500f) / 2500f).coerceIn(0f, 1f)
    val progress = ((pos - lines[cur].time).toFloat() / (end - lines[cur].time).coerceAtLeast(1L)).coerceIn(0f, 1f)
    val style = lyricText(28f * fontScale, FontWeight.Bold)
    val left = size.width * 0.09f
    val layout = layoutLine(measurer, lines[cur].text, style, 0.8f, TextAlign.Start)
    val top = size.height * 0.56f
    drawText(layout, color = Color.White.copy(alpha = 0.32f * idle), topLeft = Offset(left, top))
    // the light fills the layout line by line, left to right
    var widthTotal = 0f
    for (i in 0 until layout.lineCount) widthTotal += layout.getLineRight(i) - layout.getLineLeft(i)
    var remaining = widthTotal * progress
    for (i in 0 until layout.lineCount) {
        val lineW = layout.getLineRight(i) - layout.getLineLeft(i)
        val take = min(lineW, remaining)
        if (take <= 0f) break
        clipRect(
            left = left + layout.getLineLeft(i),
            top = top + layout.getLineTop(i),
            right = left + layout.getLineLeft(i) + take,
            bottom = top + layout.getLineBottom(i),
        ) {
            drawText(layout, color = Color.White.copy(alpha = idle), topLeft = Offset(left, top))
        }
        remaining -= take
    }
    lines.getOrNull(cur + 1)?.let { next ->
        val nextLayout = layoutLine(measurer, next.text, lyricText(22f * fontScale, FontWeight.SemiBold), 0.8f, TextAlign.Start)
        drawText(nextLayout, color = Color.White.copy(alpha = 0.26f * idle), topLeft = Offset(left, top + layout.size.height + 14.dp.toPx()))
    }
}

/** one quiet line at a time; the next melts in from below as the last drifts up and away */
private fun DrawScope.drawMinimal(lines: List<LyricsEntry>, pos: Long, measurer: TextMeasurer, fontScale: Float) {
    val cur = lineAt(lines, pos)
    if (cur < 0) return
    val end = lineEnd(lines, cur)
    val idle = 1f - ((pos - end - 1500f) / 2500f).coerceIn(0f, 1f)
    val e = easeOut((pos - lines[cur].time) / 650f)
    val style = lyricText(26f * fontScale, FontWeight.SemiBold)
    val layout = layoutLine(measurer, lines[cur].text, style, 0.74f)
    val cy = size.height * 0.56f
    if (cur > 0 && e < 1f) {
        val prev = layoutLine(measurer, lines[cur - 1].text, style, 0.74f)
        drawText(
            prev,
            color = Color.White.copy(alpha = (1f - e) * 0.45f),
            topLeft = Offset((size.width - prev.size.width) / 2f, cy - prev.size.height / 2f - 26.dp.toPx() * e),
        )
    }
    drawText(
        layout,
        color = Color.White.copy(alpha = e * idle),
        topLeft = Offset((size.width - layout.size.width) / 2f, cy - layout.size.height / 2f + 26.dp.toPx() * (1f - e)),
    )
}
