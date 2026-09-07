package com.datameter.domain

import com.datameter.domain.model.NetworkFilter
import com.datameter.domain.model.UsagePeriod
import com.datameter.domain.model.UsageRow
import com.datameter.domain.model.UsageRowKind
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeInsightBuilderTest {
    @Test
    fun `hotspot gets the primary insight when it is measured`() {
        val rows = listOf(
            UsageRow(
                id = "hotspot",
                label = "Hotspot / Tethering",
                kind = UsageRowKind.Hotspot,
                rxBytes = 400L,
                txBytes = 200L,
            ),
            UsageRow(
                id = "app:youtube",
                label = "YouTube",
                kind = UsageRowKind.App,
                rxBytes = 300L,
                txBytes = 100L,
            ),
        )

        val insight = HomeInsightBuilder.build(
            rows = rows,
            totalBytes = 1_000L,
            networkFilter = NetworkFilter.Mobile,
            period = UsagePeriod.Today,
        )

        assertEquals("Your hotspot used 60% of your mobile data today.", insight)
    }

    @Test
    fun `top app gets the primary insight when hotspot is absent`() {
        val rows = listOf(
            UsageRow(
                id = "app:youtube",
                label = "YouTube",
                kind = UsageRowKind.App,
                rxBytes = 700L,
                txBytes = 0L,
            ),
        )

        val insight = HomeInsightBuilder.build(
            rows = rows,
            totalBytes = 1_000L,
            networkFilter = NetworkFilter.Mobile,
            period = UsagePeriod.Week,
        )

        assertEquals("YouTube used 70% of your mobile data this week.", insight)
    }

    @Test
    fun `empty periods get a calm zero state insight`() {
        val insight = HomeInsightBuilder.build(
            rows = emptyList(),
            totalBytes = 0L,
            networkFilter = NetworkFilter.Wifi,
            period = UsagePeriod.Week,
        )

        assertEquals("No Wi-Fi data measured this week.", insight)
    }
}
