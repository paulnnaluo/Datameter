package com.datameter.data.usage

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process

interface UsageAccessChecker {
    fun hasUsageAccess(): Boolean
}

class UsageAccessManager(context: Context) : UsageAccessChecker {
    private val appContext = context.applicationContext
    private val appOpsManager = appContext.getSystemService(AppOpsManager::class.java)

    override fun hasUsageAccess(): Boolean {
        val packageName = appContext.packageName
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName,
            )
        }

        return mode == AppOpsManager.MODE_ALLOWED
    }
}
