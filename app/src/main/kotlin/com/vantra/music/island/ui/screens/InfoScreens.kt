package com.vantra.music.island.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vantra.music.R
import com.vantra.music.island.core.Tints
import com.vantra.music.island.core.tr
import com.vantra.music.island.core.trf
import com.vantra.music.island.data.SettingsRepo
import com.vantra.music.island.service.LiveDebug
import com.vantra.music.island.service.NotifDebug
import com.vantra.music.island.service.PayDebug
import com.vantra.music.island.ui.Group
import com.vantra.music.island.ui.GroupItem
import com.vantra.music.island.ui.GroupToggle
import com.vantra.music.island.ui.Page
import com.vantra.music.island.ui.Perms
import com.vantra.music.island.ui.PillAction
import com.vantra.music.island.ui.Pos
import com.vantra.music.island.ui.Screen
import com.vantra.music.island.ui.SectionHeader
import com.vantra.music.island.ui.Tile
import com.vantra.music.island.ui.Ui
import kotlinx.coroutines.launch

@Composable
fun InstructionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Page(tr("Инструкция", "Instructions"), onBack) {
        Step(1, tr("Спец. возможности", "Accessibility"),
            tr(
                "Откройте «Настройки → Спец. возможности → Установленные приложения → Динамический остров» и включите службу. " +
                    "Только такая служба может рисовать поверх строки состояния, рядом с камерой.",
                "Open Settings → Accessibility → Installed apps → Dynamic Island and turn the service on. Only such a " +
                    "service can draw over the status bar, next to the camera.",
            ))
        Step(2, tr("Если переключатель серый (Android 13+)", "If the switch is grayed out (Android 13+)"), restrictedHelp())
        Row { PillAction(tr("Открыть «О приложении»", "Open App info")) { Perms.openAppDetails(context) } }
        Step(3, tr("Доступ к уведомлениям", "Notification access"),
            tr(
                "Нужен для карточек музыки, прогресса загрузок и таймеров/секундомеров Google Часов. " +
                    "Остров не хранит и никуда не отправляет уведомления.",
                "Needed for the music, download progress and Google Clock timer and stopwatch cards. The island doesn't " +
                    "store or send notifications anywhere.",
            ))
        Row { PillAction(tr("Открыть доступ к уведомлениям", "Open notification access")) { Perms.openNotifications(context) } }
        Step(4, tr("Звонки", "Calls"),
            tr(
                "На вкладке «Главная» откройте «Разрешения» → «Звонки» и разрешите доступ к телефону, журналу звонков и контактам. " +
                    "Тогда остров покажет входящий вызов с любой звонилкой, подпишет имя из контактов, " +
                    "а кнопки «ответить» и «сбросить» будут работать.",
                "On the Home tab open Permissions → Calls and allow access to the phone, call log and contacts. Then the " +
                    "island shows incoming calls with any dialer, shows the name from your contacts, and the answer and " +
                    "decline buttons work.",
            ))
        Step(5, tr("Поставьте капсулу над камерой", "Place the capsule over the camera"),
            tr(
                "Вкладка «Оформление» → «Положение и размер». Пока экран открыт, остров красный: " +
                    "сдвиньте его, пока красное не окажется ровно над камерой, подберите ширину и высоту и нажмите «Сохранить».",
                "Appearance tab → Position and size. While the screen is open, the island is red: move it until the red sits " +
                    "exactly over the camera, pick the width and height and tap “Save”.",
            ))
        Step(6, tr("Батарея (Xiaomi, Huawei, Samsung…)", "Battery (Xiaomi, Huawei, Samsung…)"),
            tr(
                "Если остров пропадает, разрешите приложению автозапуск и снимите ограничения батареи — " +
                    "иначе прошивка выгружает службу.",
                "If the island disappears, allow the app to autostart and remove battery restrictions — otherwise the " +
                    "firmware kills the service.",
            ))
        Row {
            PillAction(tr("Батарея", "Battery")) { Perms.openBattery(context) }
            if (Perms.hasAutostart()) {
                Spacer(Modifier.width(8.dp))
                PillAction(tr("Автозапуск", "Autostart")) { Perms.openAutostart(context) }
            }
        }
        Step(7, tr("Ответ на сообщения и оплата", "Replies and payments"),
            tr(
                "Под уведомлением от мессенджера стоят кнопки из самого уведомления. «Ответить» открывает строку ввода прямо в острове. " +
                    "Когда приложение оплаты показывает экран оплаты телефоном, остров показывает «К терминалу», а после оплаты — галочку с суммой. " +
                    "Всё это можно попробовать: «Главная» → «Проверка».",
                "Under a messenger notification you get the notification's own buttons. “Reply” opens an input field right" +
                    " in the island. When a payment app shows its phone payment screen, the island shows “Hold near reader”, " +
                    "and after paying — a check mark with the amount. You can try all of this: Home → Try it.",
            ))
        Step(8, tr("Навигатор", "Navigation"),
            tr(
                "Пока открыт навигатор (2ГИС, Яндекс Навигатор, Карты и др.), остров читает с его экрана расстояние до манёвра, " +
                    "улицу и время прибытия, а стрелку поворота узнаёт по картинке. Экран других приложений не читается. " +
                    "Если после обновления остров пропал, выключите и снова включите его в спец. возможностях.",
                "While a navigation app is open (2GIS, Yandex Navigator, Maps and others), the island reads the distance " +
                    "to the next turn, the street and the arrival time from its screen, and recognizes the turn arrow from the" +
                    " picture. Other apps' screens aren't read. If the island disappears after an update, turn it off and on " +
                    "again in Accessibility.",
            ))
        Step(9, tr("Жесты", "Gestures"),
            tr(
                "Нажатие — раскрыть, потянуть вниз — раскрыть, вверх или касание вне острова — свернуть, " +
                    "толкнуть вбок — следующий/предыдущий трек, нажатие на пузырь — раскрыть вторую активность.",
                "Tap — expand, pull down — expand, pull up or touch outside the island — collapse, push sideways — next or" +
                    " previous track, tap the bubble — expand the second activity.",
            ))
    }
}

@Composable
private fun Step(n: Int, title: String, text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ui.Card).padding(18.dp),
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Ui.Primary), contentAlignment = Alignment.Center) {
            Text("$n", color = Ui.OnPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Ui.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(4.dp))
            Text(text, color = Ui.Sub, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

/** «Помощь»: инструкция, диагностика и системные настройки — отдельно от настроек. */
@Composable
fun AppInfoScreen(open: (Screen) -> Unit) {
    Page(tr("Помощь", "Help")) {
        Group {
            GroupItem(Pos.First, Icons.Rounded.Lightbulb, tr("Инструкция", "Instructions"),
                tr("Как включить остров на Android 13+", "How to turn the island on with Android 13+"),
                onClick = { open(Screen.INSTRUCTION) }, color = Tile.Yellow) { Arrow() }
            KeepAliveItem(Pos.Middle, open)
            GroupItem(Pos.Middle, Icons.Rounded.Navigation, tr("Диагностика навигации", "Navigation diagnostics"),
                tr("Что присылает навигатор, такси или доставка — если не видно расстояния",
                    "What a navigation, taxi or delivery app sends — if the distance doesn't show"),
                onClick = { open(Screen.DIAG) }, color = Tile.Teal) { Arrow() }
            GroupItem(Pos.Middle, Icons.Rounded.CreditCard, tr("Диагностика оплаты", "Payment diagnostics"),
                tr("Что остров увидел при оплате — если «Оплачено» не появилось", "What the island saw when you paid — if “Paid” didn't show"),
                onClick = { open(Screen.PAYDIAG) }, color = Tile.Green) { Arrow() }
            AppDetailsItem(Pos.Last)
        }
    }
}

@Composable
internal fun InfoText(text: String) {
    Text(
        text, color = Ui.Sub, fontSize = 14.sp, lineHeight = 20.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ui.Card).padding(20.dp),
    )
}

/**
 * Что остров видит в уведомлениях навигаторов, такси и доставки: если расстояние или стрелка
 * не показываются, здесь видно, что именно присылает приложение.
 */
@Composable
fun DiagScreen(onBack: () -> Unit) {
    val dumps by LiveDebug.dumps.collectAsState()
    DumpScreen(
        tr("Диагностика навигации", "Navigation diagnostics"),
        tr(
            "Включите навигатор, такси или доставку и вернитесь сюда: ниже то, что приложение присылает в уведомлении. " +
                "Если расстояние или стрелка поворота не показываются в острове, нажмите «Скопировать» и пришлите этот текст.",
            "Start a navigation, taxi or delivery app and come back here: below is what the app sends in its " +
                "notification. If the distance or the turn arrow doesn't show in the island, tap “Copy all” and send this " +
                "text.",
        ),
        tr("Пока ничего нет: ни одного уведомления навигатора, такси или доставки не пришло.",
            "Nothing yet: no navigation, taxi or delivery notification has arrived."),
        dumps.values.toList(), onBack,
    )
}

/**
 * Что остров увидел при оплате: экран оплаты, уведомления и СМС банков и как он их понял. Если «Оплачено»
 * не появилось, здесь видно почему.
 */
@Composable
fun PayDiagScreen(onBack: () -> Unit) {
    val log by PayDebug.log.collectAsState()
    DumpScreen(
        tr("Диагностика оплаты", "Payment diagnostics"),
        tr(
            "Заплатите телефоном и вернитесь сюда: ниже — что остров увидел (экран оплаты, уведомление или СМС банка) " +
                "и как понял. Если «Оплачено» не появилось, нажмите «Скопировать» и пришлите текст. Записи хранятся " +
                "только в памяти телефона и пропадают после перезапуска.",
            "Pay with your phone and come back here: below is what the island saw (the payment screen, a bank " +
                "notification or SMS) and how it understood it. If “Paid” didn't show, tap “Copy all” and send the text. " +
                "Entries stay only in the phone's memory and are gone after a restart.",
        ),
        tr("Пока ничего нет: остров не видел ни экрана оплаты, ни уведомлений банков.",
            "Nothing yet: the island hasn't seen a payment screen or a bank notification."),
        log, onBack,
    )
}

/** «Почему не показано»: что остров решил о последних уведомлениях — только приложение и причина. */
@Composable
fun NotifDiagScreen(onBack: () -> Unit) {
    val log by NotifDebug.log.collectAsState()
    DumpScreen(
        tr("Почему не показано", "Why it didn't show"),
        tr(
            "Пусть придёт уведомление, которого не было в острове, и вернитесь сюда: ниже — что остров решил о последних " +
                "уведомлениях. Только название приложения и причина, без текста сообщений; записи пропадают после перезапуска.",
            "Let a notification that didn't show in the island arrive, then come back here: below is what the island decided " +
                "about recent notifications. Only the app name and the reason, no message text; entries are gone after a restart.",
        ),
        tr("Пока ничего нет: уведомлений приложений не приходило.", "Nothing yet: no app notifications have arrived."),
        log, onBack,
    )
}

@Composable
private fun DumpScreen(title: String, intro: String, empty: String, items: List<String>, onBack: () -> Unit) {
    val context = LocalContext.current
    Page(title, onBack) {
        InfoText(intro)
        if (items.isEmpty()) {
            Text(empty, color = Ui.Sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
        } else {
            Row {
                PillAction(tr("Скопировать всё", "Copy all")) {
                    // Суммы и тексты банков: Android 13+ не показывает такой буфер обмена во всплывающем превью
                    val clip = ClipData.newPlainText(title, items.joinToString("\n\n"))
                    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
                }
            }
            items.forEach { dump ->
                Text(
                    dump, color = Ui.Text, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ui.Card).padding(16.dp),
                )
            }
        }
    }
}
