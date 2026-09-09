package com.datameter.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

enum class NetworkFilter(val label: String) {
    Mobile("Mobile data"),
    Wifi("Wi-Fi"),
    MobileAndWifi("Mobile + Wi-Fi");

    val insightLabel: String
        get() = when (this) {
            Mobile -> "mobile data"
            Wifi -> "Wi-Fi data"
            MobileAndWifi -> "mobile and Wi-Fi data"
        }
}

enum class UsagePeriod(val label: String, val insightLabel: String) {
    Today("Today", "today"),
    Week("This week", "this week"),
    Month("This month", "this month");
}

data class DateRange(
    val startMillis: Long,
    val endMillis: Long,
)

object PeriodRangeResolver {
    fun resolve(
        period: UsagePeriod,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): DateRange {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val today = now.toLocalDate()
        val startDate = when (period) {
            UsagePeriod.Today -> today
            UsagePeriod.Week -> startOfWeek(today, locale)
            UsagePeriod.Month -> today.withDayOfMonth(1)
        }

        return DateRange(
            startMillis = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            endMillis = nowMillis,
        )
    }

    private fun startOfWeek(today: LocalDate, locale: Locale): LocalDate {
        val firstDay = WeekFields.of(locale).firstDayOfWeek
        val current = today.dayOfWeek
        val daysFromStart = distanceFromWeekStart(firstDay, current)
        return today.minusDays(daysFromStart.toLong())
    }

    private fun distanceFromWeekStart(firstDay: DayOfWeek, current: DayOfWeek): Int {
        return (7 + current.value - firstDay.value) % 7
    }
}

data class ByteCount(
    val rxBytes: Long,
    val txBytes: Long,
) {
    val totalBytes: Long
        get() = rxBytes.coerceAtLeast(0L) + txBytes.coerceAtLeast(0L)

    operator fun plus(other: ByteCount): ByteCount {
        return ByteCount(
            rxBytes = rxBytes.coerceAtLeast(0L) + other.rxBytes.coerceAtLeast(0L),
            txBytes = txBytes.coerceAtLeast(0L) + other.txBytes.coerceAtLeast(0L),
        )
    }

    companion object {
        val Zero = ByteCount(0L, 0L)
    }
}

enum class UsageRowKind {
    App,
    Hotspot,
    System,
    RemovedApps,
}

data class UsageRow(
    val id: String,
    val label: String,
    val kind: UsageRowKind,
    val rxBytes: Long,
    val txBytes: Long,
    val uid: Int? = null,
    val measuredExactly: Boolean = true,
) {
    val totalBytes: Long
        get() = rxBytes.coerceAtLeast(0L) + txBytes.coerceAtLeast(0L)

    fun plus(other: UsageRow): UsageRow {
        return copy(
            rxBytes = rxBytes.coerceAtLeast(0L) + other.rxBytes.coerceAtLeast(0L),
            txBytes = txBytes.coerceAtLeast(0L) + other.txBytes.coerceAtLeast(0L),
            uid = uid ?: other.uid,
            measuredExactly = measuredExactly && other.measuredExactly,
        )
    }
}

data class TimelineBucket(
    val startMillis: Long,
    val endMillis: Long,
    val rxBytes: Long,
    val txBytes: Long,
) {
    val totalBytes: Long
        get() = rxBytes.coerceAtLeast(0L) + txBytes.coerceAtLeast(0L)
}

data class UsageSnapshot(
    val total: ByteCount,
    val rows: List<UsageRow>,
    val timelineBuckets: List<TimelineBucket>,
)

data class PeriodSummary(
    val period: UsagePeriod,
    val totalBytes: Long,
)

enum class PermissionStatus {
    Unknown,
    Granted,
    Missing,
}

sealed interface DataFreshness {
    data object Loading : DataFreshness
    data object PermissionMissing : DataFreshness
    data class Fresh(val updatedAtMillis: Long) : DataFreshness
    data class Error(val message: String) : DataFreshness
}

enum class AuditTone {
    Waiting,
    LooksNormal,
    UnusualDifference,
}

data class AuditAssessment(
    val tone: AuditTone,
    val label: String,
)

object AuditMath {
    private const val TOLERANCE_BYTES = 100L * 1_000L * 1_000L
    private const val TOLERANCE_PERCENT = 0.05

    fun assess(measuredBytes: Long, deductedBytes: Long?): AuditAssessment {
        if (deductedBytes == null) {
            return AuditAssessment(AuditTone.Waiting, "Enter latest balance to compare")
        }

        val baseline = maxOf(measuredBytes, deductedBytes, 1L)
        val tolerance = maxOf(TOLERANCE_BYTES, (baseline * TOLERANCE_PERCENT).toLong())
        val difference = deductedBytes - measuredBytes

        return if (abs(difference) <= tolerance) {
            AuditAssessment(AuditTone.LooksNormal, "Deduction matches what you used")
        } else if (difference > 0L) {
            AuditAssessment(AuditTone.UnusualDifference, "Network deducted more than you used")
        } else {
            AuditAssessment(AuditTone.UnusualDifference, "You used more than network deducted")
        }
    }
}

data class HomeViewState(
    val selectedNetworkFilter: NetworkFilter,
    val selectedPeriod: UsagePeriod,
    val totalBytes: Long,
    val periodSummaries: List<PeriodSummary>,
    val usageRows: List<UsageRow>,
    val timelineBuckets: List<TimelineBucket>,
    val primaryInsight: String,
    val permissionStatus: PermissionStatus,
    val dataFreshness: DataFreshness,
    val periodElapsedMillis: Long = Long.MAX_VALUE,
    val isLoading: Boolean = false,
) {
    companion object {
        fun initial(
            networkFilter: NetworkFilter = NetworkFilter.Mobile,
            period: UsagePeriod = UsagePeriod.Today,
        ): HomeViewState {
            return HomeViewState(
                selectedNetworkFilter = networkFilter,
                selectedPeriod = period,
                totalBytes = 0L,
                periodSummaries = UsagePeriod.entries.map { PeriodSummary(it, 0L) },
                usageRows = emptyList(),
                timelineBuckets = emptyList(),
                primaryInsight = "Grant Usage Access to start measuring.",
                permissionStatus = PermissionStatus.Unknown,
                dataFreshness = DataFreshness.Loading,
                periodElapsedMillis = 0L,
                isLoading = true,
            )
        }
    }
}
