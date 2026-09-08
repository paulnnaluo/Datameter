package com.datameter.domain

import com.datameter.data.alerts.AlertSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsageAlertEvaluatorTest {
    @Test
    fun `daily alert triggers when mobile usage reaches limit`() {
        val alerts = UsageAlertEvaluator.evaluate(
            settings = AlertSettings(
                dailyLimitEnabled = true,
                dailyLimitBytes = 1_000_000_000L,
                hourlySpikeEnabled = false,
            ),
            todayMobileBytes = 1_250_000_000L,
            lastHourMobileBytes = 0L,
        )

        assertEquals(UsageAlertType.DailyLimit, alerts.single().type)
        assertEquals("Daily data alert", alerts.single().title)
    }

    @Test
    fun `hourly alert triggers when recent mobile usage reaches limit`() {
        val alerts = UsageAlertEvaluator.evaluate(
            settings = AlertSettings(
                dailyLimitEnabled = false,
                hourlySpikeEnabled = true,
                hourlySpikeBytes = 500_000_000L,
            ),
            todayMobileBytes = 0L,
            lastHourMobileBytes = 620_000_000L,
        )

        assertEquals(UsageAlertType.HourlySpike, alerts.single().type)
        assertEquals("High hourly usage", alerts.single().title)
    }

    @Test
    fun `disabled alerts do not trigger`() {
        val alerts = UsageAlertEvaluator.evaluate(
            settings = AlertSettings(
                dailyLimitEnabled = false,
                dailyLimitBytes = 1L,
                hourlySpikeEnabled = false,
                hourlySpikeBytes = 1L,
            ),
            todayMobileBytes = Long.MAX_VALUE,
            lastHourMobileBytes = Long.MAX_VALUE,
        )

        assertTrue(alerts.isEmpty())
    }
}
