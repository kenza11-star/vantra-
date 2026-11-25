package com.vantra.music.ui.player.studio

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.vantra.music.constants.LiquidLyricStyle
import com.vantra.music.constants.LiquidLyricStyleKey
import com.vantra.music.utils.rememberEnumPreference

// the Liquid style's sheet: how the song's words are set over the liquid glass
@Composable
internal fun ColumnScope.LiquidOptions() {
    var lyricStyle by rememberEnumPreference(LiquidLyricStyleKey, LiquidLyricStyle.CONSTELLATION)
    OptionSheet(title = "Liquid lyrics") {
        LiquidLyricStyle.entries.forEachIndexed { index, option ->
            OptionRow(
                selected = lyricStyle == option,
                onClick = { lyricStyle = option },
                caption = if (index == 0) "Lyric style" else null,
                compact = true,
            ) { TwoLines(option.title, option.description) }
        }
    }
}
