package com.datameter.data.control

import android.os.Build
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.intra.Bridge
import com.celzero.firestack.intra.Intra
import com.celzero.firestack.intra.Tunnel
import com.celzero.firestack.settings.Settings

class FirestackVpnEngine(
    private val bridge: Bridge,
) : DatameterVpnEngine {
    private var tunnel: Tunnel? = null

    override val isRunning: Boolean
        get() = tunnel?.isConnected == true

    override fun start(
        tunFd: Long,
        tunMtu: Int,
        interfaceAddresses: String,
        fakeDns: String,
    ) {
        if (isRunning) return

        Settings.setDebug(false)
        Settings.dupTunFd(false)
        val defaultDns = defaultDns()
        tunnel = Intra.connect(
            tunFd,
            tunMtu.toLong(),
            tunMtu.toLong(),
            interfaceAddresses,
            fakeDns,
            defaultDns,
            bridge,
        )
        Settings.setTunMode(
            Settings.DNSModePort,
            firewallMode(),
            Settings.PtModeAuto,
        )
    }

    override fun closeUid(uid: Int) {
        tunnel?.takeIf { it.isConnected }?.closeConns(uid.toString())
    }

    override fun stop() {
        val currentTunnel = tunnel ?: return
        runCatching { currentTunnel.unlink() }
        runCatching { currentTunnel.disconnect() }
        tunnel = null
    }

    private fun firewallMode(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Settings.BlockModeFilter
        } else {
            Settings.BlockModeFilterProc
        }
    }

    private fun defaultDns() = runCatching {
        Intra.newDefaultDNS(
            Backend.strOf(Backend.DNS53),
            Backend.strOf(DEFAULT_DNS_SERVERS),
            Backend.strOf(""),
        )
    }.getOrNull()

    private companion object {
        private const val DEFAULT_DNS_SERVERS = "9.9.9.9,2620:fe::fe"
    }
}
