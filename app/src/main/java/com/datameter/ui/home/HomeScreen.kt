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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.runtime.produceState
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
import com.datameter.ui.control.DataControlUiState
import com.datameter.data.control.DataControlRunStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val appIconCache = ConcurrentHashMap<String, ImageBitmap>()

@Composable
fun HomeScreen(
    state: HomeViewState,
    dataControlState: DataControlUiState,
    onPeriodSelected: (UsagePeriod) -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenTimeline: () -> Unit,
    onRefresh: () -> Unit,
    onEnableDataControl: () -> Unit,
    onDisableDataControl: () -> Unit,
    onUsageRowSelected: (UsageRow) -> Unit,
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
        contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(34.dp),
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
            DataControlHomeSection(
                state = dataControlState,
                onEnableDataControl = onEnableDataControl,
                onDisableDataControl = onDisableDataControl,
            )
        }

        item {
            UsageBreakdownSection(
                rows = state.usageRows,
                period = state.selectedPeriod,
                periodElapsedMillis = state.periodElapsedMillis,
                onUsageRowSelected = onUsageRowSelected,
            )
        }

        item {
            TimelinePreviewSection(
                buckets = state.timelineBuckets,
                period = state.selectedPeriod,
                periodElapsedMillis = state.periodElapsedMillis,
                onOpenTimeline = onOpenTimeline,
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
        contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
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
                            emptyTimelineMessage(
                                period = state.selectedPeriod,
                                periodElapsedMillis = state.periodElapsedMillis,
                            )
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
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
                    loading = state.isLoading &&
                        summary.period != state.selectedPeriod &&
                        summary.totalBytes == 0L,
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
    loading: Boolean,
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
            if (loading) {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .width(44.dp)
                        .height(8.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(contentColor.copy(alpha = 0.18f)),
                )
            } else {
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
}

@Composable
private fun InsightSection(insight: String) {
    val shape = MaterialTheme.shapes.medium
    val alertColor = MaterialTheme.colorScheme.tertiary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        color = alertColor.copy(alpha = 0.08f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = alertColor.copy(alpha = 0.72f),
            )
            Text(
                text = insight,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
            )
        }
    }
}

@Composable
private fun UsageBreakdownSection(
    rows: List<UsageRow>,
    period: UsagePeriod,
    periodElapsedMillis: Long,
    onUsageRowSelected: (UsageRow) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader(
            title = "Where it went",
            subtitle = null,
        )

        if (rows.isEmpty()) {
            EmptyUsagePanel(
                emptyBreakdownMessage(
                    period = period,
                    periodElapsedMillis = periodElapsedMillis,
                ),
            )
        } else {
            GroupedUsageList(
                rows = rows,
                onUsageRowSelected = onUsageRowSelected,
            )
        }
    }
}

@Composable
private fun DataControlHomeSection(
    state: DataControlUiState,
    onEnableDataControl: () -> Unit,
    onDisableDataControl: () -> Unit,
) {
    val title = when (state.runtimeState.status) {
        DataControlRunStatus.Active -> "Data Control active"
        DataControlRunStatus.Standby -> "Data Control ready"
        DataControlRunStatus.Starting -> "Data Control starting"
        DataControlRunStatus.NeedsVpnPermission -> "VPN permission needed"
        DataControlRunStatus.Error -> "Data Control needs attention"
        DataControlRunStatus.Off -> "Data Control off"
    }
    val subtitle = when (state.runtimeState.status) {
        DataControlRunStatus.Active -> {
            if (state.blockedAppsToday > 0) {
                "${state.blockedAppsToday} apps blocked today"
            } else {
                "Tap any app below to control mobile or Wi-Fi access."
            }
        }

        DataControlRunStatus.Starting -> "Preparing local app rules."
        DataControlRunStatus.Standby -> {
            if (state.activeRuleCount > 0 || state.settings.globalAutoBlockEnabled) {
                "Your rules are saved. The VPN starts only for apps Datameter controls."
            } else {
                "Tap an app below to set rules, or turn on app auto-block in Alerts."
            }
        }

        DataControlRunStatus.NeedsVpnPermission -> "Android needs one more confirmation."
        DataControlRunStatus.Error -> state.runtimeState.message ?: "Could not keep app controls running."
        DataControlRunStatus.Off -> {
            if (state.activeRuleCount > 0 || state.settings.globalAutoBlockEnabled) {
                "Your rules are saved. Turn it on to apply them."
            } else {
                "Tap an app below to set rules, then turn Data Control on."
            }
        }
    }
    val active = state.runtimeState.status == DataControlRunStatus.Active
    val enabled = state.settings.enabled

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = datameterPanelShape(),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.64f)
        } else {
            datameterPanelColor()
        },
        contentColor = if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (enabled) {
                OutlinedButton(
                    onClick = onDisableDataControl,
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("Off", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Button(
                    onClick = onEnableDataControl,
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("Turn on", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun GroupedUsageList(
    rows: List<UsageRow>,
    onUsageRowSelected: (UsageRow) -> Unit,
) {
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
                onClick = { onUsageRowSelected(row) },
            )
        }
    }
}

@Composable
private fun UsageRowItem(
    row: UsageRow,
    maxBytes: Long,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    val color = usageKindColor(row.kind)
    val opensDetail = row.kind == UsageRowKind.App && row.uid != null && row.packageNameOrNull() != null
    Surface(
        modifier = Modifier
            .clip(shape)
            .clickable(
                enabled = opensDetail,
                onClick = onClick,
            ),
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
    period: UsagePeriod,
    periodElapsedMillis: Long,
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
                            emptyTimelineMessage(
                                period = period,
                                periodElapsedMillis = periodElapsedMillis,
                            )
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
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

private fun emptyBreakdownMessage(
    period: UsagePeriod,
    periodElapsedMillis: Long,
): String {
    return if (period.hasJustStarted(periodElapsedMillis)) {
        "${period.label} just started. App breakdown will appear after a little usage."
    } else {
        "App breakdown will appear once Datameter has enough usage for this period."
    }
}

private fun emptyTimelineMessage(
    period: UsagePeriod,
    periodElapsedMillis: Long,
): String {
    return if (period.hasJustStarted(periodElapsedMillis)) {
        "${period.label} just started. The timeline will fill in after usage begins."
    } else {
        "Timeline will appear once Datameter has enough usage for this period."
    }
}

private fun UsagePeriod.hasJustStarted(elapsedMillis: Long): Boolean {
    return elapsedMillis in 0L until EARLY_PERIOD_WINDOW_MILLIS
}

@Composable
private fun usageKindColor(kind: UsageRowKind): Color {
    return when (kind) {
        UsageRowKind.Hotspot -> MaterialTheme.colorScheme.tertiary
        UsageRowKind.App -> MaterialTheme.colorScheme.primary
        UsageRowKind.System -> MaterialTheme.colorScheme.secondary
        UsageRowKind.RemovedApps -> MaterialTheme.colorScheme.outline
        UsageRowKind.Measured -> MaterialTheme.colorScheme.primary
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

    val context = LocalContext.current.applicationContext
    val packageName = remember(row.id) { row.packageNameOrNull() } ?: return null
    val iconState = produceState<ImageBitmap?>(
        initialValue = appIconCache[packageName],
        packageName,
        context,
    ) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            context.loadAppIcon(packageName)
        }?.also { icon ->
            appIconCache[packageName] = icon
        }
    }

    return iconState.value
}

private fun usageVector(kind: UsageRowKind): ImageVector {
    return when (kind) {
        UsageRowKind.Hotspot -> Icons.Filled.WifiTethering
        UsageRowKind.System -> Icons.Filled.Settings
        UsageRowKind.RemovedApps -> Icons.Filled.Delete
        UsageRowKind.Measured -> Icons.Filled.Apps
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
private const val EARLY_PERIOD_WINDOW_MILLIS = 60L * 60L * 1000L
