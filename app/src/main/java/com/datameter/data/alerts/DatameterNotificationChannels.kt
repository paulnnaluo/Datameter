package com.datameter.data.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import com.datameter.R

object DatameterNotificationChannels {
    const val USAGE_ALERTS_CHANNEL_ID = "usage_alerts"
    const val DATA_CONTROL_CHANNEL_ID = "data_control"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val appContext = context.applicationContext
        val notificationManager = ContextCompat.getSystemService(
            appContext,
            NotificationManager::class.java,
        ) ?: return
        val channel = NotificationChannel(
            USAGE_ALERTS_CHANNEL_ID,
            appContext.getString(R.string.notification_channel_usage_alerts_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = appContext.getString(R.string.notification_channel_usage_alerts_description)
        }

        notificationManager.createNotificationChannel(channel)

        val dataControlChannel = NotificationChannel(
            DATA_CONTROL_CHANNEL_ID,
            appContext.getString(R.string.notification_channel_data_control_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = appContext.getString(R.string.notification_channel_data_control_description)
        }
        notificationManager.createNotificationChannel(dataControlChannel)
    }
}
