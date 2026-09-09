package com.datameter.domain

import com.datameter.data.usage.NetworkUsageDataSource
import com.datameter.data.usage.UsageAccessChecker
import com.datameter.domain.model.DataFreshness
import com.datameter.domain.model.HomeViewState
import com.datameter.domain.model.NetworkFilter
import com.datameter.domain.model.PeriodRangeResolver
import com.datameter.domain.model.PeriodSummary
import com.datameter.domain.model.PermissionStatus
import com.datameter.domain.model.UsagePeriod
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

interface HomeUsageRepository {
    suspend fun loadHomeState(
        networkFilter: NetworkFilter,
        period: UsagePeriod,
    ): HomeViewState

    suspend fun loadPrimaryHomeState(
        networkFilter: NetworkFilter,
        period: UsagePeriod,
    ): HomeViewState

    suspend fun loadTotalHomeState(
        networkFilter: NetworkFilter,
        period: UsagePeriod,
    ): HomeViewState

    suspend fun loadPeriodSummaries(
        networkFilter: NetworkFilter,
        selectedPeriod: UsagePeriod,
        selectedPeriodTotalBytes: Long,
    ): List<PeriodSummary>
}

class DefaultHomeUsageRepository(
    private val usageAccessManager: UsageAccessChecker,
    private val usageDataSource: NetworkUsageDataSource,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : HomeUsageRepository {
    override suspend fun loadHomeState(
        networkFilter: NetworkFilter,
        period: UsagePeriod,
    ): HomeViewState {
        val primaryState = loadPrimaryHomeState(networkFilter, period)
        if (primaryState.permissionStatus != PermissionStatus.Granted) {
            return primaryState
        }

        return primaryState.copy(
            periodSummaries = loadPeriodSummaries(
                networkFilter = networkFilter,
                selectedPeriod = period,
                selectedPeriodTotalBytes = primaryState.totalBytes,
            ),
            isLoading = false,
        )
    }

    override suspend fun loadPrimaryHomeState(
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
        val periodElapsedMillis = (selectedRange.endMillis - selectedRange.startMillis).coerceAtLeast(0L)
        val selectedSnapshot = usageDataSource.query(networkFilter, selectedRange)
        val usageRows = selectedSnapshot.rows
            .filter { it.totalBytes > 0L }
            .sortedByDescending { it.totalBytes }

        return HomeViewState(
            selectedNetworkFilter = networkFilter,
            selectedPeriod = period,
            totalBytes = selectedSnapshot.total.totalBytes,
            periodSummaries = UsagePeriod.entries.map { summaryPeriod ->
                PeriodSummary(
                    period = summaryPeriod,
                    totalBytes = if (summaryPeriod == period) {
                        selectedSnapshot.total.totalBytes
                    } else {
                        0L
                    },
                )
            },
            usageRows = usageRows,
            timelineBuckets = selectedSnapshot.timelineBuckets,
            primaryInsight = HomeInsightBuilder.build(
                rows = usageRows,
                totalBytes = selectedSnapshot.total.totalBytes,
                networkFilter = networkFilter,
                period = period,
                periodElapsedMillis = periodElapsedMillis,
            ),
            permissionStatus = PermissionStatus.Granted,
            dataFreshness = DataFreshness.Fresh(now),
            periodElapsedMillis = periodElapsedMillis,
            isLoading = false,
        )
    }

    override suspend fun loadTotalHomeState(
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
        val periodElapsedMillis = (selectedRange.endMillis - selectedRange.startMillis).coerceAtLeast(0L)
        val totalBytes = usageDataSource.queryTotal(networkFilter, selectedRange).totalBytes

        return HomeViewState(
            selectedNetworkFilter = networkFilter,
            selectedPeriod = period,
            totalBytes = totalBytes,
            periodSummaries = UsagePeriod.entries.map { summaryPeriod ->
                PeriodSummary(
                    period = summaryPeriod,
                    totalBytes = if (summaryPeriod == period) totalBytes else 0L,
                )
            },
            usageRows = emptyList(),
            timelineBuckets = emptyList(),
            primaryInsight = HomeInsightBuilder.build(
                rows = emptyList(),
                totalBytes = totalBytes,
                networkFilter = networkFilter,
                period = period,
                periodElapsedMillis = periodElapsedMillis,
            ),
            permissionStatus = PermissionStatus.Granted,
            dataFreshness = DataFreshness.Fresh(now),
            periodElapsedMillis = periodElapsedMillis,
            isLoading = true,
        )
    }

    override suspend fun loadPeriodSummaries(
        networkFilter: NetworkFilter,
        selectedPeriod: UsagePeriod,
        selectedPeriodTotalBytes: Long,
    ): List<PeriodSummary> = coroutineScope {
        if (!usageAccessManager.hasUsageAccess()) {
            return@coroutineScope HomeViewState.initial(networkFilter, selectedPeriod).periodSummaries
        }

        val now = clock()
        UsagePeriod.entries.map { summaryPeriod ->
            async {
                if (summaryPeriod == selectedPeriod) {
                    PeriodSummary(summaryPeriod, selectedPeriodTotalBytes)
                } else {
                    val range = PeriodRangeResolver.resolve(summaryPeriod, now)
                    PeriodSummary(
                        period = summaryPeriod,
                        totalBytes = usageDataSource.queryTotal(networkFilter, range).totalBytes,
                    )
                }
            }
        }.awaitAll()
    }

}
