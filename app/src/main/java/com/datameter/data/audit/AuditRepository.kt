package com.datameter.data.audit

data class AuditSession(
    val networkName: String,
    val startingBalanceBytes: Long,
    val startedAtMillis: Long,
    val startingDeviceMeterBytes: Long,
    val lastBalanceBytes: Long?,
    val lastCheckedAtMillis: Long?,
)

interface AuditRepository {
    fun currentSession(): AuditSession?

    fun startSession(
        networkName: String,
        startingBalanceBytes: Long,
        startingDeviceMeterBytes: Long,
        startedAtMillis: Long,
    )

    fun recordBalance(balanceBytes: Long, checkedAtMillis: Long)

    fun clearSession()
}
