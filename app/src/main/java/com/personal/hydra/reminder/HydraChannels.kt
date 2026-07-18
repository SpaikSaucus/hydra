package com.personal.hydra.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.personal.hydra.R

object HydraChannels {
    const val REMINDERS = "hydra.reminders"

    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            REMINDERS,
            context.getString(R.string.channel_reminders_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_reminders_desc)
            enableVibration(true)
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
    }
}
