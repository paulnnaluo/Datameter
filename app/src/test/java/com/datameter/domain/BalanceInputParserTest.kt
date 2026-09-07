package com.datameter.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BalanceInputParserTest {
    @Test
    fun `parses decimal gigabytes into decimal bytes`() {
        assertEquals(18_600_000_000L, BalanceInputParser.parseGigabytes("18.6"))
    }

    @Test
    fun `rejects blank and negative values`() {
        assertNull(BalanceInputParser.parseGigabytes(""))
        assertNull(BalanceInputParser.parseGigabytes("-1"))
    }
}
