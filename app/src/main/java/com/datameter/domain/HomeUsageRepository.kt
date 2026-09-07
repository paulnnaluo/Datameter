package com.datameter.domain

import com.datameter.data.audit.AuditRepository
import com.datameter.data.usage.NetworkUsageDataSource
import com.datameter.data.usage.UsageAccessChecker
import com.datameter.domain.model.AuditHomeStatus
import com.datameter.domain.model.AuditMath
import com.datameter.domain.model.DataFreshness
import com.datameter.domain.model.DateRange
import com.datameter.domain.model.HomeViewState
import com.datameter.domain.model.NetworkFilter
import com.datameter.domain.model.PeriodRangeResolver
import com.datameter.domain.model.PeriodSummary
import com.datameter.domain.model.PermissionStatus
import com.datameter.domain.model.UsagePeriod

interface HomeUsageRepository {
    suspend fun loadHomeState(
        networkFilter: NetworkFilter,
        period: UsagePeriod,
    ): HomeViewState
}

class DefaultHomeUsageRepository(
    private val usageAccessManager: UsageAccessChecker,
    private val usageDataSource: NetworkUsageDataSource,
    private val auditRepository: AuditRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : HomeUsageRepository {
    override suspend fun loadHomeState(
        networkFilter: NetworkFilter,
        period: UsagePeriod,
    ): HomeViewState {
        val now = clock()
        if (!usageAccessManager.hasUsageAccess()) {
            return HomeViewState.initial(networkFilter, period).copy(
                isLoading = false,
                permissionStatus = PermissionStatus.Missing,
                dataFreshness = DataFreshness.PermissionMissing,
            )
        }

        val selectedRange = PeriodRangeResolver.resolve(period, now)
        val selectedSnapshot = usageDataSource.query(networkFilter, selectedRange)
        val summaries = UsagePeriod.entries.map { summaryPeriod ->
            val range = PeriodRangeResolver.resolve(summaryPeriod, now)
            PeriodSummary(
                period = summaryPeriod,
                totalBytes = usageDataSource.query(networkFilter, range).total.totalBytes,
            )
        }

        return HomeViewState(
            selectedNetworkFilter = networkFilter,
            selectedPeriod = period,
            totalBytes = selectedSnapshot.total.totalBytes,
            periodSummaries = summaries,
            usageRows = selectedSnapshot.rows,
            timelineBuckets = selectedSnapshot.timelineBuckets,
            primaryInsight = HomeInsightBuilder.build(
                rows = selectedSnapshot.rows,
                totalBytes = selectedSnapshot.total.totalBytes,
                networkFilter = networkFilter,
                period = period,
            ),
            auditStatus = loadAuditStatus(now),
            permissionStatus = PermissionStatus.Granted,
            dataFreshness = DataFreshness.Fresh(now),
            isLoading = false,
        )
    }

    private suspend fun loadAuditStatus(now: Long): AuditHomeStatus {
        val session = auditRepository.currentSession() ?: return AuditHomeStatus.Inactive
        val measuredBytes = usageDataSource.query(
            NetworkFilter.Mobile,
            DateRange(session.startedAtMillis, now),
        ).total.totalBytes
        val deductedBytes = session.lastBalanceBytes?.let {
            (session.startingBalanceBytes - it).coerceAtLeast(0L)
        }
        val differenceBytes = deductedBytes?.minus(measuredBytes)

        return AuditHomeStatus.Active(
            networkName = session.networkName,
            startedAtMillis = session.startedAtMillis,
            measuredBytes = measuredBytes,
            deductedBytes = deductedBytes,
            differenceBytes = differenceBytes,
            assessment = AuditMath.assess(measuredBytes, deductedBytes),
        )
    }
}
