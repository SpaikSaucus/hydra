package com.personal.hydra.reminder

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * One unique periodic work that ticks every 15 min (the WorkManager floor).
 * WorkManager persists its queue and reschedules itself after reboot, so no
 * BOOT_COMPLETED receiver is needed.
 */
object ReminderScheduler {
    const val UNIQUE = "hydra_periodic_reminder"

    fun ensureScheduled(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE,
            ExistingPeriodicWorkPolicy.KEEP,
            buildRequest(),
        )
    }

    /** Call when the user changes the window / frequency. */
    fun reschedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE,
            ExistingPeriodicWorkPolicy.UPDATE,
            buildRequest(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
    }

    private fun buildRequest() =
        PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .setRequiresCharging(false)
                    .build(),
            )
            .addTag("hydra-reminders")
            .build()
}
