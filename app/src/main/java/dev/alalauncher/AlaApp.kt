// SPDX-License-Identifier: GPL-3.0-or-later
package dev.alalauncher

import android.app.Application
import dev.alalauncher.data.AppRepository
import dev.alalauncher.data.IconPackRepository
import dev.alalauncher.data.CrashLog
import dev.alalauncher.data.Prefs
import dev.alalauncher.widget.ClockWidgetBattery
import dev.alalauncher.widget.AlaAppWidgetHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AlaApp : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var appRepository: AppRepository
        private set
    lateinit var iconPackRepository: IconPackRepository
        private set
    lateinit var widgetHost: AlaAppWidgetHost
        private set

    /** Outlives any screen, for work that must finish even as the launcher is left behind. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // First, so a crash on the way up is still recorded.
        CrashLog.install(this)
        prefs = Prefs(this)
        appRepository = AppRepository(this, prefs, appScope)
        iconPackRepository = IconPackRepository(this)
        widgetHost = AlaAppWidgetHost(this, HOST_ID)
        // Nothing else keeps the clock widget's battery line current; see the object.
        ClockWidgetBattery.watch(this)
    }

    companion object {
        const val HOST_ID = 1024
    }
}