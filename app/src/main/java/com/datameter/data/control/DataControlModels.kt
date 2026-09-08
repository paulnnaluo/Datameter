package com.datameter.data.control

import android.os.Process
import com.datameter.domain.DataUnits

enum class DataControlNetworkType {
    Mobile,
    Wifi,
    Unknown,
}

enum class DataControlRunStatus {
    Off,
    Standby,
    Starting,
    Active,
    NeedsVpnPermission,
    Error,
}

data class DataControlRuntimeState(
    val status: DataControlRunStatus = DataControlRunStatus.Off,
    val message: String? = null,
)

data class DataControlSettings(
    val enabled: Boolean = false,
    val disclosureAccepted: Boolean = false,
    val globalAutoBlockEnabled: Boolean = false,
    val globalAutoBlockBytes: Long = DEFAULT_APP_LIMIT_BYTES,
) {
    val hasActiveControls: Boolean
        get() = enabled || globalAutoBlockEnabled
}

data class DataControlAppIdentity(
    val uid: Int,
    val packageName: String,
    val label: String,
    val packageNames: List<String> = listOf(packageName),
) {
    val displayLabel: String
        get() = if (packageNames.size > 1) "$label + ${packageNames.size - 1}" else label
}

data class DataControlRule(
    val uid: Int,
    val packageName: String,
    val label: String,
    val blockMobileData: Boolean = false,
    val blockWifi: Boolean = false,
    val dailyLimitEnabled: Boolean = false,
    val dailyLimitBytes: Long = DEFAULT_APP_LIMIT_BYTES,
    val autoBlockedLocalDate: String? = null,
    val autoBlockedReason: DataControlBlockReason? = null,
    val autoBlockedAtMillis: Long? = null,
) {
    fun isAutoBlockedToday(localDate: String): Boolean {
        return autoBlockedLocalDate == localDate
    }

    companion object {
        fun default(uid: Int, packageName: String, label: String): DataControlRule {
            return DataControlRule(
                uid = uid,
                packageName = packageName,
                label = label,
            )
        }
    }
}

data class DataControlDailyUsage(
    val uid: Int,
    val packageName: String,
    val label: String,
    val localDate: String,
    val mobileBytes: Long,
    val wifiBytes: Long,
) {
    val totalBytes: Long
        get() = mobileBytes.coerceAtLeast(0L) + wifiBytes.coerceAtLeast(0L)
}

enum class DataControlBlockReason {
    ManualMobile,
    ManualWifi,
    GlobalLimit,
    AppLimit,
}

data class DataControlBlockEvent(
    val id: Long,
    val uid: Int,
    val packageName: String,
    val label: String,
    val localDate: String,
    val reason: DataControlBlockReason,
    val networkType: DataControlNetworkType,
    val usedBytes: Long,
    val limitBytes: Long?,
    val createdAtMillis: Long,
)

data class DataControlPolicySnapshot(
    val settings: DataControlSettings = DataControlSettings(),
    val rulesByUid: Map<Int, DataControlRule> = emptyMap(),
    val todayMobileBytesByUid: Map<Int, Long> = emptyMap(),
    val localDate: String = "",
    val selfUid: Int = Process.INVALID_UID,
) {
    fun blockReasonFor(uid: Int, networkType: DataControlNetworkType): DataControlBlockReason? {
        if (!settings.enabled || uid < Process.FIRST_APPLICATION_UID || uid == selfUid) return null
        val rule = rulesByUid[uid]
        if (
            networkType == DataControlNetworkType.Mobile &&
            rule?.isAutoBlockedToday(localDate) == true
        ) {
            return rule.autoBlockedReason ?: DataControlBlockReason.GlobalLimit
        }

        return when (networkType) {
            DataControlNetworkType.Mobile -> {
                if (rule?.blockMobileData == true) DataControlBlockReason.ManualMobile else null
            }

            DataControlNetworkType.Wifi -> {
                if (rule?.blockWifi == true) DataControlBlockReason.ManualWifi else null
            }

            DataControlNetworkType.Unknown -> null
        }
    }

    fun controlledUidsFor(networkType: DataControlNetworkType): Set<Int> {
        if (!settings.enabled) return emptySet()

        return rulesByUid.values
            .filter { rule ->
                when (networkType) {
                    DataControlNetworkType.Mobile -> {
                        rule.blockMobileData ||
                            rule.dailyLimitEnabled ||
                            rule.isAutoBlockedToday(localDate)
                    }

                    DataControlNetworkType.Wifi -> rule.blockWifi
                    DataControlNetworkType.Unknown -> {
                        rule.blockMobileData ||
                            rule.blockWifi ||
                            rule.dailyLimitEnabled ||
                            rule.isAutoBlockedToday(localDate)
                    }
                }
            }
            .map { it.uid }
            .filter { it >= Process.FIRST_APPLICATION_UID && it != selfUid }
            .toSet()
    }

    fun autoBlockReasonFor(uid: Int, mobileBytesToday: Long): DataControlBlockReason? {
        if (!settings.enabled || uid < Process.FIRST_APPLICATION_UID || uid == selfUid) return null
        val rule = rulesByUid[uid]
        if (rule?.isAutoBlockedToday(localDate) == true) return null

        if (rule?.dailyLimitEnabled == true && mobileBytesToday >= rule.dailyLimitBytes) {
            return DataControlBlockReason.AppLimit
        }

        if (
            settings.globalAutoBlockEnabled &&
            mobileBytesToday >= settings.globalAutoBlockBytes
        ) {
            return DataControlBlockReason.GlobalLimit
        }

        return null
    }
}

const val DEFAULT_APP_LIMIT_BYTES: Long = DataUnits.GIGABYTE
