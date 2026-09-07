package com.datameter.data.alerts

import android.content.Context

class SharedPreferencesAlertSettingsRepository(context: Context) : AlertSettingsRepository {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): AlertSettings {
        return AlertSettings(
            dailyLimitEnabled = prefs.getBoolean(KEY_DAILY_ENABLED, false),
            dailyLimitBytes = prefs.getLong(KEY_DAILY_BYTES, 1_000_000_000L),
            hourlySpikeEnabled = prefs.getBoolean(KEY_HOURLY_ENABLED, false),
            hourlySpikeBytes = prefs.getLong(KEY_HOURLY_BYTES, 500_000_000L),
        )
    }

    override fun save(settings: AlertSettings) {
        prefs.edit()
            .putBoolean(KEY_DAILY_ENABLED, settings.dailyLimitEnabled)
            .putLong(KEY_DAILY_BYTES, settings.dailyLimitBytes)
            .putBoolean(KEY_HOURLY_ENABLED, settings.hourlySpikeEnabled)
            .putLong(KEY_HOURLY_BYTES, settings.hourlySpikeBytes)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "datameter_alerts"
        const val KEY_DAILY_ENABLED = "daily_enabled"
        const val KEY_DAILY_BYTES = "daily_bytes"
        const val KEY_HOURLY_ENABLED = "hourly_enabled"
        const val KEY_HOURLY_BYTES = "hourly_bytes"
    }
}
