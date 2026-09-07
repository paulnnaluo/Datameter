package com.datameter.core

import android.content.Context
import com.datameter.data.alerts.AlertSettingsRepository
import com.datameter.data.alerts.SharedPreferencesAlertSettingsRepository
import com.datameter.data.audit.AuditRepository
import com.datameter.data.audit.SharedPreferencesAuditRepository
import com.datameter.data.usage.AndroidNetworkUsageDataSource
import com.datameter.data.usage.NetworkUsageDataSource
import com.datameter.data.usage.PackageLabelResolver
import com.datameter.data.usage.UsageAccessManager
import com.datameter.domain.DefaultHomeUsageRepository
import com.datameter.domain.HomeUsageRepository

object DatameterServiceLocator {
    fun usageAccessManager(context: Context): UsageAccessManager {
        return UsageAccessManager(context.applicationContext)
    }

    fun networkUsageDataSource(context: Context): NetworkUsageDataSource {
        val appContext = context.applicationContext
        return AndroidNetworkUsageDataSource(
            context = appContext,
            labelResolver = PackageLabelResolver(appContext),
        )
    }

    fun auditRepository(context: Context): AuditRepository {
        return SharedPreferencesAuditRepository(context.applicationContext)
    }

    fun alertSettingsRepository(context: Context): AlertSettingsRepository {
        return SharedPreferencesAlertSettingsRepository(context.applicationContext)
    }

    fun homeUsageRepository(context: Context): HomeUsageRepository {
        val appContext = context.applicationContext
        return DefaultHomeUsageRepository(
            usageAccessManager = usageAccessManager(appContext),
            usageDataSource = networkUsageDataSource(appContext),
            auditRepository = auditRepository(appContext),
        )
    }
}
