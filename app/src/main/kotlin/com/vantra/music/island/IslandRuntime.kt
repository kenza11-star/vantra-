package com.vantra.music.island

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import com.vantra.music.island.core.IslandBus
import com.vantra.music.island.core.Lang
import com.vantra.music.island.data.ExtrasRepo
import com.vantra.music.island.data.SettingsRepo
import com.vantra.music.island.service.StepRepo
import com.vantra.music.island.ui.SetTimerActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Starts the Dynamic Island parts of Vantra. Called once from [com.vantra.music.App]; the capsule
 * itself lives in the accessibility service, which runs in this same process.
 */
object IslandRuntime {
    @Volatile
    private var started = false

    /** The capsule needs notification channels and accessibility overlays, so it starts on Android 8.0+. */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun init(app: Application) {
        if (started || !supported) return
        started = true
        runCatching {
            SettingsRepo.init(app)
            ExtrasRepo.init(app)
            StepRepo.start(app, IslandBus.scope)
            Lang.init(app)
            // "set a timer" from the assistant: Vantra is only offered for it while this is switched on
            IslandBus.scope.launch {
                SettingsRepo.state.map { it.timerFromAssistant }.distinctUntilChanged().collect { on ->
                    runCatching {
                        app.packageManager.setComponentEnabledSetting(
                            ComponentName(app, SetTimerActivity::class.java),
                            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP,
                        )
                    }
                }
            }
        }
    }
}
