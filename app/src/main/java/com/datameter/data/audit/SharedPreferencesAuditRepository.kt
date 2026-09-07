package com.datameter.data.audit

import android.content.Context

class SharedPreferencesAuditRepository(context: Context) : AuditRepository {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun currentSession(): AuditSession? {
        if (!prefs.contains(KEY_STARTED_AT)) return null

        return AuditSession(
            networkName = prefs.getString(KEY_NETWORK_NAME, null).orEmpty().ifBlank { "Network" },
            startingBalanceBytes = prefs.getLong(KEY_STARTING_BALANCE, 0L),
            startedAtMillis = prefs.getLong(KEY_STARTED_AT, 0L),
            startingDeviceMeterBytes = prefs.getLong(KEY_STARTING_DEVICE_METER, 0L),
            lastBalanceBytes = prefs.getNullableLong(KEY_LAST_BALANCE),
            lastCheckedAtMillis = prefs.getNullableLong(KEY_LAST_CHECKED_AT),
        )
    }

    override fun startSession(
        networkName: String,
        startingBalanceBytes: Long,
        startingDeviceMeterBytes: Long,
        startedAtMillis: Long,
    ) {
        prefs.edit()
            .clear()
            .putString(KEY_NETWORK_NAME, networkName.ifBlank { "Network" })
            .putLong(KEY_STARTING_BALANCE, startingBalanceBytes)
            .putLong(KEY_STARTED_AT, startedAtMillis)
            .putLong(KEY_STARTING_DEVICE_METER, startingDeviceMeterBytes)
            .apply()
    }

    override fun recordBalance(balanceBytes: Long, checkedAtMillis: Long) {
        if (!prefs.contains(KEY_STARTED_AT)) return

        prefs.edit()
            .putLong(KEY_LAST_BALANCE, balanceBytes)
            .putLong(KEY_LAST_CHECKED_AT, checkedAtMillis)
            .apply()
    }

    override fun clearSession() {
        prefs.edit().clear().apply()
    }

    private fun android.content.SharedPreferences.getNullableLong(key: String): Long? {
        return if (contains(key)) getLong(key, 0L) else null
    }

    private companion object {
        const val PREFS_NAME = "datameter_audit"
        const val KEY_NETWORK_NAME = "network_name"
        const val KEY_STARTING_BALANCE = "starting_balance"
        const val KEY_STARTED_AT = "started_at"
        const val KEY_STARTING_DEVICE_METER = "starting_device_meter"
        const val KEY_LAST_BALANCE = "last_balance"
        const val KEY_LAST_CHECKED_AT = "last_checked_at"
    }
}
