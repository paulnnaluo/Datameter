package com.datameter.data.usage

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.util.concurrent.ConcurrentHashMap

data class AppIdentity(
    val id: String,
    val label: String,
)

class PackageLabelResolver(context: Context) {
    private val packageManager = context.applicationContext.packageManager
    private val identityCache = ConcurrentHashMap<Int, AppIdentity>()

    fun resolve(uid: Int, usageStatIdentities: List<AppIdentity> = emptyList()): AppIdentity {
        if (usageStatIdentities.isNotEmpty()) {
            return identityFromUsageStats(usageStatIdentities)
        }

        return identityCache.getOrPut(uid) {
            resolveUncached(uid)
        }
    }

    private fun identityFromUsageStats(identities: List<AppIdentity>): AppIdentity {
        val distinctIdentities = identities.distinctBy { it.id }
        val first = distinctIdentities.first()
        return AppIdentity(
            id = first.id,
            label = if (distinctIdentities.size <= 1) {
                first.label
            } else {
                "${first.label} + ${distinctIdentities.size - 1}"
            },
        )
    }

    private fun resolveUncached(uid: Int): AppIdentity {
        val packages = packageNamesForUid(uid)
        val labels = packages.mapNotNull { packageName ->
            applicationLabel(packageName)
        }.distinct()

        val label = when {
            labels.isNotEmpty() -> labels.first()
            packages.isNotEmpty() -> packages.first().toReadablePackageLabel()
            else -> "Unidentified app"
        }
        val finalLabel = when {
            labels.size <= 1 -> label
            else -> "${labels.first()} + ${labels.size - 1}"
        }

        return AppIdentity(
            id = packages.firstOrNull() ?: "uid:$uid",
            label = finalLabel,
        )
    }

    private fun packageNamesForUid(uid: Int): List<String> {
        val directPackages = packageManager.getPackagesForUid(uid).orEmpty()
        if (directPackages.isNotEmpty()) return directPackages.distinct()

        return packageManager.getNameForUid(uid)
            ?.split(":")
            ?.map { it.trim() }
            ?.filter { it.contains(".") && !it.startsWith("android.uid.") }
            ?.distinct()
            .orEmpty()
    }

    private fun applicationLabel(packageName: String): String? {
        return runCatching {
            val info = getApplicationInfo(packageName)
            packageManager.getApplicationLabel(info).toString()
        }.getOrNull()
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
}
