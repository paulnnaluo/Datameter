package com.datameter.data.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.datameter.MainActivity
import com.datameter.R
import com.datameter.domain.ByteFormatter
import com.datameter.domain.UsageAlert
import com.datameter.domain.UsageAlertType

class DatameterNotificationDispatcher(
    context: Context,
) {
    private val appContext = context.applicationContext

    @SuppressLint("MissingPermission")
    fun notifyUsageAlert(alert: UsageAlert): Boolean {
        if (!canPostNotifications(appContext)) return false

        DatameterNotificationChannels.ensureCreated(appContext)

        val notification = NotificationCompat.Builder(
            appContext,
            DatameterNotificationChannels.USAGE_ALERTS_CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_notification_datameter)
            .setContentTitle(alert.title)
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(appContext).notify(alert.notificationId, notification)
        return true
    }

    @SuppressLint("MissingPermission")
    fun notifyDataControlBlocked(
        appLabel: String,
        usedBytes: Long,
        limitBytes: Long?,
        globalRule: Boolean,
    ): Boolean {
        if (!canPostNotifications(appContext)) return false

        DatameterNotificationChannels.ensureCreated(appContext)

        val limitText = limitBytes?.let { " after ${ByteFormatter.format(it)}" }.orEmpty()
        val ruleText = if (globalRule) "your all-app limit" else "its app limit"
        val message = "$appLabel used ${ByteFormatter.format(usedBytes)} today. " +
            "Datameter blocked mobile data for $ruleText$limitText."

        val notification = NotificationCompat.Builder(
            appContext,
            DatameterNotificationChannels.DATA_CONTROL_CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_notification_datameter)
            .setContentTitle("$appLabel was blocked")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(appContext).notify(DATA_CONTROL_BLOCKED_NOTIFICATION_ID, notification)
        return true
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            appContext,
            NOTIFICATION_CONTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private val UsageAlert.notificationId: Int
        get() = when (type) {
            UsageAlertType.DailyLimit -> DAILY_LIMIT_NOTIFICATION_ID
            UsageAlertType.HourlySpike -> HOURLY_SPIKE_NOTIFICATION_ID
        }

    companion object {
        fun canPostNotifications(context: Context): Boolean {
            val appContext = context.applicationContext
            val managerAllowsNotifications = NotificationManagerCompat
                .from(appContext)
                .areNotificationsEnabled()
            if (!managerAllowsNotifications) return false

            return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
        }

        private const val NOTIFICATION_CONTENT_REQUEST_CODE = 100
        private const val DAILY_LIMIT_NOTIFICATION_ID = 1001
        private const val HOURLY_SPIKE_NOTIFICATION_ID = 1002
        private const val DATA_CONTROL_BLOCKED_NOTIFICATION_ID = 1003
    }
}
