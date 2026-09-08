package com.datameter.data.alerts

data class AlertSettings(
    val dailyLimitEnabled: Boolean = false,
    val dailyLimitBytes: Long = 1_000_000_000L,
    val hourlySpikeEnabled: Boolean = false,
    val hourlySpikeBytes: Long = 500_000_000L,
) {
    val hasEnabledAlerts: Boolean
        get() = dailyLimitEnabled || hourlySpikeEnabled
}

interface AlertSettingsRepository {
    fun read(): AlertSettings
    fun save(settings: AlertSettings)
}
