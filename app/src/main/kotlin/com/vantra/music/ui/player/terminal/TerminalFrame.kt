package com.vantra.music.ui.player.terminal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.vantra.music.LocalPlayerConnection
import com.vantra.music.ui.player.styles.LocalStyleInteractive
import com.vantra.music.ui.player.styles.StyleInsets
import com.vantra.music.ui.player.styles.TurnedFlingFix
import com.vantra.music.ui.player.styles.quarterTurn
import com.vantra.music.ui.player.styles.rememberStyleCarousel
import com.vantra.music.ui.player.styles.styleGestures
import com.vantra.music.ui.player.styles.turnFit

/** Exactly one content tree during a turn: renderer, camera, listener and frame loop stay owned
 * by the same composition. The shared two-layer frame remains untouched for every other style.
 */
@Composable
internal fun TerminalFrame(rotated: Boolean, preview: Boolean, modifier: Modifier,
    content: @Composable BoxScope.(Boolean, Boolean, (Boolean) -> Unit) -> Unit) {
    val connection = LocalPlayerConnection.current
    var menuOpen by remember { mutableStateOf(false) }
    val angle = animateFloatAsState(if (rotated) 90f else 0f, tween(360), label="terminalTurn")
    TurnedFlingFix(active=rotated && !preview)
    Box(modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().quarterTurn(rotated).graphicsLayer {
            val rest = if (rotated) 90f else 0f
            rotationZ = angle.value - rest
            val fit = turnFit(size.width, size.height, rest, rotationZ)
            scaleX = fit; scaleY = fit
        }.then(if (preview) Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        } else Modifier).styleGestures(enabled=!preview && connection != null,
            interactive=LocalStyleInteractive.current, onToggle={connection?.togglePlayPause()},
            carousel=rememberStyleCarousel(preview), player={connection?.player})) {
            androidx.compose.material3.ProvideTextStyle(androidx.compose.ui.text.TextStyle(
                fontFamily=com.vantra.music.ui.theme.InterFontFamily)) {
                content(rotated, menuOpen) { menuOpen = it }
            }
        }
    }
}

/** Stable system bars + current cutouts/mandatory gestures mapped into the rotated local axes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun terminalInsets(rotated: Boolean): StyleInsets {
    val density=LocalDensity.current
    val rtl=LocalLayoutDirection.current == LayoutDirection.Rtl
    val safe=WindowInsets.safeDrawing.union(WindowInsets.mandatorySystemGestures)
        .union(WindowInsets.systemBarsIgnoringVisibility)
    val e=TerminalPresentation.insets(safe.getLeft(density,LayoutDirection.Ltr).toFloat(),
        safe.getTop(density).toFloat(),safe.getRight(density,LayoutDirection.Ltr).toFloat(),
        safe.getBottom(density).toFloat(),rotated,rtl)
    return with(density) { StyleInsets(e.start.toDp(),e.top.toDp(),e.end.toDp(),e.bottom.toDp()) }
}

@Composable
internal fun rememberTerminalTouchExploration(): Boolean {
    val context=LocalContext.current
    val manager=remember(context) { context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE)
        as? android.view.accessibility.AccessibilityManager }
    var active by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener=android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener { active=it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return active
}
