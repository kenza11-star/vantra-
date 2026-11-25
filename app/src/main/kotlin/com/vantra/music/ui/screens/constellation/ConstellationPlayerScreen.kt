package com.vantra.music.ui.screens.constellation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.vantra.music.LocalPlayerConnection
import com.vantra.music.ui.player.PlaybackClock
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

const val ConstellationRoute = "lyric_constellation"

private val Cream = Color(0xFFF6EBD8)
private val Ember = Color(0xFFFFB067)
private const val Window = 15

private tailrec fun Context.constellationActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.constellationActivity()
    else -> null
}

@Composable
fun ConstellationPlayerScreen(navController: NavController) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val view = LocalView.current

    // landscape, bars hidden, screen kept on; everything is put back when the page closes
    DisposableEffect(Unit) {
        val activity = context.constellationActivity()
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            if (previous != null) activity?.requestedOrientation = previous
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }

    val metadata by playerConnection.mediaMetadata.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val isPlaying by playerConnection.isPlaying.collectAsState()

    val title = metadata?.title.orEmpty()
    val artist = metadata?.artists?.joinToString(", ") { it.name }.orEmpty()
    val thumbnail = metadata?.thumbnailUrl
    val durationMs = (metadata?.duration ?: 0) * 1000L

    val graph = remember(lyricsEntity?.lyrics, metadata?.id, durationMs) {
        buildGraph(lyricsEntity?.lyrics, durationMs, listOf(title, artist), (metadata?.id ?: title))
    }

    val clock = remember(playerConnection) {
        PlaybackClock({ playerConnection.player }, { playerConnection.lyricsOffsetMs.value.toLong() })
    }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { frame = it } }

    var controls by remember { mutableStateOf(false) }
    var touch by remember { mutableLongStateOf(0L) }
    var big by remember { mutableStateOf(false) }
    LaunchedEffect(controls, touch) {
        if (controls) {
            delay(4200)
            controls = false
        }
    }

    val textMeasurer = rememberTextMeasurer(cacheSize = 96)
    val fontScale = if (big) 1.3f else 1f

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onTap = { controls = !controls; touch++ }) },
    ) {
        Crossfade(targetState = thumbnail, animationSpec = tween(900), label = "backdrop") { url ->
            LiquidBackdrop(url)
        }

        Canvas(Modifier.fillMaxSize()) {
            if (frame < 0L) return@Canvas
            drawConstellation(graph, clock(), textMeasurer, fontScale)
        }

        ClockBlock(Modifier.align(Alignment.TopCenter).padding(top = 18.dp))

        if (!graph.hasLyrics) {
            Text(
                "♪",
                color = Cream.copy(alpha = 0.35f),
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(22.dp),
            )
        }

        Text(
            "Aa",
            color = Cream.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .clip(CircleShape)
                .clickable { big = !big }
                .padding(horizontal = 8.dp, vertical = 5.dp),
        )

        AsyncImage(
            model = thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 34.dp, bottom = 22.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .clickable { controls = !controls; touch++ },
        )

        AnimatedVisibility(
            visible = controls,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(350)),
            modifier = Modifier.fillMaxSize(),
        ) {
            ControlsOverlay(
                title = title,
                artist = artist,
                isPlaying = isPlaying,
                onClose = { navController.navigateUp() },
                onTouch = { touch++ },
            )
        }
    }
}

@Composable
private fun LiquidBackdrop(url: String?) {
    val t = rememberInfiniteTransition(label = "liquid")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(34000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val softBlur = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(38.dp) else Modifier
    val lightBlur = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(5.dp) else Modifier
    Box(Modifier.fillMaxSize().background(Color(0xFF16121A))) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 1.55f + 0.12f * a
                    scaleX = s
                    scaleY = s
                    translationX = (a - 0.5f) * 90f
                    translationY = (b - 0.5f) * 60f
                    rotationZ = (a - 0.5f) * 6f
                }
                .then(softBlur),
        )
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 0.62f
                    val s = 1.12f + 0.05f * b
                    scaleX = s
                    scaleY = s
                    translationX = (0.5f - b) * 50f
                    translationY = (a - 0.5f) * 36f
                }
                .then(lightBlur),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0x33FFFFFF), Color(0x12FFFFFF), Color(0x66000000)),
                ),
            ),
        )
        // the violet light along the screen edge in the video
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x22B79CFF),
                    0.12f to Color.Transparent,
                    0.84f to Color.Transparent,
                    1f to Color(0x77A98BFF),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color(0x30A98BFF),
                    0.08f to Color.Transparent,
                    0.92f to Color.Transparent,
                    1f to Color(0x30A98BFF),
                ),
            ),
        )
    }
}

@Composable
private fun ClockBlock(modifier: Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 50L)
        }
    }
    val date = remember(now) { now.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())) }
    val time = remember(now) { now.format(DateTimeFormatter.ofPattern("HH:mm")) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            date,
            color = Cream.copy(alpha = 0.6f),
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = 13.sp,
        )
        Text(
            time,
            color = Cream.copy(alpha = 0.5f),
            fontFamily = FontFamily.Serif,
            fontSize = 50.sp,
            style = TextStyle(shadow = Shadow(Color(0x40000000), Offset(0f, 3f), 10f)),
        )
    }
}

@Composable
private fun ControlsOverlay(
    title: String,
    artist: String,
    isPlaying: Boolean,
    onClose: () -> Unit,
    onTouch: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            position = playerConnection.player.currentPosition.coerceAtLeast(0L)
            duration = playerConnection.player.duration.coerceAtLeast(1L)
            delay(250)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0x55000000), Color.Transparent, Color(0xCC000000)),
                ),
            )
            .pointerInput(Unit) { detectTapGestures(onTap = { onTouch() }) },
    ) {
        Icon(
            Icons.Rounded.Close,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose)
                .padding(8.dp),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 40.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Slider(
                value = dragging ?: (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                onValueChange = { dragging = it; onTouch() },
                onValueChangeFinished = {
                    dragging?.let { playerConnection.seekTo((it * duration).toLong()) }
                    dragging = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                ),
                modifier = Modifier.fillMaxWidth(0.7f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.SkipPrevious, null, tint = Color.White,
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable { playerConnection.seekToPrevious(); onTouch() }.padding(8.dp),
                )
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.Black,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { playerConnection.togglePlayPause(); onTouch() }
                        .padding(13.dp),
                )
                Icon(
                    Icons.Rounded.SkipNext, null, tint = Color.White,
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable { playerConnection.seekToNext(); onTouch() }.padding(8.dp),
                )
            }
        }
    }
}

private fun sparkle(c: Offset, r: Float): Path = Path().apply {
    moveTo(c.x, c.y - r)
    quadraticTo(c.x, c.y, c.x + r, c.y)
    quadraticTo(c.x, c.y, c.x, c.y + r)
    quadraticTo(c.x, c.y, c.x - r, c.y)
    quadraticTo(c.x, c.y, c.x, c.y - r)
    close()
}

private fun DrawScope.drawConstellation(
    graph: CGraph,
    pos: Long,
    measurer: androidx.compose.ui.text.TextMeasurer,
    fontScale: Float,
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

    val first = max(0, cur - Window)
    val gap = (pos - words[cur].endMs).coerceAtLeast(0L)
    val idle = 1f - ((gap - 2500f) / 3000f).coerceIn(0f, 1f)
    if (idle <= 0f) return

    fun alphaOf(j: Int): Float {
        val age = cur - j
        return if (age == 0) 1f else (1f - age / (Window + 1f)).pow(1.3f) * 0.85f
    }
    fun ease(j: Int): Float {
        val t = ((pos - words[j].startMs) / 420f).coerceIn(0f, 1f)
        return 1f - (1f - t) * (1f - t) * (1f - t)
    }
    fun at(j: Int) = Offset(words[j].x * size.width, words[j].y * size.height)

    val line = 1.1.dp.toPx()
    // threads between the stars; the newest one grows out of the word before it
    for (j in first..cur) {
        val e = ease(j)
        for (k in graph.links[j]) {
            if (k < first) continue
            val a = at(k)
            val b = at(j)
            val end = Offset(a.x + (b.x - a.x) * e, a.y + (b.y - a.y) * e)
            drawLine(Cream.copy(alpha = min(alphaOf(k), alphaOf(j)) * 0.8f * idle), a, end, strokeWidth = line)
        }
    }

    val small = TextStyle(fontSize = (12 * fontScale).sp, fontWeight = FontWeight.Medium, color = Cream)
    for (j in first until cur) {
        val p = at(j)
        val al = alphaOf(j) * idle
        drawPath(sparkle(p, 4.5.dp.toPx()), Cream.copy(alpha = al))
        val layout = measurer.measure(words[j].text, small)
        val x = (p.x + 7.dp.toPx()).coerceAtMost(size.width - layout.size.width - 8.dp.toPx())
        val y = (p.y - 16.dp.toPx()).coerceAtLeast(4.dp.toPx())
        drawText(layout, color = Cream.copy(alpha = al), topLeft = Offset(x, y))
    }

    // the word being sung
    val e = ease(cur)
    val c = at(cur)
    val glowR = 30.dp.toPx()
    drawCircle(
        brush = Brush.radialGradient(listOf(Ember.copy(alpha = 0.85f * e * idle), Color.Transparent), center = c, radius = glowR),
        radius = glowR,
        center = c,
    )
    drawCircle(Cream.copy(alpha = 0.9f * e * idle), radius = (6 + 9 * e).dp.toPx(), center = c, style = Stroke(1.5.dp.toPx()))
    drawPath(sparkle(c, 11.dp.toPx() * (0.5f + 0.5f * e)), Cream.copy(alpha = idle))

    val bigStyle = TextStyle(
        fontSize = (52 * fontScale).sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1.5).sp,
        color = Cream,
        shadow = Shadow(Color(0x55000000), Offset(0f, 4f), 12f),
    )
    val layout = measurer.measure(words[cur].text, bigStyle)
    val margin = 14.dp.toPx()
    val x = (c.x - layout.size.width / 2f).coerceIn(margin, max(margin, size.width - layout.size.width - margin))
    val below = c.y + 20.dp.toPx()
    val y = if (below + layout.size.height > size.height - 70.dp.toPx()) c.y - 20.dp.toPx() - layout.size.height else below
    val s = 0.88f + 0.12f * e
    withTransform({ scale(s, s, Offset(x + layout.size.width / 2f, y + layout.size.height / 2f)) }) {
        drawText(layout, color = Cream.copy(alpha = (0.2f + 0.8f * e) * idle), topLeft = Offset(x, y))
    }
}
