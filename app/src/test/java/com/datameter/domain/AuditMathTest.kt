package com.datameter.domain

import com.datameter.domain.model.AuditMath
import com.datameter.domain.model.AuditTone
import kotlin.test.Test
import kotlin.test.assertEquals

class AuditMathTest {
    @Test
    fun `close network deduction looks normal`() {
        val assessment = AuditMath.assess(
            measuredBytes = 3_420_000_000L,
            deductedBytes = 3_510_000_000L,
        )

        assertEquals(AuditTone.LooksNormal, assessment.tone)
        assertEquals("Deduction matches what you used", assessment.label)
    }

    @Test
    fun `large gap is unusual`() {
        val assessment = AuditMath.assess(
            measuredBytes = 3_400_000_000L,
            deductedBytes = 5_800_000_000L,
        )

        assertEquals(AuditTone.UnusualDifference, assessment.tone)
        assertEquals("Network deducted more than you used", assessment.label)
    }

    @Test
    fun `missing second balance remains in measuring state`() {
        val assessment = AuditMath.assess(
            measuredBytes = 500_000_000L,
            deductedBytes = null,
        )

        assertEquals(AuditTone.Waiting, assessment.tone)
        assertEquals("Enter latest balance to compare", assessment.label)
    }
}
