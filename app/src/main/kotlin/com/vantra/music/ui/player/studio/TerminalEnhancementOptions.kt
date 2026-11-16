package com.vantra.music.ui.player.studio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vantra.music.LocalPlayerConnection
import com.vantra.music.constants.TerminalTypingSpeed
import com.vantra.music.constants.TerminalTypingSpeedKey
import com.vantra.music.constants.TerminalBassResponse
import com.vantra.music.constants.TerminalBassResponseKey
import com.vantra.music.constants.TerminalBeatSync
import com.vantra.music.constants.TerminalBeatSyncKey
import com.vantra.music.constants.TerminalFlashMode
import com.vantra.music.constants.TerminalFlashModeKey
import com.vantra.music.constants.TerminalShotMode
import com.vantra.music.constants.TerminalShotModeKey
import com.vantra.music.constants.TerminalEmotionalZoom
import com.vantra.music.constants.TerminalEmotionalZoomKey
import com.vantra.music.constants.TerminalEmphasisMarkersKey
import com.vantra.music.ui.player.terminal.TerminalEmphasis
import com.vantra.music.ui.player.terminal.TerminalMarkerPreferences
import com.vantra.music.utils.rememberEnumPreference
import com.vantra.music.utils.rememberPreference

/** Part of the existing Terminal OptionSheet, not another settings destination. */
@Composable
internal fun ColumnScope.TerminalEnhancementOptions() {
    var speed by rememberEnumPreference(TerminalTypingSpeedKey,TerminalTypingSpeed.NORMAL)
    var bass by rememberEnumPreference(TerminalBassResponseKey,TerminalBassResponse.NORMAL)
    var zoom by rememberEnumPreference(TerminalEmotionalZoomKey,TerminalEmotionalZoom.NORMAL)
    var shots by rememberEnumPreference(TerminalShotModeKey,TerminalShotMode.CINEMATIC)
    var flash by rememberEnumPreference(TerminalFlashModeKey,TerminalFlashMode.FULL)
    var sync by rememberEnumPreference(TerminalBeatSyncKey,TerminalBeatSync.EXACT)
    var raw by rememberPreference(TerminalEmphasisMarkersKey,defaultValue="{}")
    val connection=LocalPlayerConnection.current
    val metadata=connection?.mediaMetadata?.collectAsState()?.value
    var editorId by remember { mutableStateOf<String?>(null) }
    var editorTitle by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    TerminalTypingSpeed.entries.forEachIndexed { i,v ->
        OptionRow(selected=speed==v,onClick={speed=v},caption=if(i==0) "Typing speed" else null,compact=true) { TwoLines(v.title,v.description) }
    }
    TerminalBassResponse.entries.forEachIndexed { i,v ->
        OptionRow(selected=bass==v,onClick={bass=v},caption=if(i==0) "Bass response" else null,compact=true) { TwoLines(v.title,v.description) }
    }
    TerminalShotMode.entries.forEachIndexed { i,v ->
        OptionRow(selected=shots==v,onClick={shots=v},caption=if(i==0) "Camera shots" else null,compact=true) { TwoLines(v.title,v.description) }
    }
    TerminalFlashMode.entries.forEachIndexed { i,v ->
        OptionRow(selected=flash==v,onClick={flash=v},caption=if(i==0) "Beat flash (phonk / montagem)" else null,compact=true) { TwoLines(v.title,v.description) }
    }
    TerminalBeatSync.entries.forEachIndexed { i,v ->
        OptionRow(selected=sync==v,onClick={sync=v},caption=if(i==0) "Beat sync" else null,compact=true) { TwoLines(v.title,v.description) }
    }
    TerminalEmotionalZoom.entries.forEachIndexed { i,v ->
        OptionRow(selected=zoom==v,onClick={zoom=v},caption=if(i==0) "Emotional zoom (manual markers)" else null,compact=true) { TwoLines(v.title,v.description) }
    }
    val id=metadata?.id
    if(id!=null) {
        OptionRow(selected=false,onClick={editorId=id;editorTitle=metadata?.title.orEmpty();text=TerminalMarkerPreferences.text(raw,id);error=null},compact=true) {
            TwoLines("Edit emphasis markers","Current track · no automatic emotion guesses")
        }
    } else Text("Play a track to edit its emphasis markers",color=Color.LightGray)
    // Inline editor stays inside the existing rotated/scrollable OptionSheet.
    if(editorId!=null) Column(Modifier.fillMaxWidth()) {
        Text("Emphasis: $editorTitle",color=Color.White)
        Text("Time = focus peak on the lyric clock. One marker per line:\nmm:ss.sss | push / power / gentle / still / climax | 0..1.5 | duration 600..8000 ms",color=Color.LightGray)
        OutlinedTextField(value=text,onValueChange={if(it.length<=24000) {text=it;error=null}},
            modifier=Modifier.fillMaxWidth().heightIn(min=160.dp,max=300.dp),
            textStyle=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.Monospace),
            placeholder={Text("00:12.500 | push | 1.0 | 2400",color=Color.Gray)},
            colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,
                focusedBorderColor=Color.White,unfocusedBorderColor=Color.Gray,cursorColor=Color.White))
        error?.let {Text(it,color=Color.White)}
        Text("Empty text removes markers for this track. No markers = normal camera.",color=Color.LightGray)
        Row {
            TextButton(onClick={
                val checked=TerminalEmphasis.parse(text)
                if(checked.error!=null) error=checked.error else {
                    val target=editorId
                    if(target!=null) runCatching {TerminalMarkerPreferences.save(raw,target,text)}
                        .onSuccess {raw=it;editorId=null}.onFailure {error=it.message ?: "Cannot save markers"}
                }
            }) {Text("Save",color=Color.White)}
            TextButton(onClick={editorId=null}) {Text("Cancel",color=Color.White)}
        }
    }
}
