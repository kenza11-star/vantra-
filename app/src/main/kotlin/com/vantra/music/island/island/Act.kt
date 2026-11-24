package com.vantra.music.island.island

import com.vantra.music.island.core.AlarmInfo
import com.vantra.music.island.core.InboxItem
import com.vantra.music.island.core.BatteryInfo
import com.vantra.music.island.core.CallInfo
import com.vantra.music.island.core.ChronoInfo
import com.vantra.music.island.core.LiveInfo
import com.vantra.music.island.core.MediaInfo
import com.vantra.music.island.core.Notice
import com.vantra.music.island.core.PayReady
import com.vantra.music.island.core.ProgressInfo
import com.vantra.music.island.core.StopwatchState
import com.vantra.music.island.core.TimerState
import com.vantra.music.island.core.TorchState

/** То, что сейчас может показывать остров. Короткие (transient) не раскрываются и не уходят в пузырь. */
sealed class Act(val id: String, val transient: Boolean = false) {
    open val pkg: String? get() = null

    data object Idle : Act("idle")
    data class HotspotA(val status: com.vantra.music.island.service.HotspotStatus) : Act("hotspot")
    data class WeatherA(val weather: com.vantra.music.island.service.WeatherInfo) : Act("weather")
    data class NoticeA(val n: Notice) : Act("notice", transient = true)
    data class TimerA(val t: TimerState) : Act("timer")
    data class StopwatchA(val w: StopwatchState) : Act("stopwatch")
    data class TorchA(val t: TorchState) : Act("torch")
    data class ChronoA(val c: ChronoInfo) : Act("chrono") {
        override val pkg: String get() = c.pkg
    }
    data class MediaA(val m: MediaInfo) : Act("media") {
        override val pkg: String get() = m.pkg
    }
    data class ProgressA(val p: ProgressInfo) : Act("progress") {
        override val pkg: String get() = p.pkg
    }
    data class CallA(val c: CallInfo) : Act("call") {
        override val pkg: String get() = c.pkg
    }
    data class LiveA(val l: LiveInfo) : Act("live") {
        override val pkg: String get() = l.pkg
    }
    data class ChargingA(val b: BatteryInfo) : Act("charging")
    /** Оплата телефоном: ждём терминал. Не раскрывается и не уходит в пузырь */
    data class PayA(val p: PayReady) : Act("pay", transient = true) {
        override val pkg: String get() = p.pkg
    }
    data class AlarmA(val a: AlarmInfo) : Act("alarm") {
        override val pkg: String? get() = a.pkg
    }
    /** «История уведомлений»: прочитать позже — значки приложений в капсуле, список при раскрытии */
    data class InboxA(val items: List<InboxItem>) : Act("inbox")
}
