package com.datameter.domain

import com.datameter.data.usage.NetworkUsageDataSource
import com.datameter.data.usage.UsageAccessChecker
import com.datameter.domain.model.ByteCount
import com.datameter.domain.model.DataFreshness
import com.datameter.domain.model.DateRange
import com.datameter.domain.model.NetworkFilter
import com.datameter.domain.model.PermissionStatus
import com.datameter.domain.model.TimelineBucket
import com.datameter.domain.model.UsagePeriod
import com.datameter.domain.model.UsageRow
import com.datameter.domain.model.UsageRowKind
import com.datameter.domain.model.UsageSnapshot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultHomeUsageRepositoryTest {
    @Test
    fun `permission missing returns no fake usage`() = runTest {
        val repository = DefaultHomeUsageRepository(
            usageAccessManager = FakeUsageAccessChecker(hasAccess = false),
            usageDataSource = FakeUsageDataSource(),
            clock = { FIXED_NOW },
        )

        val state = repository.loadHomeState(NetworkFilter.Mobile, UsagePeriod.Today)

        assertEquals(PermissionStatus.Missing, state.permissionStatus)
        assertEquals(0L, state.totalBytes)
        assertTrue(state.usageRows.isEmpty())
        assertIs<DataFreshness.PermissionMissing>(state.dataFreshness)
    }

    @Test
    fun `mobile home state uses selected mobile snapshot`() = runTest {
        val repository = DefaultHomeUsageRepository(
            usageAccessManager = FakeUsageAccessChecker(hasAccess = true),
            usageDataSource = FakeUsageDataSource(
                snapshot = UsageSnapshot(
                    total = ByteCount(rxBytes = 800L, txBytes = 200L),
                    rows = listOf(
                        UsageRow(
                            id = "app:youtube",
                            label = "YouTube",
                            kind = UsageRowKind.App,
                            rxBytes = 700L,
                            txBytes = 100L,
                        ),
                    ),
                    timelineBuckets = listOf(
                        TimelineBucket(
                            startMillis = FIXED_NOW - 60 * 60 * 1000,
                            endMillis = FIXED_NOW,
                            rxBytes = 800L,
                            txBytes = 200L,
                        ),
                    ),
                ),
            ),
            clock = { FIXED_NOW },
        )

        val state = repository.loadHomeState(NetworkFilter.Mobile, UsagePeriod.Today)

        assertEquals(PermissionStatus.Granted, state.permissionStatus)
        assertEquals(NetworkFilter.Mobile, state.selectedNetworkFilter)
        assertEquals(1_000L, state.totalBytes)
        assertEquals("YouTube", state.usageRows.first().label)
    }

    @Test
    fun `measured total is shown even when app rows are missing`() = runTest {
        val repository = DefaultHomeUsageRepository(
            usageAccessManager = FakeUsageAccessChecker(hasAccess = true),
            usageDataSource = FakeUsageDataSource(
                snapshot = UsageSnapshot(
                    total = ByteCount(rxBytes = 3_700_000_000L, txBytes = 0L),
                    rows = emptyList(),
                    timelineBuckets = emptyList(),
                ),
            ),
            clock = { FIXED_NOW },
        )

        val state = repository.loadPrimaryHomeState(NetworkFilter.Mobile, UsagePeriod.Today)

        assertEquals(1, state.usageRows.size)
        assertEquals("Measured mobile data", state.usageRows.single().label)
        assertEquals(UsageRowKind.Measured, state.usageRows.single().kind)
        assertEquals(3_700_000_000L, state.usageRows.single().totalBytes)
    }

    @Test
    fun `unassigned measured remainder is added to partial app rows`() = runTest {
        val repository = DefaultHomeUsageRepository(
            usageAccessManager = FakeUsageAccessChecker(hasAccess = true),
            usageDataSource = FakeUsageDataSource(
                snapshot = UsageSnapshot(
                    total = ByteCount(rxBytes = 1_000L, txBytes = 0L),
                    rows = listOf(
                        UsageRow(
                            id = "app:youtube",
                            label = "YouTube",
                            kind = UsageRowKind.App,
                            rxBytes = 700L,
                            txBytes = 0L,
                        ),
                    ),
                    timelineBuckets = emptyList(),
                ),
            ),
            clock = { FIXED_NOW },
        )

        val state = repository.loadPrimaryHomeState(NetworkFilter.Mobile, UsagePeriod.Today)

        assertEquals(2, state.usageRows.size)
        assertEquals(1_000L, state.usageRows.sumOf { it.totalBytes })
        assertEquals("Measured mobile data", state.usageRows.last().label)
        assertEquals(300L, state.usageRows.last().totalBytes)
    }

    @Test
    fun `primary home state only queries selected period`() = runTest {
        val usageDataSource = FakeUsageDataSource()
        val repository = DefaultHomeUsageRepository(
            usageAccessManager = FakeUsageAccessChecker(hasAccess = true),
            usageDataSource = usageDataSource,
            clock = { FIXED_NOW },
        )

        repository.loadPrimaryHomeState(NetworkFilter.Mobile, UsagePeriod.Today)

        assertEquals(1, usageDataSource.queryCount)
    }

    @Test
    fun `full home state reuses selected period total for summaries`() = runTest {
        val usageDataSource = FakeUsageDataSource()
        val repository = DefaultHomeUsageRepository(
            usageAccessManager = FakeUsageAccessChecker(hasAccess = true),
            usageDataSource = usageDataSource,
            clock = { FIXED_NOW },
        )

        repository.loadHomeState(NetworkFilter.Mobile, UsagePeriod.Today)

        assertEquals(UsagePeriod.entries.size, usageDataSource.queryCount)
    }

    private class FakeUsageAccessChecker(
        private val hasAccess: Boolean,
    ) : UsageAccessChecker {
        override fun hasUsageAccess(): Boolean = hasAccess
    }

    private class FakeUsageDataSource(
        private val snapshot: UsageSnapshot = UsageSnapshot(
            total = ByteCount.Zero,
            rows = emptyList(),
            timelineBuckets = emptyList(),
        ),
    ) : NetworkUsageDataSource {
        var queryCount = 0
            private set

        override suspend fun query(filter: NetworkFilter, range: DateRange): UsageSnapshot {
            queryCount += 1
            return snapshot
        }
    }

    private companion object {
        const val FIXED_NOW = 1_775_000_000_000L
    }
}
