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
    ): String {
        if (totalBytes <= 0L) {
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

        return "Android could not identify the apps behind this usage ${period.insightLabel}."
    }
}
