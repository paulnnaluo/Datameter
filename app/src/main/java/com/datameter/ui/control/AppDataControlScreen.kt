package com.datameter.ui.control

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.datameter.data.control.DataControlBlockEvent
import com.datameter.data.control.DataControlBlockReason
import com.datameter.data.control.DataControlRunStatus
import com.datameter.data.control.DataControlRule
import com.datameter.domain.ByteFormatter
import com.datameter.domain.DataUnits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToLong

private val appIconCache = ConcurrentHashMap<String, ImageBitmap>()

@Composable
fun AppDataControlScreen(
    state: AppDataControlUiState,
    dataControlState: DataControlUiState,
    onEnableDataControl: () -> Unit,
    onDisableDataControl: () -> Unit,
    onBlockMobileChanged: (Boolean) -> Unit,
    onBlockWifiChanged: (Boolean) -> Unit,
    onDailyLimitEnabledChanged: (Boolean) -> Unit,
    onDailyLimitBytesChanged: (Long) -> Unit,
    onClearAutoBlock: () -> Unit,
) {
    val todayUsage = state.todayUsage
    val rule = state.rule

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        item {
            AppHero(
                app = state.app,
                todayBytes = todayUsage?.mobileBytes ?: 0L,
            )
        }

        item {
            DataControlStatusPanel(
                state = dataControlState,
                onEnableDataControl = onEnableDataControl,
                onDisableDataControl = onDisableDataControl,
            )
        }

        if (rule.isAutoBlockedToday(currentLocalDate())) {
            item {
                AutoBlockedPanel(
                    rule = rule,
                    onClearAutoBlock = onClearAutoBlock,
                )
            }
        }

        item {
            AppRulesSection(
                rule = rule,
                onBlockMobileChanged = onBlockMobileChanged,
                onBlockWifiChanged = onBlockWifiChanged,
                onDailyLimitEnabledChanged = onDailyLimitEnabledChanged,
                onDailyLimitBytesChanged = onDailyLimitBytesChanged,
            )
        }

        item {
            UsageProofSection(
                selectedPeriod = state.app.selectedPeriod.label,
                selectedPeriodBytes = state.app.selectedPeriodBytes,
                todayUsage = todayUsage,
            )
        }

        item {
            RecentBlocksSection(events = state.recentEvents)
        }
    }
}

@Composable
private fun AppHero(
    app: SelectedUsageApp,
    todayBytes: Long,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIcon(packageName = app.packageName, modifier = Modifier.size(58.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Data Control",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = ByteFormatter.format(todayBytes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "today",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DataControlStatusPanel(
    state: DataControlUiState,
    onEnableDataControl: () -> Unit,
    onDisableDataControl: () -> Unit,
) {
    val active = state.runtimeState.status == DataControlRunStatus.Active
    val enabled = state.settings.enabled
    val title = when (state.runtimeState.status) {
        DataControlRunStatus.Active -> "Data Control active"
        DataControlRunStatus.Standby -> "Data Control ready"
        DataControlRunStatus.Starting -> "Data Control starting"
        DataControlRunStatus.NeedsVpnPermission -> "VPN permission needed"
        DataControlRunStatus.Error -> "Data Control needs attention"
        DataControlRunStatus.Off -> "Data Control off"
    }
    val text = when (state.runtimeState.status) {
        DataControlRunStatus.Active -> {
            if (state.blockedAppsToday > 0) {
                "${state.blockedAppsToday} apps blocked today"
            } else {
                "Rules are ready for this app."
            }
        }

        DataControlRunStatus.NeedsVpnPermission -> "Android needs one more confirmation."
        DataControlRunStatus.Error -> state.runtimeState.message ?: "Could not keep the VPN running."
        DataControlRunStatus.Standby -> "Saved rules will start the local VPN only for controlled apps."
        DataControlRunStatus.Starting -> "Preparing local app controls."
        DataControlRunStatus.Off -> "Turn it on to apply app data rules."
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.74f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f)
        },
        contentColor = if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = text,
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
private fun AutoBlockedPanel(
    rule: DataControlRule,
    onClearAutoBlock: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mobile data blocked today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when (rule.autoBlockedReason) {
                        DataControlBlockReason.AppLimit -> "This app reached its daily limit."
                        DataControlBlockReason.GlobalLimit -> "This app reached your all-app limit."
                        else -> "Datameter is holding this app offline."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onClearAutoBlock,
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Allow", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AppRulesSection(
    rule: DataControlRule,
    onBlockMobileChanged: (Boolean) -> Unit,
    onBlockWifiChanged: (Boolean) -> Unit,
    onDailyLimitEnabledChanged: (Boolean) -> Unit,
    onDailyLimitBytesChanged: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Rules")
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SwitchRow(
                title = "Block mobile data",
                subtitle = "Stops this app on cellular data.",
                checked = rule.blockMobileData,
                onCheckedChange = onBlockMobileChanged,
                shape = groupedItemShape(0, 3),
            )
            SwitchRow(
                title = "Block Wi-Fi",
                subtitle = "Stops this app while connected to Wi-Fi.",
                checked = rule.blockWifi,
                onCheckedChange = onBlockWifiChanged,
                shape = groupedItemShape(1, 3),
            )
            LimitRow(
                rule = rule,
                shape = groupedItemShape(2, 3),
                onDailyLimitEnabledChanged = onDailyLimitEnabledChanged,
                onDailyLimitBytesChanged = onDailyLimitBytesChanged,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: RoundedCornerShape,
) {
    Surface(
        shape = shape,
        color = listItemColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun LimitRow(
    rule: DataControlRule,
    shape: RoundedCornerShape,
    onDailyLimitEnabledChanged: (Boolean) -> Unit,
    onDailyLimitBytesChanged: (Long) -> Unit,
) {
    Surface(
        shape = shape,
        color = listItemColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Daily app limit",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = ByteFormatter.format(rule.dailyLimitBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = rule.dailyLimitEnabled,
                    onCheckedChange = onDailyLimitEnabledChanged,
                )
            }
            Slider(
                value = rule.dailyLimitBytes.toFloat() / DataUnits.GIGABYTE.toFloat(),
                onValueChange = {
                    onDailyLimitBytesChanged((it * DataUnits.GIGABYTE).roundToLong())
                },
                valueRange = 0.5f..20f,
                steps = 38,
                enabled = rule.dailyLimitEnabled,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun UsageProofSection(
    selectedPeriod: String,
    selectedPeriodBytes: Long,
    todayUsage: com.datameter.data.control.DataControlDailyUsage?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Usage")
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProofRow("$selectedPeriod measured", ByteFormatter.format(selectedPeriodBytes))
                ProofRow("Data Control mobile", ByteFormatter.format(todayUsage?.mobileBytes ?: 0L))
                ProofRow("Data Control Wi-Fi", ByteFormatter.format(todayUsage?.wifiBytes ?: 0L))
            }
        }
    }
}

@Composable
private fun ProofRow(label: String, value: String) {
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
private fun RecentBlocksSection(events: List<DataControlBlockEvent>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Recent blocks")
        if (events.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
            ) {
                Text(
                    text = "No blocks for this app yet.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                events.forEachIndexed { index, event ->
                    Surface(
                        shape = groupedItemShape(index, events.size),
                        color = listItemColor(),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
                            ProofRow(
                                label = "${event.reason.label()} - ${formatClock(event.createdAtMillis)}",
                                value = ByteFormatter.format(event.usedBytes),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
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

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (iconState.value != null) {
            Image(
                bitmap = iconState.value!!,
                contentDescription = null,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Apps,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun DataControlBlockReason.label(): String {
    return when (this) {
        DataControlBlockReason.ManualMobile -> "Mobile blocked"
        DataControlBlockReason.ManualWifi -> "Wi-Fi blocked"
        DataControlBlockReason.GlobalLimit -> "All-app limit"
        DataControlBlockReason.AppLimit -> "App limit"
    }
}

@Composable
private fun listItemColor(): Color {
    return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)
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

private fun currentLocalDate(): String {
    return java.time.LocalDate.now(ZoneId.systemDefault()).toString()
}

private fun formatClock(millis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    return formatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
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

private const val ICON_BITMAP_SIZE_PX = 96
