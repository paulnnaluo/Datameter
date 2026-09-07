package com.datameter.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datameter.domain.model.AuditHomeStatus
import com.datameter.domain.model.AuditMath
import com.datameter.domain.model.DataFreshness
import com.datameter.domain.model.HomeViewState
import com.datameter.domain.model.NetworkFilter
import com.datameter.domain.model.PeriodSummary
import com.datameter.domain.model.PermissionStatus
import com.datameter.domain.model.TimelineBucket
import com.datameter.domain.model.UsagePeriod
import com.datameter.domain.model.UsageRow
import com.datameter.domain.model.UsageRowKind
import com.datameter.ui.alerts.AlertsScreen
import com.datameter.ui.alerts.AlertsViewModel
import com.datameter.ui.audit.AuditScreen
import com.datameter.ui.audit.AuditViewModel
import com.datameter.ui.home.AppsScreen
import com.datameter.ui.home.HomeScreen
import com.datameter.ui.home.HomeViewModel
import com.datameter.ui.home.TimelineScreen
import com.datameter.ui.theme.DatameterTheme

private enum class DatameterDestination(
    val label: String,
    val icon: ImageVector,
) {
    Home("Home", Icons.Filled.Home),
    Timeline("Timeline", Icons.Filled.Timeline),
    Apps("Apps", Icons.Filled.Apps),
    Audit("Audit", Icons.Filled.CheckCircle),
    Alerts("Alerts", Icons.Filled.Notifications),
}

@Composable
fun DatameterRoot() {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(application))
    val auditViewModel: AuditViewModel = viewModel(factory = AuditViewModel.factory(application))
    val alertsViewModel: AlertsViewModel = viewModel(factory = AlertsViewModel.factory(application))

    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val auditState by auditViewModel.uiState.collectAsStateWithLifecycle()
    val alertSettings by alertsViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(auditState.activeSession, auditState.deductedBytes) {
        homeViewModel.refresh()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeViewModel.refresh()
                auditViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DatameterTheme {
        var destination by rememberSaveable { mutableStateOf(DatameterDestination.Home) }
        val openUsageAccess = {
            val targetedIntent = Intent(
                Settings.ACTION_USAGE_ACCESS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            )
            context.startSettingsIntent(
                intent = targetedIntent,
                fallbackIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            )
        }
        val openAppSettings = {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            )
            context.startSettingsIntent(intent)
        }

        Scaffold(
            topBar = {
                DatameterTopBar(
                    selectedFilter = homeState.selectedNetworkFilter,
                    onFilterSelected = homeViewModel::selectNetworkFilter,
                )
            },
            bottomBar = {
                DatameterBottomBar(
                    selectedDestination = destination,
                    onDestinationSelected = { destination = it },
                )
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (destination) {
                    DatameterDestination.Home -> HomeScreen(
                        state = homeState,
                        onPeriodSelected = homeViewModel::selectPeriod,
                        onOpenUsageAccess = openUsageAccess,
                        onOpenAppSettings = openAppSettings,
                        onOpenTimeline = { destination = DatameterDestination.Timeline },
                        onOpenAudit = { destination = DatameterDestination.Audit },
                        onRefresh = homeViewModel::refresh,
                    )

                    DatameterDestination.Timeline -> TimelineScreen(
                        state = homeState,
                        onOpenUsageAccess = openUsageAccess,
                        onOpenAppSettings = openAppSettings,
                    )

                    DatameterDestination.Apps -> AppsScreen(
                        state = homeState,
                        onOpenUsageAccess = openUsageAccess,
                        onOpenAppSettings = openAppSettings,
                    )

                    DatameterDestination.Audit -> AuditScreen(
                        state = auditState,
                        onNetworkNameChanged = auditViewModel::setNetworkName,
                        onStartingBalanceChanged = auditViewModel::setStartingBalance,
                        onCurrentBalanceChanged = auditViewModel::setCurrentBalance,
                        onStartAudit = auditViewModel::startAudit,
                        onCheckAgain = auditViewModel::recordCheck,
                        onEndAudit = auditViewModel::endAudit,
                        onOpenUsageAccess = openUsageAccess,
                        onOpenAppSettings = openAppSettings,
                    )

                    DatameterDestination.Alerts -> AlertsScreen(
                        settings = alertSettings,
                        onDailyEnabledChanged = alertsViewModel::setDailyLimitEnabled,
                        onDailyLimitChanged = alertsViewModel::setDailyLimitBytes,
                        onHourlyEnabledChanged = alertsViewModel::setHourlySpikeEnabled,
                        onHourlyLimitChanged = alertsViewModel::setHourlySpikeBytes,
                    )
                }
            }
        }
    }
}

private fun Context.startSettingsIntent(intent: Intent, fallbackIntent: Intent? = null) {
    val resolvedIntent = if (intent.resolveActivity(packageManager) != null) {
        intent
    } else {
        fallbackIntent ?: intent
    }
    resolvedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(resolvedIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatameterTopBar(
    selectedFilter: NetworkFilter,
    onFilterSelected: (NetworkFilter) -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = "Datameter",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.primary,
        ),
        actions = {
            NetworkFilterMenu(
                selectedFilter = selectedFilter,
                onFilterSelected = onFilterSelected,
            )
        },
    )
}

@Composable
private fun NetworkFilterMenu(
    selectedFilter: NetworkFilter,
    onFilterSelected: (NetworkFilter) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.padding(end = 8.dp)) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.heightIn(min = 40.dp),
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(
                text = selectedFilter.label,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            NetworkFilter.entries.forEach { filter ->
                DropdownMenuItem(
                    text = { Text(filter.label) },
                    onClick = {
                        expanded = false
                        onFilterSelected(filter)
                    },
                )
            }
        }
    }
}

@Composable
private fun DatameterBottomBar(
    selectedDestination: DatameterDestination,
    onDestinationSelected: (DatameterDestination) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DatameterDestination.entries.forEach { item ->
                DatameterBottomNavItem(
                    item = item,
                    selected = selectedDestination == item,
                    onClick = { onDestinationSelected(item) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DatameterBottomNavItem(
    item: DatameterDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconTint = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val labelColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .height(62.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(top = 5.dp, bottom = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                modifier = Modifier.size(22.dp),
                tint = iconTint,
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 1,
        )
    }
}

@Preview(
    name = "Home - Mobile Data",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun DatameterHomePreview() {
    val state = previewHomeState()

    DatameterTheme(darkTheme = false) {
        Scaffold(
            topBar = {
                DatameterTopBar(
                    selectedFilter = state.selectedNetworkFilter,
                    onFilterSelected = {},
                )
            },
            bottomBar = {
                DatameterBottomBar(
                    selectedDestination = DatameterDestination.Home,
                    onDestinationSelected = {},
                )
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                HomeScreen(
                    state = state,
                    onPeriodSelected = {},
                    onOpenUsageAccess = {},
                    onOpenAppSettings = {},
                    onOpenTimeline = {},
                    onOpenAudit = {},
                    onRefresh = {},
                )
            }
        }
    }
}

private fun previewHomeState(): HomeViewState {
    val now = System.currentTimeMillis()
    val hour = 60L * 60L * 1000L
    val total = 4_800_000_000L

    return HomeViewState(
        selectedNetworkFilter = NetworkFilter.Mobile,
        selectedPeriod = UsagePeriod.Today,
        totalBytes = total,
        periodSummaries = listOf(
            PeriodSummary(UsagePeriod.Today, total),
            PeriodSummary(UsagePeriod.Week, 18_300_000_000L),
            PeriodSummary(UsagePeriod.Month, 61_700_000_000L),
        ),
        usageRows = listOf(
            UsageRow("hotspot", "Hotspot / Tethering", UsageRowKind.Hotspot, 1_900_000_000L, 700_000_000L),
            UsageRow("app:com.google.android.youtube", "YouTube", UsageRowKind.App, 690_000_000L, 130_000_000L),
            UsageRow("app:com.instagram.android", "Instagram", UsageRowKind.App, 420_000_000L, 90_000_000L),
            UsageRow("app:com.android.chrome", "Chrome", UsageRowKind.App, 260_000_000L, 50_000_000L),
            UsageRow("app:com.whatsapp", "WhatsApp", UsageRowKind.App, 150_000_000L, 40_000_000L),
            UsageRow("app:com.google.android.apps.photos", "Google Photos", UsageRowKind.App, 110_000_000L, 30_000_000L),
            UsageRow("app:com.google.android.gm", "Gmail", UsageRowKind.App, 70_000_000L, 50_000_000L),
            UsageRow("app:com.spotify.music", "Spotify", UsageRowKind.App, 90_000_000L, 20_000_000L),
        ),
        timelineBuckets = List(12) { index ->
            val usage = when (index) {
                7 -> 3_100_000_000L
                8 -> 620_000_000L
                4 -> 410_000_000L
                else -> 80_000_000L + (index * 12_000_000L)
            }
            TimelineBucket(
                startMillis = now - ((12 - index) * hour),
                endMillis = now - ((11 - index) * hour),
                rxBytes = (usage * 0.78).toLong(),
                txBytes = (usage * 0.22).toLong(),
            )
        },
        primaryInsight = "Your hotspot used 54% of your mobile data today.",
        auditStatus = AuditHomeStatus.Active(
            networkName = "MTN",
            startedAtMillis = now - (3 * hour),
            measuredBytes = 3_420_000_000L,
            deductedBytes = 3_510_000_000L,
            differenceBytes = 90_000_000L,
            assessment = AuditMath.assess(3_420_000_000L, 3_510_000_000L),
        ),
        permissionStatus = PermissionStatus.Granted,
        dataFreshness = DataFreshness.Fresh(now),
    )
}
