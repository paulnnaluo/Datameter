package com.datameter.domain

import com.datameter.data.audit.AuditRepository
import com.datameter.data.audit.AuditSession
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
            auditRepository = FakeAuditRepository(),
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
            auditRepository = FakeAuditRepository(),
            clock = { FIXED_NOW },
        )

        val state = repository.loadHomeState(NetworkFilter.Mobile, UsagePeriod.Today)

        assertEquals(PermissionStatus.Granted, state.permissionStatus)
        assertEquals(NetworkFilter.Mobile, state.selectedNetworkFilter)
        assertEquals(1_000L, state.totalBytes)
        assertEquals("YouTube", state.usageRows.first().label)
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
        override suspend fun query(filter: NetworkFilter, range: DateRange): UsageSnapshot = snapshot
    }

    private class FakeAuditRepository : AuditRepository {
        override fun currentSession(): AuditSession? = null

        override fun startSession(
            networkName: String,
            startingBalanceBytes: Long,
            startingDeviceMeterBytes: Long,
            startedAtMillis: Long,
        ) = Unit

        override fun recordBalance(balanceBytes: Long, checkedAtMillis: Long) = Unit

        override fun clearSession() = Unit
    }

    private companion object {
        const val FIXED_NOW = 1_775_000_000_000L
    }
}
