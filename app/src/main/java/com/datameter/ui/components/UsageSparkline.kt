package com.datameter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.datameter.domain.model.TimelineBucket

@Composable
fun UsageSparkline(
    buckets: List<TimelineBucket>,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val spikeColor = MaterialTheme.colorScheme.tertiary
    val guideColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.64f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp),
    ) {
        val baseline = size.height - 2.dp.toPx()
        val guideStroke = 1.dp.toPx()

        listOf(0.34f, 0.68f, 1f).forEach { fraction ->
            val y = size.height * fraction
            drawLine(
                color = guideColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = guideStroke,
            )
        }

        drawLine(
            color = guideColor,
            start = Offset(0f, baseline),
            end = Offset(size.width, baseline),
            strokeWidth = guideStroke,
        )

        if (buckets.isEmpty()) return@Canvas

        val maxBytes = buckets.maxOf { it.totalBytes }.coerceAtLeast(1L)
        val gap = 4.dp.toPx()
        val barWidth = ((size.width - gap * (buckets.size - 1)) / buckets.size)
            .coerceAtLeast(3.dp.toPx())
        val spikeIndex = buckets.indexOfFirst { it.totalBytes == maxBytes }

        buckets.forEachIndexed { index, bucket ->
            val ratio = bucket.totalBytes.toFloat() / maxBytes.toFloat()
            val barHeight = (ratio * (size.height - 10.dp.toPx())).coerceAtLeast(4.dp.toPx())
            val left = index * (barWidth + gap)
            val top = baseline - barHeight
            drawRoundRect(
                color = if (index == spikeIndex) spikeColor else barColor.copy(alpha = 0.74f),
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
            )
        }
    }
}

@Composable
fun UsageBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp)
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
                shape = RoundedCornerShape(999.dp),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .align(Alignment.CenterStart)
                .background(color = color, shape = RoundedCornerShape(999.dp)),
        )
    }
}

@Composable
fun UsageKindDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(9.dp)
            .height(9.dp)
            .background(color = color, shape = RoundedCornerShape(999.dp)),
    )
}
