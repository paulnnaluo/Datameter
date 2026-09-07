package com.datameter.data.usage

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

data class AppIdentity(
    val id: String,
    val label: String,
)

class PackageLabelResolver(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    fun resolve(uid: Int): AppIdentity {
        val packages = packageNamesForUid(uid)
        val labels = packages.mapNotNull { packageName ->
            applicationLabel(packageName)
        }.distinct()

        val label = when {
            labels.isNotEmpty() -> labels.first()
            packages.isNotEmpty() -> packages.first().toReadablePackageLabel()
            else -> "App UID $uid"
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
