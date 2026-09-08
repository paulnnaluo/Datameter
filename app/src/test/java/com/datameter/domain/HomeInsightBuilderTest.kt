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

    @Test
    fun `freshly started periods get an aware zero state insight`() {
        val insight = HomeInsightBuilder.build(
            rows = emptyList(),
            totalBytes = 0L,
            networkFilter = NetworkFilter.Mobile,
            period = UsagePeriod.Today,
            periodElapsedMillis = 5 * 60 * 1000L,
        )

        assertEquals(
            "Today just started. Datameter needs a little usage before this becomes useful.",
            insight,
        )
    }

    @Test
    fun `measured rows get a concrete total insight`() {
        val rows = listOf(
            UsageRow(
                id = "measured_mobile_data",
                label = "Measured mobile data",
                kind = UsageRowKind.Measured,
                rxBytes = 3_700_000_000L,
                txBytes = 0L,
                measuredExactly = false,
            ),
        )

        val insight = HomeInsightBuilder.build(
            rows = rows,
            totalBytes = 3_700_000_000L,
            networkFilter = NetworkFilter.Mobile,
            period = UsagePeriod.Today,
        )

        assertEquals("Datameter measured 3.70 GB of your mobile data today.", insight)
    }

    @Test
    fun `measured totals without rows still get a concrete insight`() {
        val insight = HomeInsightBuilder.build(
            rows = emptyList(),
            totalBytes = 1_000L,
            networkFilter = NetworkFilter.Mobile,
            period = UsagePeriod.Today,
            periodElapsedMillis = 5 * 60 * 1000L,
        )

        assertEquals("Datameter measured 1 KB of your mobile data today.", insight)
    }
}
