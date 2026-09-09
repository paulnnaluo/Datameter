package com.datameter.domain

import com.datameter.domain.model.NetworkFilter
import com.datameter.domain.model.UsagePeriod
import com.datameter.domain.model.UsageRow
import com.datameter.domain.model.UsageRowKind

object HomeInsightBuilder {
    fun build(
        rows: List<UsageRow>,
        totalBytes: Long,
        networkFilter: NetworkFilter,
        period: UsagePeriod,
        periodElapsedMillis: Long = Long.MAX_VALUE,
    ): String {
        if (totalBytes <= 0L) {
            if (period.hasJustStarted(periodElapsedMillis)) {
                return "${period.label} just started. Datameter needs a little usage before this becomes useful."
            }

            return "No ${networkFilter.insightLabel} measured ${period.insightLabel}."
        }

        val hotspot = rows.firstOrNull { it.kind == UsageRowKind.Hotspot && it.totalBytes > 0L }
        if (hotspot != null) {
            return "Your hotspot used ${ByteFormatter.percent(hotspot.totalBytes, totalBytes)} of your ${networkFilter.insightLabel} ${period.insightLabel}."
        }

        val top = rows.firstOrNull { it.totalBytes > 0L }
        if (top != null) {
            return "${top.label} used ${ByteFormatter.percent(top.totalBytes, totalBytes)} of your ${networkFilter.insightLabel} ${period.insightLabel}."
        }

        return "Datameter measured ${ByteFormatter.format(totalBytes)} of your ${networkFilter.insightLabel} ${period.insightLabel}."
    }

    private fun UsagePeriod.hasJustStarted(elapsedMillis: Long): Boolean {
        return elapsedMillis in 0L until EARLY_PERIOD_WINDOW_MILLIS
    }

    private const val EARLY_PERIOD_WINDOW_MILLIS = 60L * 60L * 1000L
}
