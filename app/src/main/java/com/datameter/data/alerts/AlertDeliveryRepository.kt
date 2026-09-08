package com.datameter.data.alerts

import android.content.Context

data class AlertDeliveryState(
    val dailyLimitDate: String? = null,
    val hourlySpikeBucketStartMillis: Long? = null,
)

interface AlertDeliveryRepository {
    fun read(): AlertDeliveryState
    fun markDailyLimitSent(localDate: String)
    fun markHourlySpikeSent(bucketStartMillis: Long)
}

class SharedPreferencesAlertDeliveryRepository(context: Context) : AlertDeliveryRepository {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): AlertDeliveryState {
        return AlertDeliveryState(
            dailyLimitDate = prefs.getString(KEY_DAILY_LIMIT_DATE, null),
            hourlySpikeBucketStartMillis = prefs.getNullableLong(KEY_HOURLY_SPIKE_BUCKET),
        )
    }

    override fun markDailyLimitSent(localDate: String) {
        prefs.edit()
            .putString(KEY_DAILY_LIMIT_DATE, localDate)
            .apply()
    }

    override fun markHourlySpikeSent(bucketStartMillis: Long) {
        prefs.edit()
            .putLong(KEY_HOURLY_SPIKE_BUCKET, bucketStartMillis)
            .apply()
    }

    private fun android.content.SharedPreferences.getNullableLong(key: String): Long? {
        return if (contains(key)) getLong(key, 0L) else null
    }

    private companion object {
        const val PREFS_NAME = "datameter_alert_delivery"
        const val KEY_DAILY_LIMIT_DATE = "daily_limit_date"
        const val KEY_HOURLY_SPIKE_BUCKET = "hourly_spike_bucket"
    }
}
