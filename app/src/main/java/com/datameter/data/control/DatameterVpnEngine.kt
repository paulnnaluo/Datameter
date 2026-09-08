package com.datameter.data.control

interface DatameterVpnEngine {
    fun start(
        tunFd: Long,
        tunMtu: Int,
        interfaceAddresses: String,
        fakeDns: String,
    )
    fun closeUid(uid: Int)
    fun stop()
    val isRunning: Boolean
}
