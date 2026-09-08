package com.datameter.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.datameter.data.alerts.AlertSettings
import com.datameter.domain.ByteFormatter
import com.datameter.domain.DataUnits
import com.datameter.ui.control.DataControlUiState
import kotlin.math.roundToLong

@Composable
fun AlertsScreen(
    settings: AlertSettings,
    dataControlState: DataControlUiState,
    notificationsAllowed: Boolean,
    onRequestNotifications: () -> Unit,
    onEnableDataControl: () -> Unit,
    onDailyEnabledChanged: (Boolean) -> Unit,
    onDailyLimitChanged: (Long) -> Unit,
    onHourlyEnabledChanged: (Boolean) -> Unit,
    onHourlyLimitChanged: (Long) -> Unit,
    onGlobalAutoBlockEnabledChanged: (Boolean) -> Unit,
    onGlobalAutoBlockBytesChanged: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Alerts",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Thresholds saved on this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (settings.hasEnabledAlerts && !notificationsAllowed) {
            item {
                NotificationPermissionCard(onRequestNotifications = onRequestNotifications)
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AlertSettingCard(
                    title = "Daily mobile data",
                    value = ByteFormatter.format(settings.dailyLimitBytes),
                    enabled = settings.dailyLimitEnabled,
                    onEnabledChanged = onDailyEnabledChanged,
                    shape = groupedItemShape(0, 2),
                ) { sliderModifier ->
                    Slider(
                        modifier = sliderModifier,
                        value = settings.dailyLimitBytes.toFloat() / DataUnits.GIGABYTE.toFloat(),
                        onValueChange = {
                            onDailyLimitChanged((it * DataUnits.GIGABYTE).roundToLong())
                        },
                        valueRange = 0.5f..20f,
                        steps = 38,
                        enabled = settings.dailyLimitEnabled,
                        colors = datameterSliderColors(),
                    )
                }

                AlertSettingCard(
                    title = "High hourly usage",
                    value = ByteFormatter.format(settings.hourlySpikeBytes),
                    enabled = settings.hourlySpikeEnabled,
                    onEnabledChanged = onHourlyEnabledChanged,
                    shape = groupedItemShape(1, 2),
                ) { sliderModifier ->
                    Slider(
                        modifier = sliderModifier,
                        value = settings.hourlySpikeBytes.toFloat() / DataUnits.MEGABYTE.toFloat(),
                        onValueChange = {
                            onHourlyLimitChanged((it * DataUnits.MEGABYTE).roundToLong())
                        },
                        valueRange = 100f..2_000f,
                        steps = 18,
                        enabled = settings.hourlySpikeEnabled,
                        colors = datameterSliderColors(),
                    )
                }
            }
        }

        item {
            DataControlAutoBlockSection(
                dataControlState = dataControlState,
                onEnableDataControl = onEnableDataControl,
                onGlobalAutoBlockEnabledChanged = onGlobalAutoBlockEnabledChanged,
                onGlobalAutoBlockBytesChanged = onGlobalAutoBlockBytesChanged,
            )
        }
    }
}

@Composable
private fun DataControlAutoBlockSection(
    dataControlState: DataControlUiState,
    onEnableDataControl: () -> Unit,
    onGlobalAutoBlockEnabledChanged: (Boolean) -> Unit,
    onGlobalAutoBlockBytesChanged: (Long) -> Unit,
) {
    val settings = dataControlState.settings
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "App auto-block",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Stops any app on mobile data after it crosses your limit.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!settings.enabled) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Data Control is off",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Turn on Data Control before enabling app auto-block.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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

        AlertSettingCard(
            title = "Auto-block apps",
            value = ByteFormatter.format(settings.globalAutoBlockBytes),
            enabled = settings.globalAutoBlockEnabled,
            onEnabledChanged = onGlobalAutoBlockEnabledChanged,
            shape = groupedItemShape(0, 1),
        ) { sliderModifier ->
            Slider(
                modifier = sliderModifier,
                value = settings.globalAutoBlockBytes.toFloat() / DataUnits.GIGABYTE.toFloat(),
                onValueChange = {
                    onGlobalAutoBlockBytesChanged((it * DataUnits.GIGABYTE).roundToLong())
                },
                valueRange = 0.5f..20f,
                steps = 38,
                enabled = settings.globalAutoBlockEnabled,
                colors = datameterSliderColors(),
            )
        }
    }
}

@Composable
private fun NotificationPermissionCard(
    onRequestNotifications: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Notifications are off",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Android must allow Datameter notifications before usage alerts can appear.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onRequestNotifications,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(Icons.Filled.Notifications, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Enable notifications",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun AlertSettingCard(
    title: String,
    value: String,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    shape: RoundedCornerShape,
    slider: @Composable (Modifier) -> Unit,
) {
    Surface(
        shape = shape,
        color = datameterListItemColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChanged,
                )
            }
            slider(Modifier.fillMaxWidth().heightIn(min = 40.dp))
        }
    }
}

@Composable
private fun datameterSliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledThumbColor = MaterialTheme.colorScheme.outline,
    disabledActiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
    disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f),
)

@Composable
private fun datameterListItemColor(): Color {
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
