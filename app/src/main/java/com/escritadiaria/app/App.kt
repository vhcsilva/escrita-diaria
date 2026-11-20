package com.escritadiaria.app

import android.app.Application
import com.escritadiaria.app.data.AppDb
import com.escritadiaria.app.data.SettingsStore
import com.escritadiaria.app.ink.Recognition
import com.escritadiaria.app.reminders.Notifs
import com.escritadiaria.app.reminders.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class App : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifs.createChannels(this)
        appScope.launch {
            SettingsStore(this@App).ensureCycleStart()
            AppDb.get(this@App).rituals().deleteAbandoned(LocalDate.now().toEpochDay())
            Scheduler.rescheduleAll(this@App)
            Recognition.processPending(this@App)
        }
    }
}
