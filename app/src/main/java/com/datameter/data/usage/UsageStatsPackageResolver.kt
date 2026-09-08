package com.datameter.data.usage

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.datameter.domain.model.DateRange
import java.util.concurrent.ConcurrentHashMap

class UsageStatsPackageResolver(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val usageStatsManager = appContext.getSystemService(UsageStatsManager::class.java)
    private val packageIdentityCache = ConcurrentHashMap<String, PackageUsageIdentity>()

    fun identitiesByUid(range: DateRange): Map<Int, List<AppIdentity>> {
        val usageStats = runCatching {
            usageStatsManager.queryAndAggregateUsageStats(range.startMillis, range.endMillis)
        }.getOrDefault(emptyMap())

        if (usageStats.isEmpty()) return emptyMap()

        return usageStats.values
            .asSequence()
            .filter { it.packageName.isNotBlank() }
            .mapNotNull { usageStat ->
                packageUsageIdentity(usageStat)?.copy(
                    lastTimeUsedMillis = usageStat.lastTimeUsed.coerceAtLeast(0L),
                    foregroundTimeMillis = usageStat.totalTimeInForeground.coerceAtLeast(0L),
                )
            }
            .filter { it.foregroundTimeMillis > 0L || it.lastTimeUsedMillis >= range.startMillis }
            .groupBy { it.uid }
            .mapValues { (_, packages) ->
                packages
                    .sortedWith(
                        compareByDescending<PackageUsageIdentity> { it.foregroundTimeMillis }
                            .thenByDescending { it.lastTimeUsedMillis },
                    )
                    .map { packageUsageIdentity ->
                        AppIdentity(
                            id = packageUsageIdentity.packageName,
                            label = packageUsageIdentity.label,
                        )
                    }
                    .distinctBy { it.id }
            }
    }

    private fun packageUsageIdentity(usageStats: UsageStats): PackageUsageIdentity? {
        val packageName = usageStats.packageName.takeIf { it.isNotBlank() } ?: return null
        packageIdentityCache[packageName]?.let { cached ->
            return cached
        }

        val identity = runCatching {
            val info = getApplicationInfo(packageName)
            PackageUsageIdentity(
                packageName = packageName,
                uid = info.uid,
                label = packageManager.getApplicationLabel(info).toString()
                    .ifBlank { packageName.toReadablePackageLabel() },
                lastTimeUsedMillis = 0L,
                foregroundTimeMillis = 0L,
            )
        }.getOrNull()

        if (identity != null) {
            packageIdentityCache[packageName] = identity
        }
        return identity
    }

    private fun getApplicationInfo(packageName: String): ApplicationInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
    }

    private fun String.toReadablePackageLabel(): String {
        return substringAfterLast(".")
            .replace('_', ' ')
            .replace('-', ' ')
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase() else char.toString()
                }
            }
            .ifBlank { this }
    }

    private data class PackageUsageIdentity(
        val packageName: String,
        val uid: Int,
        val label: String,
        val lastTimeUsedMillis: Long,
        val foregroundTimeMillis: Long,
    )
}
