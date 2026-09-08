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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.datameter.domain.ByteFormatter
import com.datameter.domain.model.AuditTone
import com.datameter.domain.model.PermissionStatus
import com.datameter.ui.home.PermissionRequiredContent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun AuditScreen(
    state: AuditUiState,
    onNetworkNameChanged: (String) -> Unit,
    onStartingBalanceChanged: (String) -> Unit,
    onCurrentBalanceChanged: (String) -> Unit,
    onStartAudit: () -> Unit,
    onEndAudit: () -> Unit,
    onNewAudit: () -> Unit,
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
        contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        if (state.isBusy) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Data audit",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
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

        item {
            when {
                state.activeSession != null -> RunningAuditCard(
                    state = state,
                    onCurrentBalanceChanged = onCurrentBalanceChanged,
                    onEndAudit = onEndAudit,
                )

                state.completedAudit != null -> FinishedAuditCard(
                    result = state.completedAudit,
                    onNewAudit = onNewAudit,
                )

                else -> StartAuditCard(
                    state = state,
                    onNetworkNameChanged = onNetworkNameChanged,
                    onStartingBalanceChanged = onStartingBalanceChanged,
                    onStartAudit = onStartAudit,
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
                label = { Text("Current data balance") },
                suffix = { Text("GB") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.medium,
            )
            Button(
                onClick = onStartAudit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
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
private fun RunningAuditCard(
    state: AuditUiState,
    onCurrentBalanceChanged: (String) -> Unit,
    onEndAudit: () -> Unit,
) {
    val session = state.activeSession ?: return

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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Audit running",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = runningAuditStatusText(
                        startedAtMillis = session.startedAtMillis,
                        lastCheckedAtMillis = session.lastCheckedAtMillis,
                        lastBalanceBytes = session.lastBalanceBytes,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = state.currentBalanceInput,
                onValueChange = onCurrentBalanceChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Current data balance") },
                suffix = { Text("GB") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.medium,
            )

            Button(
                onClick = onEndAudit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                enabled = !state.isBusy,
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Finish audit",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FinishedAuditCard(
    result: CompletedAuditResult,
    onNewAudit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AuditValueBlock(
                    label = "You used",
                    value = ByteFormatter.format(result.measuredBytes),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.64f))
                AuditValueBlock(
                    label = "${result.networkName} deducted",
                    value = ByteFormatter.format(result.deductedBytes),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    AuditEndpointBlock(
                        time = "Started ${formatAuditTime(result.startedAtMillis)}",
                        balance = ByteFormatter.format(result.startingBalanceBytes),
                        modifier = Modifier.weight(1f),
                    )
                    AuditEndpointBlock(
                        time = "Ended ${formatAuditTime(result.endedAtMillis)}",
                        balance = ByteFormatter.format(result.endingBalanceBytes),
                        modifier = Modifier.weight(1f),
                        alignEnd = true,
                    )
                }

                Text(
                    text = auditResultLine(result),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = auditResultLineColor(result),
                    textAlign = TextAlign.Center,
                )
            }
        }

        OutlinedButton(
            onClick = onNewAudit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(
                text = "New audit",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun AuditValueBlock(
    label: String,
    value: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AuditEndpointBlock(
    time: String,
    balance: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = balance,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun auditResultLineColor(result: CompletedAuditResult): Color {
    if (auditNeedsMoreUsageData(result)) {
        return MaterialTheme.colorScheme.onSurfaceVariant
    }

    return when (result.assessment.tone) {
        AuditTone.LooksNormal -> MaterialTheme.colorScheme.primary
        AuditTone.UnusualDifference -> MaterialTheme.colorScheme.tertiary
        AuditTone.Waiting -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun datameterPanelColor(): Color {
    return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
}

private fun datameterPanelShape(): RoundedCornerShape {
    return RoundedCornerShape(24.dp)
}

private fun runningAuditStatusText(
    startedAtMillis: Long,
    lastCheckedAtMillis: Long?,
    lastBalanceBytes: Long?,
): String {
    return if (lastCheckedAtMillis == null) {
        "Started ${formatAuditTime(startedAtMillis)}"
    } else {
        "Last balance ${ByteFormatter.format(lastBalanceBytes ?: 0L)} at ${formatAuditTime(lastCheckedAtMillis)}"
    }
}

private fun auditResultLine(result: CompletedAuditResult): String {
    if (auditNeedsMoreUsageData(result)) {
        return "This audit just started. Use some data before judging the deduction."
    }

    val difference = result.differenceBytes
    return when {
        result.assessment.tone == AuditTone.LooksNormal && difference == 0L -> "No difference"
        result.assessment.tone == AuditTone.LooksNormal -> {
            "Close match: ${ByteFormatter.format(abs(difference))}"
        }
        difference > 0L -> "${result.networkName} deducted ${ByteFormatter.format(difference)} more"
        difference < 0L -> "Datameter saw ${ByteFormatter.format(abs(difference))} more"
        else -> "No difference"
    }
}

private fun auditNeedsMoreUsageData(result: CompletedAuditResult): Boolean {
    val durationMillis = (result.endedAtMillis - result.startedAtMillis).coerceAtLeast(0L)
    return result.measuredBytes == 0L && durationMillis < EARLY_AUDIT_WINDOW_MILLIS
}

private fun formatAuditTime(millis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())
    return formatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}

private const val EARLY_AUDIT_WINDOW_MILLIS = 15L * 60L * 1000L
