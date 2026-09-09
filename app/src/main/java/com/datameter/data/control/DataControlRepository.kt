package com.datameter.data.control

interface DataControlRepository {
    fun readSettings(): DataControlSettings
    fun saveSettings(settings: DataControlSettings)
    fun acceptDisclosure()

    fun readRule(uid: Int, packageName: String, label: String): DataControlRule
    fun saveRule(rule: DataControlRule)
    fun clearAutoBlock(uid: Int)

    fun loadPolicySnapshot(localDate: String, selfUid: Int): DataControlPolicySnapshot
    fun recordUsage(
        identity: DataControlAppIdentity,
        localDate: String,
        networkType: DataControlNetworkType,
        bytes: Long,
    )

    fun readDailyUsage(uid: Int, localDate: String): DataControlDailyUsage?
    fun markAutoBlocked(
        identity: DataControlAppIdentity,
        localDate: String,
        reason: DataControlBlockReason,
        networkType: DataControlNetworkType,
        usedBytes: Long,
        limitBytes: Long?,
        createdAtMillis: Long,
    )

    fun blockedAppsCount(localDate: String): Int
    fun activeRuleCount(): Int
    fun activeControlledUids(localDate: String): Set<Int>
    fun recentBlockEvents(uid: Int, limit: Int): List<DataControlBlockEvent>
}
