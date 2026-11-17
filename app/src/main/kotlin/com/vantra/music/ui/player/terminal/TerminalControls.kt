package com.vantra.music.ui.player.terminal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vantra.music.LocalPlayerConnection
import com.vantra.music.R
import com.vantra.music.extensions.toggleRepeatMode
import com.vantra.music.ui.player.styles.StyleActions
import com.vantra.music.ui.player.styles.StyleInsets
import com.vantra.music.ui.player.styles.StyleMenu
import com.vantra.music.ui.player.styles.StyleTones

/** Native, labelled 48dp targets. Menu stays in the rotated content tree instead of an unrotated
 * popup window. Every action delegates to the same player/StyleActions as the original controls.
 */
@Composable
internal fun TerminalControls(landscape:Boolean, insets:StyleInsets, menuOpen:Boolean,
    setMenuOpen:(Boolean)->Unit, actions:StyleActions, onRotate:()->Unit) {
    val connection=LocalPlayerConnection.current
    val transport=connection?.uiState?.transportState?.collectAsState()?.value
    BackHandler(enabled=menuOpen) { setMenuOpen(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val vertical=landscape && maxHeight >= 240.dp
        val buttons: @Composable ()->Unit = {
            TerminalControl(R.drawable.skip_previous,"Previous track",transport?.canSkipPrevious == true) {
                connection?.seekToPrevious()
            }
            TerminalControl(if(transport?.isPlaying == true) R.drawable.pause else R.drawable.play,
                if(transport?.isPlaying == true) "Pause" else "Play",connection != null) {
                if(transport?.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    connection?.player?.seekTo(0,0)
                    connection?.player?.playWhenReady=true
                } else connection?.togglePlayPause()
            }
            TerminalControl(R.drawable.skip_next,"Next track",transport?.canSkipNext == true) {
                connection?.seekToNext()
            }
        }
        Box(Modifier.align(if(landscape) Alignment.BottomEnd else Alignment.BottomCenter)
            .padding(end=if(landscape) insets.end+16.dp else 0.dp,
                bottom=if(landscape) 16.dp else insets.bottom+28.dp)) {
            if(vertical) Column(verticalArrangement=Arrangement.spacedBy(4.dp)) { buttons() }
            else Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { buttons() }
        }
        // the same round-button menu the Bedside style opens: a label beside each circle, rising
        // out of the three dots, with the screen dimmed behind it
        val tones=remember {
            StyleTones(played=Color.Black,remaining=Color.Black,ink=Color.White,
                inkSoft=Color.White.copy(alpha=0.62f),chip=Color.White.copy(alpha=0.12f))
        }
        StyleMenu(
            open=menuOpen,
            onOpenChange=setMenuOpen,
            tones=tones,
            actions=actions,
            rotated=landscape,
            onRotate={ onRotate() },
            atBottom=false,
            end=insets.end+16.dp+4.dp,
            edge=if(landscape) 12.dp+4.dp else insets.top+12.dp+4.dp,
            buttonSize=40.dp,
            showButton=false,
            pill=true,
        )
        Box(Modifier.align(Alignment.TopEnd).padding(end=insets.end+16.dp,
            top=if(landscape) 12.dp else insets.top+12.dp)) {
            TerminalControl(R.drawable.more_horiz,if(menuOpen) "Close menu" else "Open menu",true) { setMenuOpen(!menuOpen) }
        }
    }
}

@Composable
private fun TerminalControl(icon:Int,label:String,enabled:Boolean,onClick:()->Unit) {
    IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(48.dp)) {
        Icon(painterResource(icon),label,tint=Color.White.copy(alpha=if(enabled) 0.92f else 0.35f),modifier=Modifier.size(24.dp))
    }
}
