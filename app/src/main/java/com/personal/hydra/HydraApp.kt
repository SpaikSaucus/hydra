package com.personal.hydra

import android.app.Application
import com.personal.hydra.di.AppContainer
import com.personal.hydra.reminder.HydraChannels
import com.personal.hydra.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HydraApp : Application() {

    lateinit var container: AppContainer
        private set

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        HydraChannels.ensure(this)
        ReminderScheduler.ensureScheduled(this)
    }
}
