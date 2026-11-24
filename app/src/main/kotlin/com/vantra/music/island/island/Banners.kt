package com.vantra.music.island.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vantra.music.R
import com.vantra.music.island.core.Controls
import com.vantra.music.island.core.IslandBus
import com.vantra.music.island.core.Notice
import com.vantra.music.island.core.Tints
import com.vantra.music.island.core.tr
import com.vantra.music.island.data.IslandSettings
import kotlinx.coroutines.launch

/** Чем писать на сиреневых кнопках — тёмным, как в ролике. */
private val Ink = Color(0xFF1C1B22)

/**
 * Ответ ушёл — как в ролике: сиреневый кружок с галочкой и «Отправлено», справа — кому.
 * Показывается коротко (половина «Показа событий») и сразу уступает место.
 */
@Composable
fun SentBanner(n: Notice, s: IslandSettings, wideWidth: Dp) {
    val height = noticeHeight(s)
    val pop = remember(n.id) { Animatable(if (s.reduceEffects) 1f else 0.4f) }
    LaunchedEffect(n.id) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f)) }
    Row(
        Modifier.width(wideWidth).height(height).padding(horizontal = height * 0.32f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(height * 0.42f).scale(pop.value).clip(CircleShape).background(n.tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ImageVector.vectorResource(R.drawable.ios_check), null, tint = onAccent(n.tint), modifier = Modifier.size(height * 0.26f))
            }
            Spacer(Modifier.width(10.dp))
            Text(n.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold.island, maxLines = 1)
        }
        Spacer(Modifier.width(compactGap(s)))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (n.state != null) {
                Text(n.state, color = Tints.Gray, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

