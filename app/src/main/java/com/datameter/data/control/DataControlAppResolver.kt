package com.datameter.data.control

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

class DataControlAppResolver(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = context.applicationContext.packageManager

    fun resolveUid(uid: Int): DataControlAppIdentity {
        val packages = packageManager.getPackagesForUid(uid)
            ?.distinct()
            .orEmpty()
        val primaryPackage = packages.firstOrNull() ?: "uid:$uid"
        val labels = packages.mapNotNull(::applicationLabel).distinct()
        val label = labels.firstOrNull() ?: primaryPackage.toReadablePackageLabel()

        return DataControlAppIdentity(
            uid = uid,
            packageName = primaryPackage,
            label = label,
            packageNames = packages.ifEmpty { listOf(primaryPackage) },
        )
    }

    fun identityFor(uid: Int, packageName: String, label: String): DataControlAppIdentity {
        val packages = packageManager.getPackagesForUid(uid)
            ?.distinct()
            .orEmpty()
            .ifEmpty { listOf(packageName) }
        val finalPackageName = packageName.takeIf { it.isNotBlank() } ?: packages.first()
        val finalLabel = label.takeIf { it.isNotBlank() }
            ?: applicationLabel(finalPackageName)
            ?: finalPackageName.toReadablePackageLabel()

        return DataControlAppIdentity(
            uid = uid,
            packageName = finalPackageName,
            label = finalLabel,
            packageNames = packages,
        )
    }

    fun controllableInstalledPackages(): Set<String> {
        val applications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(0)
        }

        return applications
            .asSequence()
            .filter { it.enabled }
            .filter { it.uid >= android.os.Process.FIRST_APPLICATION_UID }
            .filter { it.packageName != appContext.packageName }
            .filter { it.isUserVisibleOrUserInstalled() }
            .map { it.packageName }
            .toSet()
    }

    private fun applicationLabel(packageName: String): String? {
        return runCatching {
            val info = getApplicationInfo(packageName)
            packageManager.getApplicationLabel(info).toString()
                .ifBlank { packageName.toReadablePackageLabel() }
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

    private fun ApplicationInfo.isUserVisibleOrUserInstalled(): Boolean {
        val systemApp = flags and ApplicationInfo.FLAG_SYSTEM != 0
        val updatedSystemApp = flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        val launchable = packageManager.getLaunchIntentForPackage(packageName) != null
        return !systemApp || updatedSystemApp || launchable
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
