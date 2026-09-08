package com.datameter.domain

import com.datameter.data.alerts.AlertSettings

enum class UsageAlertType {
    DailyLimit,
    HourlySpike,
}

data class UsageAlert(
    val type: UsageAlertType,
    val title: String,
    val message: String,
)

object UsageAlertEvaluator {
    fun evaluate(
        settings: AlertSettings,
        todayMobileBytes: Long,
        lastHourMobileBytes: Long,
    ): List<UsageAlert> {
        val alerts = mutableListOf<UsageAlert>()

        if (settings.dailyLimitEnabled && todayMobileBytes >= settings.dailyLimitBytes) {
            alerts += UsageAlert(
                type = UsageAlertType.DailyLimit,
                title = "Daily data alert",
                message = "You used ${ByteFormatter.format(todayMobileBytes)} today. " +
                    "Your daily alert is ${ByteFormatter.format(settings.dailyLimitBytes)}.",
            )
        }

        if (settings.hourlySpikeEnabled && lastHourMobileBytes >= settings.hourlySpikeBytes) {
            alerts += UsageAlert(
                type = UsageAlertType.HourlySpike,
                title = "High hourly usage",
                message = "You used ${ByteFormatter.format(lastHourMobileBytes)} in the last hour. " +
                    "Your hourly alert is ${ByteFormatter.format(settings.hourlySpikeBytes)}.",
            )
        }

        return alerts
    }
}
