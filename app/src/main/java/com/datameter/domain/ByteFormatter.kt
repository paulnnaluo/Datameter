package com.datameter.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs

object DataUnits {
    const val KILOBYTE = 1_000L
    const val MEGABYTE = 1_000L * KILOBYTE
    const val GIGABYTE = 1_000L * MEGABYTE
}

object ByteFormatter {
    private val units = listOf(
        "B" to 1L,
        "KB" to DataUnits.KILOBYTE,
        "MB" to DataUnits.MEGABYTE,
        "GB" to DataUnits.GIGABYTE,
        "TB" to 1_000L * DataUnits.GIGABYTE,
    )

    fun format(bytes: Long): String {
        val safeBytes = bytes.coerceAtLeast(0L)
        val unit = units.last { safeBytes >= it.second || it.second == 1L }
        if (unit.second == 1L) return "$safeBytes B"

        val value = safeBytes.toDouble() / unit.second.toDouble()
        val decimals = when {
            value >= 100 -> 0
            value >= 10 -> 1
            else -> 2
        }
        return "%.${decimals}f %s".format(Locale.US, value, unit.first)
            .replace(".0 ", " ")
            .replace(".00 ", " ")
    }

    fun formatSigned(bytes: Long): String {
        val sign = when {
            bytes > 0L -> "+"
            bytes < 0L -> "-"
            else -> ""
        }
        return sign + format(abs(bytes))
    }

    fun percent(partBytes: Long, totalBytes: Long): String {
        if (totalBytes <= 0L) return "0%"
        val value = (partBytes.toDouble() / totalBytes.toDouble()) * 100.0
        return if (value >= 10.0) {
            "%.0f%%".format(Locale.US, value)
        } else {
            "%.1f%%".format(Locale.US, value)
        }
    }
}

object BalanceInputParser {
    fun parseGigabytes(input: String): Long? {
        val normalized = input.trim().replace(",", "")
        if (normalized.isBlank()) return null

        return runCatching {
            BigDecimal(normalized)
                .multiply(BigDecimal(DataUnits.GIGABYTE))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
                .takeIf { it >= 0L }
        }.getOrNull()
    }
}
