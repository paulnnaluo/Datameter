package com.datameter.ui.audit

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.datameter.domain.ByteFormatter
import com.datameter.domain.model.AuditTone
import com.datameter.domain.model.PermissionStatus
import com.datameter.ui.home.PermissionRequiredContent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AuditScreen(
    state: AuditUiState,
    onNetworkNameChanged: (String) -> Unit,
    onStartingBalanceChanged: (String) -> Unit,
    onCurrentBalanceChanged: (String) -> Unit,
    onStartAudit: () -> Unit,
    onCheckAgain: () -> Unit,
    onEndAudit: () -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.isBusy) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Data Audit",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Measured by this phone. Compared with the balance you enter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.errorMessage?.let { message ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (state.activeSession == null) {
            item {
                StartAuditCard(
                    state = state,
                    onNetworkNameChanged = onNetworkNameChanged,
                    onStartingBalanceChanged = onStartingBalanceChanged,
                    onStartAudit = onStartAudit,
                )
            }
        } else {
            item {
                ActiveAuditCard(
                    state = state,
                    onCurrentBalanceChanged = onCurrentBalanceChanged,
                    onCheckAgain = onCheckAgain,
                    onEndAudit = onEndAudit,
                )
            }
        }
    }
}

@Composable
private fun StartAuditCard(
    state: AuditUiState,
    onNetworkNameChanged: (String) -> Unit,
    onStartingBalanceChanged: (String) -> Unit,
    onStartAudit: () -> Unit,
) {
    Surface(
        shape = datameterPanelShape(),
        color = datameterPanelColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Start balance check",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = state.networkNameInput,
                onValueChange = onNetworkNameChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Network") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
            OutlinedTextField(
                value = state.startingBalanceInput,
                onValueChange = onStartingBalanceChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Current balance") },
                suffix = { Text("GB") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.medium,
            )
            Button(
                onClick = onStartAudit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
                enabled = !state.isBusy,
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Start audit",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ActiveAuditCard(
    state: AuditUiState,
    onCurrentBalanceChanged: (String) -> Unit,
    onCheckAgain: () -> Unit,
    onEndAudit: () -> Unit,
) {
    val session = state.activeSession ?: return
    val containerColor = when (state.assessment.tone) {
        AuditTone.UnusualDifference -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.74f)

        AuditTone.LooksNormal -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)

        AuditTone.Waiting -> datameterPanelColor()
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${session.networkName} audit",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Started ${formatAuditTime(session.startedAtMillis)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AuditMetricRow("Starting balance", ByteFormatter.format(session.startingBalanceBytes))
            AuditMetricRow("Device measured", ByteFormatter.format(state.measuredBytes))
            state.deductedBytes?.let { AuditMetricRow("Network deducted", ByteFormatter.format(it)) }
            state.differenceBytes?.let { AuditMetricRow("Difference", ByteFormatter.formatSigned(it)) }

            if (state.deductedBytes != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = state.assessment.label,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            OutlinedTextField(
                value = state.currentBalanceInput,
                onValueChange = onCurrentBalanceChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("New balance") },
                suffix = { Text("GB") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.medium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onCheckAgain,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp),
                    enabled = !state.isBusy,
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Check again",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                OutlinedButton(
                    onClick = onEndAudit,
                    enabled = !state.isBusy,
                    modifier = Modifier.heightIn(min = 44.dp),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "End",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
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

private fun datameterPanelShape(): RoundedCornerShape {
    return RoundedCornerShape(24.dp)
}

private fun formatAuditTime(millis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())
    return formatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}
