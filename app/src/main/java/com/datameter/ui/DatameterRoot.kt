package com.datameter.ui

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.datameter.R
import com.datameter.data.alerts.DatameterNotificationChannels
import com.datameter.data.alerts.DatameterNotificationDispatcher
import com.datameter.data.alerts.UsageAlertScheduler
import com.datameter.data.control.DataControlVpnController
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
import com.datameter.ui.control.AppDataControlScreen
import com.datameter.ui.control.AppDataControlViewModel
import com.datameter.ui.control.DataControlViewModel
import com.datameter.ui.control.SelectedUsageApp
import com.datameter.ui.home.HomeScreen
import com.datameter.ui.home.HomeViewModel
import com.datameter.ui.home.TimelineScreen
import com.datameter.ui.theme.DatameterTheme

private enum class DatameterDestination(
    val label: String,
    val icon: ImageVector,
) {
    Home("Home", Icons.Filled.Home),
    Audit("Audit", Icons.Filled.CheckCircle),
    Timeline("Timeline", Icons.Filled.Timeline),
    Alerts("Alerts", Icons.Filled.Notifications),
}

@Composable
fun DatameterRoot() {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(application))

    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    var notificationsAllowed by rememberSaveable {
        mutableStateOf(DatameterNotificationDispatcher.canPostNotifications(context))
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        notificationsAllowed = DatameterNotificationDispatcher.canPostNotifications(context)
        if (notificationsAllowed) UsageAlertScheduler.sync(context)
    }
    val requestNotifications = {
        DatameterNotificationChannels.ensureCreated(context)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!DatameterNotificationDispatcher.canPostNotifications(context)) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            context.startSettingsIntent(intent)
        } else {
            notificationsAllowed = DatameterNotificationDispatcher.canPostNotifications(context)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeViewModel.refresh()
                notificationsAllowed = DatameterNotificationDispatcher.canPostNotifications(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DatameterTheme {
        if (homeState.shouldShowLaunchScreen()) {
            DatameterLaunchScreen()
        } else {
            val auditViewModel: AuditViewModel = viewModel(factory = AuditViewModel.factory(application))
            val alertsViewModel: AlertsViewModel = viewModel(factory = AlertsViewModel.factory(application))
            val dataControlViewModel: DataControlViewModel = viewModel(
                factory = DataControlViewModel.factory(application),
            )
            val auditState by auditViewModel.uiState.collectAsStateWithLifecycle()
            val alertSettings by alertsViewModel.uiState.collectAsStateWithLifecycle()
            val dataControlState by dataControlViewModel.uiState.collectAsStateWithLifecycle()
            var destination by rememberSaveable { mutableStateOf(DatameterDestination.Home) }
            var selectedApp by remember { mutableStateOf<SelectedUsageApp?>(null) }
            var showDataControlDisclosure by rememberSaveable { mutableStateOf(false) }
            var showDisableDataControlWarning by rememberSaveable { mutableStateOf(false) }
            var pendingDataControlAction by remember { mutableStateOf<(() -> Unit)?>(null) }

            val vpnPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val pendingAction = pendingDataControlAction
                    pendingDataControlAction = null
                    pendingAction?.invoke()
                    dataControlViewModel.enableAfterVpnConsent()
                } else {
                    pendingDataControlAction = null
                    dataControlViewModel.refresh()
                }
            }

            fun startDataControlWithConsent() {
                val intent = DataControlVpnController.prepareIntent(context)
                if (intent != null) {
                    vpnPermissionLauncher.launch(intent)
                } else {
                    val pendingAction = pendingDataControlAction
                    pendingDataControlAction = null
                    pendingAction?.invoke()
                    dataControlViewModel.enableAfterVpnConsent()
                }
            }

            fun requestDataControlStart() {
                if (!dataControlState.settings.disclosureAccepted) {
                    showDataControlDisclosure = true
                } else {
                    startDataControlWithConsent()
                }
            }

            fun runWithDataControlGate(action: () -> Unit) {
                if (dataControlState.settings.enabled) {
                    action()
                } else {
                    pendingDataControlAction = action
                    requestDataControlStart()
                }
            }

            fun requestDataControlDisable() {
                if (
                    dataControlState.activeRuleCount > 0 ||
                    dataControlState.settings.globalAutoBlockEnabled
                ) {
                    showDisableDataControlWarning = true
                } else {
                    dataControlViewModel.disableDataControl()
                }
            }

            BackHandler(enabled = selectedApp != null) {
                selectedApp = null
            }

            DisposableEffect(lifecycleOwner, auditViewModel, dataControlViewModel) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        auditViewModel.refresh()
                        dataControlViewModel.refresh()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

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
                    val app = selectedApp
                    if (destination == DatameterDestination.Home && app != null) {
                        DatameterAppTopBar(
                            title = app.label,
                            onBack = { selectedApp = null },
                        )
                    } else {
                        DatameterTopBar(
                            selectedFilter = homeState.selectedNetworkFilter,
                            onFilterSelected = homeViewModel::selectNetworkFilter,
                        )
                    }
                },
                bottomBar = {
                    DatameterBottomBar(
                        selectedDestination = destination,
                        onDestinationSelected = {
                            selectedApp = null
                            destination = it
                        },
                    )
                },
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    val app = selectedApp
                    if (destination == DatameterDestination.Home && app != null) {
                        val appViewModel: AppDataControlViewModel = viewModel(
                            key = "app-control-${app.uid}-${app.packageName}",
                            factory = AppDataControlViewModel.factory(application, app),
                        )
                        val appState by appViewModel.uiState.collectAsStateWithLifecycle()

                        AppDataControlScreen(
                            state = appState,
                            dataControlState = dataControlState,
                            onEnableDataControl = { requestDataControlStart() },
                            onDisableDataControl = { requestDataControlDisable() },
                            onBlockMobileChanged = {
                                val updateRule = {
                                    appViewModel.setBlockMobileData(it)
                                    dataControlViewModel.refresh()
                                }
                                if (it) runWithDataControlGate(updateRule) else updateRule()
                            },
                            onBlockWifiChanged = {
                                val updateRule = {
                                    appViewModel.setBlockWifi(it)
                                    dataControlViewModel.refresh()
                                }
                                if (it) runWithDataControlGate(updateRule) else updateRule()
                            },
                            onDailyLimitEnabledChanged = {
                                val updateRule = {
                                    appViewModel.setDailyLimitEnabled(it)
                                    dataControlViewModel.refresh()
                                }
                                if (it) runWithDataControlGate(updateRule) else updateRule()
                            },
                            onDailyLimitBytesChanged = {
                                appViewModel.setDailyLimitBytes(it)
                                dataControlViewModel.refresh()
                            },
                            onClearAutoBlock = {
                                appViewModel.clearAutoBlock()
                                dataControlViewModel.refresh()
                            },
                        )
                    } else when (destination) {
                        DatameterDestination.Home -> HomeScreen(
                            state = homeState,
                            dataControlState = dataControlState,
                            onPeriodSelected = homeViewModel::selectPeriod,
                            onOpenUsageAccess = openUsageAccess,
                            onOpenAppSettings = openAppSettings,
                            onOpenTimeline = { destination = DatameterDestination.Timeline },
                            onRefresh = homeViewModel::refresh,
                            onEnableDataControl = { requestDataControlStart() },
                            onDisableDataControl = { requestDataControlDisable() },
                            onUsageRowSelected = { row ->
                                val uid = row.uid ?: return@HomeScreen
                                val packageName = row.packageNameOrNull() ?: return@HomeScreen
                                selectedApp = SelectedUsageApp(
                                    uid = uid,
                                    packageName = packageName,
                                    label = row.label,
                                    selectedPeriod = homeState.selectedPeriod,
                                    selectedPeriodBytes = row.totalBytes,
                                )
                            },
                        )

                        DatameterDestination.Timeline -> TimelineScreen(
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
                            onEndAudit = auditViewModel::endAudit,
                            onNewAudit = auditViewModel::clearCompletedAudit,
                            onOpenUsageAccess = openUsageAccess,
                            onOpenAppSettings = openAppSettings,
                        )

                        DatameterDestination.Alerts -> AlertsScreen(
                            settings = alertSettings,
                            dataControlState = dataControlState,
                            notificationsAllowed = notificationsAllowed,
                            onRequestNotifications = requestNotifications,
                            onEnableDataControl = { requestDataControlStart() },
                            onDailyEnabledChanged = { enabled ->
                                alertsViewModel.setDailyLimitEnabled(enabled)
                                if (enabled) requestNotifications()
                            },
                            onDailyLimitChanged = alertsViewModel::setDailyLimitBytes,
                            onHourlyEnabledChanged = { enabled ->
                                alertsViewModel.setHourlySpikeEnabled(enabled)
                                if (enabled) requestNotifications()
                            },
                            onHourlyLimitChanged = alertsViewModel::setHourlySpikeBytes,
                            onGlobalAutoBlockEnabledChanged = { enabled ->
                                val updateRule = {
                                    dataControlViewModel.setGlobalAutoBlockEnabled(enabled)
                                    if (enabled) requestNotifications()
                                }
                                if (enabled) runWithDataControlGate(updateRule) else updateRule()
                            },
                            onGlobalAutoBlockBytesChanged = dataControlViewModel::setGlobalAutoBlockBytes,
                        )
                    }
                }
            }

            if (showDataControlDisclosure) {
                DataControlDisclosureDialog(
                    onDismiss = {
                        pendingDataControlAction = null
                        showDataControlDisclosure = false
                    },
                    onAccept = {
                        showDataControlDisclosure = false
                        dataControlViewModel.acceptDisclosure()
                        startDataControlWithConsent()
                    },
                )
            }

            if (showDisableDataControlWarning) {
                DisableDataControlWarningDialog(
                    onDismiss = { showDisableDataControlWarning = false },
                    onConfirm = {
                        showDisableDataControlWarning = false
                        dataControlViewModel.disableDataControl()
                    },
                )
            }
        }
    }
}

@Composable
private fun DatameterLaunchScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.datameter_app_launcher),
                    contentDescription = "Datameter",
                    modifier = Modifier
                        .width(150.dp)
                        .height(141.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                )
                LinearProgressIndicator(
                    modifier = Modifier
                        .width(144.dp)
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = "Built with love",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "by Chíjìọ́kẹ́ Paul.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun HomeViewState.shouldShowLaunchScreen(): Boolean {
    return permissionStatus == PermissionStatus.Unknown &&
        dataFreshness == DataFreshness.Loading &&
        isLoading
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

@Composable
private fun DisableDataControlWarningDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Turn off Data Control?",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = "Your app blocks and auto-block limits will stay saved, but Datameter will stop enforcing them until Data Control is turned on again.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Turn off")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep on")
            }
        },
    )
}

@Composable
private fun DataControlDisclosureDialog(
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Turn on Data Control",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = "Datameter uses a local Android VPN to measure and block app network traffic on this phone. Traffic is not sent to Datameter servers.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatameterTopBar(
    selectedFilter: NetworkFilter,
    onFilterSelected: (NetworkFilter) -> Unit,
) {
    TopAppBar(
        title = {
            Image(
                painter = painterResource(R.drawable.datameter_main_logo),
                contentDescription = "Datameter",
                modifier = Modifier
                    .width(124.dp)
                    .height(22.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatameterAppTopBar(
    title: String,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
            )
        },
        navigationIcon = {
            TextButton(
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.primary,
        ),
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

private fun UsageRow.packageNameOrNull(): String? {
    val packageName = id.removePrefix(APP_ROW_PREFIX)
    return packageName.takeIf {
        id.startsWith(APP_ROW_PREFIX) && it.isNotBlank() && !it.startsWith("uid:")
    }
}

private const val APP_ROW_PREFIX = "app:"

@Composable
private fun DatameterBottomBar(
    selectedDestination: DatameterDestination,
    onDestinationSelected: (DatameterDestination) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
    ) {
        DatameterDestination.entries.forEach { item ->
            NavigationBarItem(
                selected = selectedDestination == item,
                onClick = { onDestinationSelected(item) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        maxLines = 1,
                    )
                },
            )
        }
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
                    dataControlState = com.datameter.ui.control.DataControlUiState(),
                    onPeriodSelected = {},
                    onOpenUsageAccess = {},
                    onOpenAppSettings = {},
                    onOpenTimeline = {},
                    onRefresh = {},
                    onEnableDataControl = {},
                    onDisableDataControl = {},
                    onUsageRowSelected = {},
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
        permissionStatus = PermissionStatus.Granted,
        dataFreshness = DataFreshness.Fresh(now),
    )
}
