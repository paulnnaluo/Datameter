package com.datameter.data.alerts

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat

object UsageAlertScheduler {
    fun sync(context: Context) {
        val appContext = context.applicationContext
        val settings = SharedPreferencesAlertSettingsRepository(appContext).read()
        val jobScheduler = ContextCompat.getSystemService(
            appContext,
            JobScheduler::class.java,
        ) ?: return

        if (!settings.hasEnabledAlerts) {
            jobScheduler.cancel(IMMEDIATE_JOB_ID)
            jobScheduler.cancel(PERIODIC_JOB_ID)
            return
        }

        jobScheduler.schedule(periodicJob(appContext))
        jobScheduler.schedule(immediateJob(appContext))
    }

    private fun periodicJob(context: Context): JobInfo {
        return JobInfo.Builder(PERIODIC_JOB_ID, serviceComponent(context))
            .setPeriodic(PERIODIC_REPEAT_MILLIS)
            .setPersisted(true)
            .build()
    }

    private fun immediateJob(context: Context): JobInfo {
        return JobInfo.Builder(IMMEDIATE_JOB_ID, serviceComponent(context))
            .setMinimumLatency(0L)
            .setOverrideDeadline(0L)
            .build()
    }

    private fun serviceComponent(context: Context): ComponentName {
        return ComponentName(
            context.applicationContext,
            UsageAlertJobService::class.java,
        )
    }

    private const val PERIODIC_JOB_ID = 2001
    private const val IMMEDIATE_JOB_ID = 2002
    private const val PERIODIC_REPEAT_MILLIS = 15L * 60L * 1000L
}
