package com.personal.hydra.reminder

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.personal.hydra.HydraApp
import com.personal.hydra.domain.model.IntakeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Handles notification actions off the main thread via goAsync() + IO.
 * Not exported; only triggered by our own PendingIntents.
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as HydraApp
        val pending = goAsync()
        when (intent.action) {
            ACTION_LOG -> {
                val ml = intent.getIntExtra(EXTRA_ML, 250)
                app.appScope.launch(Dispatchers.IO) {
                    try {
                        app.container.hydrationRepository.addIntake(ml, IntakeSource.NOTIFICATION)
                        app.container.notifier.cancel()
                    } finally {
                        pending.finish()
                    }
                }
            }

            ACTION_SNOOZE -> {
                app.appScope.launch(Dispatchers.IO) {
                    try {
                        app.container.notifier.cancel()
                        val min = app.container.settingsRepository.snapshot().settings.snoozeMin
                        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                            .setInitialDelay(min.toLong(), TimeUnit.MINUTES)
                            .addTag("hydra-snooze")
                            .build()
                        WorkManager.getInstance(context)
                            .enqueueUniqueWork("hydra_snooze", ExistingWorkPolicy.REPLACE, req)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_LOG = "com.personal.hydra.action.LOG"
        const val ACTION_SNOOZE = "com.personal.hydra.action.SNOOZE"
        const val EXTRA_ML = "ml"

        // Distinct, fixed request codes per action. The suggested amount travels in
        // EXTRA_ML (FLAG_UPDATE_CURRENT refreshes the extra), so the request code
        // need not encode `ml` — avoiding any overlap between action code ranges.
        private const val RC_LOG = 1001
        private const val RC_SNOOZE = 1002

        fun logIntent(ctx: Context, ml: Int): PendingIntent {
            val i = Intent(ctx, ReminderActionReceiver::class.java)
                .setAction(ACTION_LOG)
                .putExtra(EXTRA_ML, ml)
            return PendingIntent.getBroadcast(
                ctx,
                RC_LOG,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun snoozeIntent(ctx: Context): PendingIntent {
            val i = Intent(ctx, ReminderActionReceiver::class.java).setAction(ACTION_SNOOZE)
            return PendingIntent.getBroadcast(
                ctx,
                RC_SNOOZE,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
