package com.personal.hydra.reminder

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.personal.hydra.MainActivity
import com.personal.hydra.R
import com.personal.hydra.domain.model.ReminderDecision
import com.personal.hydra.domain.model.UnitSystem
import com.personal.hydra.util.VolumeFormat

class HydraNotifier(private val context: Context) {

    private val nm = NotificationManagerCompat.from(context)

    fun canPost(): Boolean = nm.areNotificationsEnabled()

    @SuppressLint("MissingPermission") // guarded by canPost(); wrapped in try/catch
    fun showReminder(d: ReminderDecision, unit: UnitSystem) {
        if (!canPost()) return
        val title = context.getString(
            if (d.isBehind) R.string.notif_behind_title else R.string.notif_due_title,
        )
        val body = context.getString(
            R.string.notif_body,
            VolumeFormat.volume(d.consumedMl, unit),
            VolumeFormat.volume(d.goalMl, unit),
            VolumeFormat.volume(d.remainingMl, unit),
            VolumeFormat.volume(d.nextTargetMl, unit),
        )
        val builder = NotificationCompat.Builder(context, HydraChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_water)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(context.getColor(R.color.hydra_primary))
            .addAction(
                0,
                context.getString(R.string.action_log, VolumeFormat.volume(d.nextTargetMl, unit)),
                ReminderActionReceiver.logIntent(context, d.nextTargetMl),
            )
            // 3rd action: open the app to log a different amount manually.
            .addAction(0, context.getString(R.string.action_other), openAppIntent())
            .addAction(0, context.getString(R.string.action_snooze), ReminderActionReceiver.snoozeIntent(context))

        if (d.overflowWarning) {
            builder.setSubText(context.getString(R.string.notif_overflow))
        }
        try {
            nm.notify(NOTIF_ID, builder.build())
        } catch (_: SecurityException) {
            // Notifications not granted; ignore.
        }
    }

    fun cancel() = nm.cancel(NOTIF_ID)

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.personal.hydra.OPEN_FROM_REMINDER"
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val NOTIF_ID = 1001
    }
}
