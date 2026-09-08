package com.datameter.data.alerts

import android.content.Context
import com.datameter.core.DatameterServiceLocator
import com.datameter.domain.UsageAlertEvaluator
import com.datameter.domain.UsageAlertType
import com.datameter.domain.model.DateRange
import com.datameter.domain.model.NetworkFilter
import com.datameter.domain.model.PeriodRangeResolver
import com.datameter.domain.model.UsagePeriod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object UsageAlertRunner {
    suspend fun run(context: Context) {
        val appContext = context.applicationContext
        val settings = DatameterServiceLocator.alertSettingsRepository(appContext).read()
        if (!settings.hasEnabledAlerts) return

        if (!DatameterServiceLocator.usageAccessManager(appContext).hasUsageAccess()) {
            return
        }

        val now = System.currentTimeMillis()
        val usageDataSource = DatameterServiceLocator.networkUsageDataSource(appContext)
        val todayMobileBytes = usageDataSource.query(
            NetworkFilter.Mobile,
            PeriodRangeResolver.resolve(UsagePeriod.Today, now),
        ).total.totalBytes
        val lastHourMobileBytes = usageDataSource.query(
            NetworkFilter.Mobile,
            DateRange(
                startMillis = (now - ONE_HOUR_MILLIS).coerceAtLeast(0L),
                endMillis = now,
            ),
        ).total.totalBytes

        val alerts = UsageAlertEvaluator.evaluate(
            settings = settings,
            todayMobileBytes = todayMobileBytes,
            lastHourMobileBytes = lastHourMobileBytes,
        )
        if (alerts.isEmpty()) return

        val deliveryRepository = DatameterServiceLocator.alertDeliveryRepository(appContext)
        val deliveryState = deliveryRepository.read()
        val dispatcher = DatameterServiceLocator.notificationDispatcher(appContext)
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        val hourlyBucketStartMillis = floorToHour(now)

        alerts.forEach { alert ->
            when (alert.type) {
                UsageAlertType.DailyLimit -> {
                    if (deliveryState.dailyLimitDate == today) return@forEach
                    if (dispatcher.notifyUsageAlert(alert)) {
                        deliveryRepository.markDailyLimitSent(today)
                    }
                }

                UsageAlertType.HourlySpike -> {
                    if (deliveryState.hourlySpikeBucketStartMillis == hourlyBucketStartMillis) {
                        return@forEach
                    }
                    if (dispatcher.notifyUsageAlert(alert)) {
                        deliveryRepository.markHourlySpikeSent(hourlyBucketStartMillis)
                    }
                }
            }
        }
    }

    private fun floorToHour(millis: Long): Long {
        return Instant
            .ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .toInstant()
            .toEpochMilli()
    }

    private const val ONE_HOUR_MILLIS = 60L * 60L * 1000L
}
