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
        if(menuOpen) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.72f))
                .semantics { contentDescription="Close Terminal menu" }.clickable { setMenuOpen(false) })
            Column(Modifier.align(Alignment.TopEnd).padding(end=insets.end+16.dp,
                top=if(landscape) 68.dp else insets.top+68.dp)
                .width(224.dp).heightIn(max=(maxHeight-100.dp).coerceAtLeast(48.dp))
                .background(Color(0xFF111315),RoundedCornerShape(4.dp))
                .border(1.dp,Color(0xFF30363D),RoundedCornerShape(4.dp))
                .verticalScroll(rememberScrollState())) {
                @Composable fun entry(label:String,icon:Int,close:Boolean=true,action:()->Unit) {
                    DropdownMenuItem(text={Text(label,color=Color.White)},
                        leadingIcon={Icon(painterResource(icon),null,tint=Color.White,modifier=Modifier.size(20.dp))},
                        onClick={if(close) setMenuOpen(false);action()},modifier=Modifier.heightIn(min=48.dp))
                }
                actions.onLyrics?.let { open -> entry("Lyrics",R.drawable.lyrics,action=open) }
                if(connection != null && transport != null) {
                    entry(if(transport.shuffleModeEnabled) "Shuffle: on" else "Shuffle: off",R.drawable.shuffle,false) {
                        connection.player.shuffleModeEnabled=!connection.player.shuffleModeEnabled
                    }
                    entry(when(transport.repeatMode) { androidx.media3.common.Player.REPEAT_MODE_ONE -> "Repeat: one"
                        androidx.media3.common.Player.REPEAT_MODE_ALL -> "Repeat: all"; else -> "Repeat: off" },
                        if(transport.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat,false) {
                        connection.player.toggleRepeatMode()
                    }
                }
                entry("Other menu",R.drawable.more_horiz,action=actions.onOtherMenu)
                entry(if(landscape) "Turn upright" else "Rotate",R.drawable.screen_rotation,action=onRotate)
                entry("Edit player",R.drawable.tune,action=actions.onEditPlayer)
            }
        }
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
