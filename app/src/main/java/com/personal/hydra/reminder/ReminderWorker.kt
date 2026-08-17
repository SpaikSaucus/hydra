package com.personal.hydra.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personal.hydra.HydraApp
import com.personal.hydra.core.time.DayKeyResolver
import com.personal.hydra.domain.ReminderEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val app = applicationContext as HydraApp
        val container = app.container

        val config = container.settingsRepository.snapshot()
        if (!config.settings.remindersEnabled) return@withContext Result.success()
        if (!container.notifier.canPost()) return@withContext Result.success()

        val consumed = container.hydrationRepository.consumedToday()
        val sinceLast = container.hydrationRepository.minutesSinceLastIntake()
        // The hydration day = the calendar day; the wake/sleep times only bound the
        // reminder window inside ReminderEvaluator.
        val today = LocalDate.parse(DayKeyResolver().todayKey())
        val decision = ReminderEvaluator.evaluate(config, consumed, sinceLast, LocalTime.now(), today)

        // Post when due; otherwise clear any stale notification (goal reached, night
        // cutoff, paused, or simply not due yet) so a reminder never lingers.
        if (decision.shouldNotify) {
            container.notifier.showReminder(decision, config.settings.unitSystem)
        } else {
            container.notifier.cancel()
        }
        Result.success()
    }
}
