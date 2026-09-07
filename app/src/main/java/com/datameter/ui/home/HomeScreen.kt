package com.datameter.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.datameter.domain.ByteFormatter
import com.datameter.domain.model.AuditHomeStatus
import com.datameter.domain.model.AuditTone
import com.datameter.domain.model.DataFreshness
import com.datameter.domain.model.HomeViewState
import com.datameter.domain.model.PermissionStatus
import com.datameter.domain.model.PeriodSummary
import com.datameter.domain.model.TimelineBucket
import com.datameter.domain.model.UsagePeriod
import com.datameter.domain.model.UsageRow
import com.datameter.domain.model.UsageRowKind
import com.datameter.ui.components.UsageBar
import com.datameter.ui.components.UsageSparkline
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    state: HomeViewState,
    onPeriodSelected: (UsagePeriod) -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenTimeline: () -> Unit,
    onOpenAudit: () -> Unit,
    onRefresh: () -> Unit,
) {
    if (state.permissionStatus == PermissionStatus.Missing) {
        PermissionRequiredContent(
            onOpenUsageAccess = onOpenUsageAccess,
            onOpenAppSettings = onOpenAppSettings,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 14.dp, 20.dp, 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (state.isLoading) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            PrimaryMeterSection(
                state = state,
                onPeriodSelected = onPeriodSelected,
            )
        }

        item {
            InsightSection(insight = state.primaryInsight)
        }

        item {
            UsageBreakdownSection(rows = state.usageRows)
        }

        item {
            TimelinePreviewSection(
                buckets = state.timelineBuckets,
                onOpenTimeline = onOpenTimeline,
            )
        }

        item {
            AuditEntrySection(
                status = state.auditStatus,
                onOpenAudit = onOpenAudit,
            )
        }

        item {
            DataFreshnessFooter(
                freshness = state.dataFreshness,
                onRefresh = onRefresh,
            )
        }
    }
}

@Composable
fun TimelineScreen(
    state: HomeViewState,
    onOpenUsageAccess: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    if (state.permissionStatus == PermissionStatus.Missing) {
        PermissionRequiredContent(
            onOpenUsageAccess = onOpenUsageAccess,
            onOpenAppSettings = onOpenAppSettings,
        )
        return
    }

    val spikes = state.timelineBuckets.sortedByDescending { it.totalBytes }.take(8)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 14.dp, 20.dp, 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionHeader(
                title = "${state.selectedPeriod.label} - ${ByteFormatter.format(state.totalBytes)}",
                subtitle = state.selectedNetworkFilter.label,
            )
        }

        item {
            Surface(
                shape = datameterPanelShape(),
                color = datameterPanelColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    UsageSparkline(buckets = state.timelineBuckets)
                    Text(
                        text = if (state.timelineBuckets.isEmpty()) {
                            "No usage measured in this period."
                        } else {
                            "Biggest movement: ${formatBucketLabel(spikes.first())}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (spikes.isNotEmpty()) {
            item {
                GroupedTimelineList(buckets = spikes)
            }
        }
    }
}

@Composable
fun AppsScreen(
    state: HomeViewState,
    onOpenUsageAccess: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    if (state.permissionStatus == PermissionStatus.Missing) {
        PermissionRequiredContent(
            onOpenUsageAccess = onOpenUsageAccess,
            onOpenAppSettings = onOpenAppSettings,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 14.dp, 20.dp, 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                title = "Who used it",
                subtitle = "${state.selectedPeriod.label} - ${state.selectedNetworkFilter.label}",
            )
        }

        if (state.usageRows.isEmpty()) {
            item {
                EmptyUsagePanel("No app usage identified in this period.")
            }
        } else {
            item {
                GroupedUsageList(rows = state.usageRows)
            }
        }
    }
}

@Composable
fun PermissionRequiredContent(
    onOpenUsageAccess: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Enable Usage Access",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Datameter needs Android Usage Access to show mobile, Wi-Fi, app, and hotspot usage from this phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        PermissionStepList()
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onOpenUsageAccess,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open Usage Access")
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onOpenAppSettings,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text("Open Datameter settings")
        }
    }
}

@Composable
private fun PermissionStepList() {
    val steps = listOf(
        "Tap Datameter in Usage Access.",
        "Turn on Permit usage access.",
        "If Android shows Restricted settings, open Datameter settings first.",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        steps.forEachIndexed { index, step ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = groupedItemShape(index, steps.size),
                color = datameterListItemColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}",
                        modifier = Modifier.width(28.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = step,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryMeterSection(
    state: HomeViewState,
    onPeriodSelected: (UsagePeriod) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Text(
                text = state.selectedPeriod.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = ByteFormatter.format(state.totalBytes),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${state.selectedNetworkFilter.label} used",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.periodSummaries.forEach { summary ->
                PeriodSummaryChip(
                    summary = summary,
                    selected = summary.period == state.selectedPeriod,
                    onSelected = { onPeriodSelected(summary.period) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PeriodSummaryChip(
    summary: PeriodSummary,
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier
            .heightIn(min = 60.dp)
            .clip(shape)
            .clickable(onClick = onSelected),
        shape = shape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            datameterPanelColor()
        },
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = summary.period.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = ByteFormatter.format(summary.totalBytes),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.76f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InsightSection(insight: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = datameterPanelShape(),
    ) {
        Text(
            text = insight,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun UsageBreakdownSection(rows: List<UsageRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Where it went",
            subtitle = null,
        )

        if (rows.isEmpty()) {
            EmptyUsagePanel("No app usage identified in this period.")
        } else {
            GroupedUsageList(rows = rows)
        }
    }
}

@Composable
private fun GroupedUsageList(rows: List<UsageRow>) {
    val maxBytes = rows.maxOfOrNull { it.totalBytes }.coerceAtLeastOne()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        rows.forEachIndexed { index, row ->
            UsageRowItem(
                row = row,
                maxBytes = maxBytes,
                shape = groupedItemShape(index, rows.size),
            )
        }
    }
}

@Composable
private fun UsageRowItem(
    row: UsageRow,
    maxBytes: Long,
    shape: RoundedCornerShape,
) {
    val color = usageKindColor(row.kind)
    Surface(
        shape = shape,
        color = datameterListItemColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UsageSourceIcon(row = row, color = color)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = row.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = ByteFormatter.format(row.totalBytes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            UsageBar(
                fraction = row.totalBytes.toFloat() / maxBytes.toFloat(),
                color = color,
            )
        }
    }
}

@Composable
private fun TimelinePreviewSection(
    buckets: List<TimelineBucket>,
    onOpenTimeline: () -> Unit,
) {
    val spike = remember(buckets) { buckets.maxByOrNull { it.totalBytes } }

    Surface(
        shape = datameterPanelShape(),
        color = datameterPanelColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "When it moved",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (spike == null) {
                            "No timeline movement yet."
                        } else {
                            "Biggest spike: ${formatBucketLabel(spike)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onOpenTimeline,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = "Open",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            UsageSparkline(buckets = buckets)
        }
    }
}

@Composable
private fun AuditEntrySection(
    status: AuditHomeStatus,
    onOpenAudit: () -> Unit,
) {
    val containerColor = when (status) {
        AuditHomeStatus.Inactive -> datameterPanelColor()
        is AuditHomeStatus.Active -> when (status.assessment.tone) {
            AuditTone.UnusualDifference -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.74f)

            AuditTone.LooksNormal -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)

            AuditTone.Waiting -> datameterPanelColor()
        }
    }

    Surface(
        shape = datameterPanelShape(),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Check my network",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = when (status) {
                            AuditHomeStatus.Inactive -> "Start a measured balance check."
                            is AuditHomeStatus.Active -> "${status.networkName} audit - ${status.assessment.label}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onOpenAudit,
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = "Open",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            if (status is AuditHomeStatus.Active) {
                AuditMetricRow("Device measured", ByteFormatter.format(status.measuredBytes))
                status.deductedBytes?.let { AuditMetricRow("Network deducted", ByteFormatter.format(it)) }
                status.differenceBytes?.let { AuditMetricRow("Difference", ByteFormatter.formatSigned(it)) }
            }
        }
    }
}

@Composable
private fun DataFreshnessFooter(
    freshness: DataFreshness,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val text = when (freshness) {
            DataFreshness.Loading -> "Updating..."
            DataFreshness.PermissionMissing -> "Usage Access required"
            is DataFreshness.Fresh -> "Updated ${formatClock(freshness.updatedAtMillis)}"
            is DataFreshness.Error -> freshness.message
        }

        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onRefresh,
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Refresh",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun GroupedTimelineList(buckets: List<TimelineBucket>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        buckets.forEachIndexed { index, bucket ->
            TimelineBucketRow(
                bucket = bucket,
                shape = groupedItemShape(index, buckets.size),
            )
        }
    }
}

@Composable
private fun TimelineBucketRow(
    bucket: TimelineBucket,
    shape: RoundedCornerShape,
) {
    Surface(
        shape = shape,
        color = datameterListItemColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatBucketLabel(bucket),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Download + upload",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = ByteFormatter.format(bucket.totalBytes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun EmptyUsagePanel(message: String) {
    Surface(
        shape = datameterPanelShape(),
        color = datameterPanelColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AuditMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun datameterPanelColor(): Color {
    return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
}

@Composable
private fun datameterListItemColor(): Color {
    return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)
}

private fun datameterPanelShape(): RoundedCornerShape {
    return RoundedCornerShape(24.dp)
}

private fun groupedItemShape(index: Int, totalItems: Int): RoundedCornerShape {
    return when {
        totalItems <= 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomEnd = 4.dp,
            bottomStart = 4.dp,
        )
        index == totalItems - 1 -> RoundedCornerShape(
            topStart = 4.dp,
            topEnd = 4.dp,
            bottomEnd = 24.dp,
            bottomStart = 24.dp,
        )
        else -> RoundedCornerShape(4.dp)
    }
}

@Composable
private fun usageKindColor(kind: UsageRowKind): Color {
    return when (kind) {
        UsageRowKind.Hotspot -> MaterialTheme.colorScheme.tertiary
        UsageRowKind.App -> MaterialTheme.colorScheme.primary
        UsageRowKind.System -> MaterialTheme.colorScheme.secondary
        UsageRowKind.RemovedApps -> MaterialTheme.colorScheme.outline
    }
}

@Composable
private fun UsageSourceIcon(row: UsageRow, color: Color) {
    val shape = MaterialTheme.shapes.medium
    val iconBitmap = rememberAppIcon(row = row)

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .clip(MaterialTheme.shapes.small),
            )
        } else {
            Icon(
                imageVector = usageVector(row.kind),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = color,
            )
        }
    }
}

@Composable
private fun rememberAppIcon(row: UsageRow): ImageBitmap? {
    if (row.kind != UsageRowKind.App) return null

    val context = LocalContext.current
    return remember(row.id) {
        val packageName = row.packageNameOrNull() ?: return@remember null
        context.loadAppIcon(packageName)
    }
}

private fun usageVector(kind: UsageRowKind): ImageVector {
    return when (kind) {
        UsageRowKind.Hotspot -> Icons.Filled.WifiTethering
        UsageRowKind.System -> Icons.Filled.Settings
        UsageRowKind.RemovedApps -> Icons.Filled.Delete
        UsageRowKind.App -> Icons.Filled.Apps
    }
}

private fun Context.loadAppIcon(packageName: String): ImageBitmap? {
    return runCatching {
        packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap()
    }.getOrNull()
}

private fun Drawable.toBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(ICON_BITMAP_SIZE_PX, ICON_BITMAP_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

private fun UsageRow.packageNameOrNull(): String? {
    val packageName = id.removePrefix(APP_ROW_PREFIX)
    return packageName.takeIf {
        id.startsWith(APP_ROW_PREFIX) && it.isNotBlank() && !it.startsWith("uid:")
    }
}

private fun formatBucketLabel(bucket: TimelineBucket): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(bucket.startMillis).atZone(zone)
    val end = Instant.ofEpochMilli(bucket.endMillis).atZone(zone)
    val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    return "${formatter.format(start)}-${formatter.format(end)}"
}

private fun formatClock(millis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    return formatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}

private fun Long?.coerceAtLeastOne(): Long {
    return this?.coerceAtLeast(1L) ?: 1L
}

private const val APP_ROW_PREFIX = "app:"
private const val ICON_BITMAP_SIZE_PX = 96
