package com.datameter.data.control

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.core.app.NotificationCompat
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.backend.DNSOpts
import com.celzero.firestack.backend.DNSSummary
import com.celzero.firestack.backend.Gostr
import com.celzero.firestack.backend.ServerSummary
import com.celzero.firestack.backend.Tab
import com.celzero.firestack.intra.Bridge
import com.celzero.firestack.intra.Mark
import com.celzero.firestack.intra.PreMark
import com.celzero.firestack.intra.SocketSummary
import com.datameter.MainActivity
import com.datameter.R
import com.datameter.core.DatameterServiceLocator
import com.datameter.data.alerts.DatameterNotificationChannels
import com.datameter.domain.ByteFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class DataControlVpnService : VpnService(), Bridge {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val policyRef = AtomicReference(DataControlPolicySnapshot())
    private val pendingUsageBytes = ConcurrentHashMap<UsageBucketKey, AtomicLong>()
    private val todayMobileBytesByUid = ConcurrentHashMap<Int, AtomicLong>()
    private val identityCache = ConcurrentHashMap<Int, DataControlAppIdentity>()
    private val autoBlockedUids = ConcurrentHashMap.newKeySet<Int>()
    private val commandMutex = Mutex()

    private lateinit var repository: DataControlRepository
    private lateinit var appResolver: DataControlAppResolver
    private lateinit var connectivityManager: ConnectivityManager
    private var engine: DatameterVpnEngine? = null
    private var flushJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var destroyStateOverride: DataControlRuntimeState? = null

    @Volatile
    private var activeNetworkType = DataControlNetworkType.Unknown

    override fun onCreate() {
        super.onCreate()
        repository = DatameterServiceLocator.dataControlRepository(this)
        appResolver = DataControlAppResolver(this)
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        DatameterNotificationChannels.ensureCreated(this)
        refreshActiveNetworkType()
        registerNetworkCallback()
        DataControlVpnController.update(DataControlRuntimeState(DataControlRunStatus.Starting))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat("Starting Data Control")

        when (intent?.action) {
            ACTION_STOP -> serviceScope.launch {
                commandMutex.withLock {
                    stopDataControl(userInitiated = true)
                }
            }

            ACTION_RELOAD_POLICY -> serviceScope.launch {
                commandMutex.withLock {
                    restartDataControl()
                }
            }

            else -> serviceScope.launch {
                commandMutex.withLock {
                    startDataControl()
                }
            }
        }

        return START_STICKY
    }

    override fun onRevoke() {
        repository.saveSettings(repository.readSettings().copy(enabled = false))
        runBlocking(Dispatchers.IO) {
            stopDataControl(userInitiated = false)
        }
        super.onRevoke()
    }

    override fun onDestroy() {
        runBlocking(Dispatchers.IO) {
            stopEngineOnly()
        }
        unregisterNetworkCallback()
        serviceScope.cancel()
        DataControlVpnController.update(
            destroyStateOverride ?: DataControlRuntimeState(DataControlRunStatus.Off),
        )
        super.onDestroy()
    }

    override fun protect(who: String?, fid: Long) {
        protectFd(fid)
    }

    override fun bind4(who: String?, addrPort: String?, fid: Long) {
        bindFdToActiveNetwork(fid)
    }

    override fun bind6(who: String?, addrPort: String?, fid: Long) {
        bindFdToActiveNetwork(fid)
    }

    override fun preflow(protocol: Int, uid: Int, src: Gostr?, dst: Gostr?): PreMark {
        val resolvedUid = resolveFlowUid(protocol, uid, src?.value(), dst?.value())
        return PreMark().apply {
            setUID(resolvedUid.toString())
            setIsUidSelf(resolvedUid == applicationInfo.uid)
        }
    }

    override fun flow(
        protocol: Int,
        uid: Int,
        src: Gostr?,
        dst: Gostr?,
        realIps: Gostr?,
        domains: Gostr?,
        probableDomains: Gostr?,
        blocklists: Gostr?,
    ): Mark {
        val resolvedUid = resolveFlowUid(protocol, uid, src?.value(), dst?.value())
        val reason = policyRef.get().blockReasonFor(resolvedUid, activeNetworkType)
        return Mark().apply {
            setPIDCSV(if (reason == null) Backend.Base else Backend.Block)
            setUID(resolvedUid.toString())
            setCID(flowId(resolvedUid, protocol, src?.value(), dst?.value()))
        }
    }

    override fun inflow(protocol: Int, recvdUid: Int, src: Gostr?, dst: Gostr?): Mark {
        return Mark().apply {
            setPIDCSV(Backend.Base)
            setUID(recvdUid.toString())
            setCID(flowId(recvdUid, protocol, src?.value(), dst?.value()))
        }
    }

    override fun postFlow(mark: Mark?) {
        // No-op. Datameter records coarse byte totals only when a socket closes.
    }

    override fun onSocketClosed(summary: SocketSummary?) {
        val uid = summary?.getUID()?.toIntOrNull() ?: return
        if (uid < Process.FIRST_APPLICATION_UID || uid == applicationInfo.uid) return

        val bytes = summary.getRx().coerceAtLeast(0L) + summary.getTx().coerceAtLeast(0L)
        if (bytes <= 0L) return

        recordClosedFlow(uid, bytes)
    }

    override fun onQuery(uid: Gostr?, qname: Gostr?, qtype: Long): DNSOpts {
        return DNSOpts().apply {
            setPIDCSV(Backend.Base)
            setTIDCSV(Backend.Default)
            setNOBLOCK(true)
        }
    }

    override fun onUpstreamAnswer(summary: DNSSummary?, ipcsv: Gostr?): DNSOpts {
        return DNSOpts().apply {
            setPIDCSV(Backend.Base)
            setTIDCSV(Backend.Default)
            setNOBLOCK(true)
        }
    }

    override fun onResponse(summary: DNSSummary?) = Unit
    override fun onDNSAdded(id: Gostr?) = Unit
    override fun onDNSRemoved(id: Gostr?) = Unit
    override fun onDNSStopped() = Unit
    override fun onProxiesStopped() = Unit
    override fun onProxyAdded(id: Gostr?) = Unit
    override fun onProxyRemoved(id: Gostr?) = Unit
    override fun onProxyStopped(id: Gostr?) = Unit
    override fun onSvcComplete(summary: ServerSummary?) = Unit
    override fun log(level: Int, message: Gostr?) = Unit

    override fun svcRoute(
        sid: String?,
        pid: String?,
        network: String?,
        sipport: String?,
        dipport: String?,
    ): Tab {
        return Tab().apply {
            setCID("")
            setBlock(false)
        }
    }

    private suspend fun startDataControl() {
        destroyStateOverride = null
        val settings = repository.readSettings()
        if (!settings.enabled || !settings.disclosureAccepted) {
            stopDataControl(userInitiated = false)
            return
        }

        if (prepare(this) != null) {
            DataControlVpnController.update(
                DataControlRuntimeState(
                    status = DataControlRunStatus.NeedsVpnPermission,
                    message = "Android VPN permission is needed.",
                ),
            )
            stopDataControl(userInitiated = false)
            return
        }

        refreshActiveNetworkType()
        val snapshot = reloadPolicy(closeUid = Process.INVALID_UID)
        val routedPackages = routedPackagesFor(snapshot)
        if (routedPackages.isEmpty()) {
            enterStandby("Choose an app rule or turn on app auto-block.")
            return
        }

        if (engine?.isRunning == true) {
            DataControlVpnController.update(DataControlRuntimeState(DataControlRunStatus.Active))
            startForegroundCompat("Data Control is active")
            return
        }

        val tun = withContext(Dispatchers.Main) {
            establishTunnel(routedPackages)
        }
        if (tun == null) {
            failAndStop("Could not start Data Control.")
            return
        }

        val rawTunFd = tun.detachFd()
        try {
            val nextEngine = FirestackVpnEngine(this)
            nextEngine.start(
                tunFd = rawTunFd.toLong(),
                tunMtu = VPN_INTERFACE_MTU,
                interfaceAddresses = firestackInterfaceAddresses(),
                fakeDns = firestackFakeDns(),
            )
            engine = nextEngine
            startFlushLoop()
            DataControlVpnController.update(DataControlRuntimeState(DataControlRunStatus.Active))
            startForegroundCompat("Data Control is active")
        } catch (throwable: Throwable) {
            runCatching { ParcelFileDescriptor.adoptFd(rawTunFd).close() }
            failAndStop(throwable.message ?: "Data Control could not start.")
        }
    }

    private fun stopDataControl(userInitiated: Boolean) {
        destroyStateOverride = DataControlRuntimeState(DataControlRunStatus.Off)
        if (userInitiated) {
            repository.saveSettings(repository.readSettings().copy(enabled = false))
        }
        stopEngineOnly()
        DataControlVpnController.update(DataControlRuntimeState(DataControlRunStatus.Off))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun restartDataControl() {
        stopEngineOnly()
        startDataControl()
    }

    private fun enterStandby(message: String) {
        val state = DataControlRuntimeState(
            status = DataControlRunStatus.Standby,
            message = message,
        )
        destroyStateOverride = state
        stopEngineOnly()
        DataControlVpnController.update(state)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopEngineOnly() {
        flushJob?.cancel()
        flushJob = null
        flushUsage()
        engine?.stop()
        engine = null
        pendingUsageBytes.clear()
    }

    private fun failAndStop(message: String) {
        val state = DataControlRuntimeState(
            status = DataControlRunStatus.Error,
            message = message,
        )
        destroyStateOverride = state
        DataControlVpnController.update(state)
        stopEngineOnly()
        stopSelf()
    }

    private fun reloadPolicy(closeUid: Int): DataControlPolicySnapshot {
        val date = today()
        val snapshot = repository.loadPolicySnapshot(date, applicationInfo.uid)
        policyRef.set(snapshot)

        todayMobileBytesByUid.clear()
        snapshot.todayMobileBytesByUid.forEach { (uid, bytes) ->
            todayMobileBytesByUid[uid] = AtomicLong(bytes)
        }

        autoBlockedUids.clear()
        snapshot.rulesByUid.values
            .filter { it.isAutoBlockedToday(date) }
            .forEach { autoBlockedUids.add(it.uid) }

        if (closeUid != Process.INVALID_UID) {
            val reason = snapshot.blockReasonFor(closeUid, activeNetworkType)
            if (reason != null) {
                engine?.closeUid(closeUid)
            }
        }

        return snapshot
    }

    private fun recordClosedFlow(uid: Int, bytes: Long) {
        val identity = identityCache.getOrPut(uid) { appResolver.resolveUid(uid) }
        val networkType = activeNetworkType
        val localDate = today()
        val usageKey = UsageBucketKey(
            localDate = localDate,
            uid = identity.uid,
            packageName = identity.packageName,
            label = identity.displayLabel,
            networkType = networkType,
        )
        pendingUsageBytes.getOrPut(usageKey) { AtomicLong(0L) }.addAndGet(bytes)

        if (networkType == DataControlNetworkType.Mobile) {
            val total = todayMobileBytesByUid
                .getOrPut(uid) { AtomicLong(policyRef.get().todayMobileBytesByUid[uid] ?: 0L) }
                .addAndGet(bytes)
            maybeAutoBlock(identity, localDate, total)
        }
    }

    private fun maybeAutoBlock(
        identity: DataControlAppIdentity,
        localDate: String,
        mobileBytesToday: Long,
    ) {
        val snapshot = policyRef.get()
        val reason = snapshot.autoBlockReasonFor(identity.uid, mobileBytesToday) ?: return
        if (!autoBlockedUids.add(identity.uid)) return

        val limit = when (reason) {
            DataControlBlockReason.AppLimit -> snapshot.rulesByUid[identity.uid]?.dailyLimitBytes
            DataControlBlockReason.GlobalLimit -> snapshot.settings.globalAutoBlockBytes
            else -> null
        }
        serviceScope.launch {
            repository.markAutoBlocked(
                identity = identity,
                localDate = localDate,
                reason = reason,
                networkType = DataControlNetworkType.Mobile,
                usedBytes = mobileBytesToday,
                limitBytes = limit,
                createdAtMillis = System.currentTimeMillis(),
            )
            reloadPolicy(closeUid = identity.uid)
            DatameterServiceLocator.notificationDispatcher(this@DataControlVpnService)
                .notifyDataControlBlocked(
                    appLabel = identity.displayLabel,
                    usedBytes = mobileBytesToday,
                    limitBytes = limit,
                    globalRule = reason == DataControlBlockReason.GlobalLimit,
                )
        }
    }

    private fun flushUsage() {
        pendingUsageBytes.forEach { (key, counter) ->
            val bytes = counter.getAndSet(0L)
            if (bytes <= 0L) return@forEach

            runCatching {
                repository.recordUsage(
                    identity = DataControlAppIdentity(
                        uid = key.uid,
                        packageName = key.packageName,
                        label = key.label,
                        packageNames = listOf(key.packageName),
                    ),
                    localDate = key.localDate,
                    networkType = key.networkType,
                    bytes = bytes,
                )
            }.onFailure {
                counter.addAndGet(bytes)
            }
        }
    }

    private fun startFlushLoop() {
        if (flushJob?.isActive == true) return
        flushJob = serviceScope.launch {
            while (isActive) {
                delay(USAGE_FLUSH_INTERVAL_MILLIS)
                flushUsage()
            }
        }
    }

    private fun establishTunnel(routedPackages: Set<String>): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession("Datameter Data Control")
            .setMtu(VPN_INTERFACE_MTU)
            .setConfigureIntent(contentIntent())

        builder.addAddress(VPN_IPV4_ADDRESS, VPN_IPV4_PREFIX_LENGTH)
        builder.addRoute("0.0.0.0", 0)
        builder.addDnsServer(VPN_IPV4_DNS)

        runCatching {
            builder.addAddress(VPN_IPV6_ADDRESS, VPN_IPV6_PREFIX_LENGTH)
            builder.addRoute("::", 0)
            builder.addDnsServer(VPN_IPV6_DNS)
        }

        routedPackages
            .filterNot { it == packageName }
            .sorted()
            .forEach { routedPackage ->
                runCatching {
                    builder.addAllowedApplication(routedPackage)
                }
            }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(activeNetworkType == DataControlNetworkType.Mobile)
            }
        }
        runCatching {
            currentUnderlyingNetwork()?.let { builder.setUnderlyingNetworks(arrayOf(it)) }
        }

        return runCatching { builder.establish() }.getOrNull()
    }

    private fun routedPackagesFor(snapshot: DataControlPolicySnapshot): Set<String> {
        if (!snapshot.settings.enabled) return emptySet()

        if (
            snapshot.settings.globalAutoBlockEnabled &&
            activeNetworkType == DataControlNetworkType.Mobile
        ) {
            return appResolver.controllableInstalledPackages()
        }

        val controlledUids = snapshot.controlledUidsFor(activeNetworkType)
        return controlledUids
            .flatMap { uid -> appResolver.resolveUid(uid).packageNames }
            .filter { it.isNotBlank() && it != packageName && !it.startsWith("uid:") }
            .toSet()
    }

    private fun startForegroundCompat(message: String) {
        val notification = foregroundNotification(message)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST,
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun foregroundNotification(message: String): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            Intent(this, DataControlVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(
            this,
            DatameterNotificationChannels.DATA_CONTROL_CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_notification_datameter)
            .setContentTitle("Data Control is active")
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$message. Datameter is measuring and applying your app data rules locally.",
                ),
            )
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Turn off", stopIntent)
            .build()
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            CONTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun registerNetworkCallback() {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateUnderlyingNetwork(network)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                updateUnderlyingNetwork(network, capabilities)
            }

            override fun onLost(network: Network) {
                val previous = activeNetworkType
                runCatching { refreshActiveNetworkType() }
                restartForNetworkChange(previous, activeNetworkType)
            }
        }
        networkCallback = callback
        runCatching { connectivityManager.registerDefaultNetworkCallback(callback) }
    }

    private fun updateUnderlyingNetwork(
        network: Network,
        capabilities: NetworkCapabilities? = runCatching {
            connectivityManager.getNetworkCapabilities(network)
        }.getOrNull(),
    ) {
        if (capabilities?.isDatameterUnderlyingNetwork() != true) return

        val previous = activeNetworkType
        runCatching { setUnderlyingNetworks(arrayOf(network)) }
        activeNetworkType = capabilities.toDataControlNetworkType()
        restartForNetworkChange(previous, activeNetworkType)
    }

    private fun restartForNetworkChange(
        previous: DataControlNetworkType,
        current: DataControlNetworkType,
    ) {
        if (previous == current || !repository.readSettings().enabled) return

        serviceScope.launch {
            commandMutex.withLock {
                restartDataControl()
            }
        }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        networkCallback = null
    }

    private fun refreshActiveNetworkType() {
        activeNetworkType = runCatching {
            val capabilities = currentUnderlyingNetwork()
                ?.let(connectivityManager::getNetworkCapabilities)
            when {
                capabilities != null -> capabilities.toDataControlNetworkType()
                connectivityManager.isActiveNetworkMetered -> DataControlNetworkType.Mobile
                else -> DataControlNetworkType.Unknown
            }
        }.getOrDefault(DataControlNetworkType.Unknown)
    }

    private fun resolveFlowUid(protocol: Int, uid: Int, src: String?, dst: String?): Int {
        if (uid >= Process.FIRST_APPLICATION_UID) return uid
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return uid

        val local = src?.toInetSocketAddress() ?: return uid
        val remote = dst?.toInetSocketAddress() ?: return uid
        return runCatching {
            connectivityManager.getConnectionOwnerUid(protocol, local, remote)
        }.getOrDefault(uid)
    }

    private fun protectFd(fid: Long) {
        val intFd = fid.toInt()
        if (intFd < 0) return
        runCatching { protect(intFd) }
    }

    private fun bindFdToActiveNetwork(fid: Long) {
        val intFd = fid.toInt()
        if (intFd < 0) return
        protectFd(fid)

        val network = currentUnderlyingNetwork() ?: return
        val descriptor = ParcelFileDescriptor.adoptFd(intFd)
        try {
            network.bindSocket(descriptor.fileDescriptor)
        } catch (_: Throwable) {
        } finally {
            runCatching { descriptor.detachFd() }
        }
    }

    private fun currentUnderlyingNetwork(): Network? {
        val activeNetwork = connectivityManager.activeNetwork
        val activeCapabilities = activeNetwork?.let {
            runCatching { connectivityManager.getNetworkCapabilities(it) }.getOrNull()
        }
        if (activeNetwork != null && activeCapabilities?.isDatameterUnderlyingNetwork() == true) {
            return activeNetwork
        }

        return runCatching {
            connectivityManager.allNetworks.firstOrNull { network ->
                connectivityManager.getNetworkCapabilities(network)
                    ?.isDatameterUnderlyingNetwork() == true
            }
        }.getOrNull()
    }

    private fun NetworkCapabilities.isDatameterUnderlyingNetwork(): Boolean {
        return hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            !hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun NetworkCapabilities.toDataControlNetworkType(): DataControlNetworkType {
        return when {
            hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> DataControlNetworkType.Mobile
            hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> DataControlNetworkType.Wifi
            else -> DataControlNetworkType.Unknown
        }
    }

    private fun String.toInetSocketAddress(): InetSocketAddress? {
        val clean = trim()
            .removePrefix("/")
            .substringBefore("%")
            .trim()
        if (clean.isBlank()) return null

        val host: String
        val portText: String
        if (clean.startsWith("[")) {
            val endBracket = clean.indexOf("]")
            if (endBracket <= 0) return null
            host = clean.substring(1, endBracket)
            portText = clean.substringAfter("]:", "")
        } else {
            val divider = clean.lastIndexOf(":")
            if (divider <= 0 || divider == clean.lastIndex) return null
            host = clean.substring(0, divider)
            portText = clean.substring(divider + 1)
        }

        val port = portText.toIntOrNull() ?: return null
        return runCatching {
            InetSocketAddress(InetAddress.getByName(host), port)
        }.getOrNull()
    }

    private fun Gostr.value(): String {
        return runCatching { v() }.getOrElse { runCatching { string() }.getOrDefault("") }
    }

    private fun flowId(uid: Int, protocol: Int, src: String?, dst: String?): String {
        return "$uid:$protocol:${src.orEmpty()}:${dst.orEmpty()}:${System.nanoTime()}"
    }

    private fun firestackInterfaceAddresses(): String {
        return "$VPN_IPV4_ADDRESS,$VPN_IPV6_ADDRESS"
    }

    private fun firestackFakeDns(): String {
        return "$VPN_IPV4_DNS:53,[$VPN_IPV6_DNS]:53"
    }

    private fun today(): String {
        return LocalDate.now(ZoneId.systemDefault()).toString()
    }

    private data class UsageBucketKey(
        val localDate: String,
        val uid: Int,
        val packageName: String,
        val label: String,
        val networkType: DataControlNetworkType,
    )

    companion object {
        const val ACTION_START = "com.datameter.data.control.START"
        const val ACTION_STOP = "com.datameter.data.control.STOP"
        const val ACTION_RELOAD_POLICY = "com.datameter.data.control.RELOAD_POLICY"
        const val EXTRA_UID = "uid"

        private const val VPN_INTERFACE_MTU = 1500
        private const val VPN_IPV4_ADDRESS = "10.111.222.1"
        private const val VPN_IPV4_DNS = "10.111.222.3"
        private const val VPN_IPV4_PREFIX_LENGTH = 24
        private const val VPN_IPV6_ADDRESS = "fd66:f83a:c650::1"
        private const val VPN_IPV6_DNS = "fd66:f83a:c650::3"
        private const val VPN_IPV6_PREFIX_LENGTH = 120
        private const val USAGE_FLUSH_INTERVAL_MILLIS = 45_000L
        private const val FOREGROUND_NOTIFICATION_ID = 3001
        private const val STOP_REQUEST_CODE = 3002
        private const val CONTENT_REQUEST_CODE = 3003
    }
}
