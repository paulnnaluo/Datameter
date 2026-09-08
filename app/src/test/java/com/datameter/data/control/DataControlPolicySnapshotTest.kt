package com.datameter.data.control

import android.os.Process
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataControlPolicySnapshotTest {
    @Test
    fun `manual mobile block only blocks mobile traffic`() {
        val snapshot = policySnapshot(
            rulesByUid = mapOf(
                APP_UID to DataControlRule.default(APP_UID, PACKAGE_NAME, LABEL)
                    .copy(blockMobileData = true),
            ),
        )

        assertEquals(
            DataControlBlockReason.ManualMobile,
            snapshot.blockReasonFor(APP_UID, DataControlNetworkType.Mobile),
        )
        assertNull(snapshot.blockReasonFor(APP_UID, DataControlNetworkType.Wifi))
    }

    @Test
    fun `global limit creates auto block when mobile usage crosses limit`() {
        val snapshot = policySnapshot(
            settings = DataControlSettings(
                enabled = true,
                disclosureAccepted = true,
                globalAutoBlockEnabled = true,
                globalAutoBlockBytes = LIMIT_BYTES,
            ),
        )

        assertNull(snapshot.autoBlockReasonFor(APP_UID, LIMIT_BYTES - 1L))
        assertEquals(
            DataControlBlockReason.GlobalLimit,
            snapshot.autoBlockReasonFor(APP_UID, LIMIT_BYTES),
        )
    }

    @Test
    fun `auto block only blocks mobile traffic`() {
        val snapshot = policySnapshot(
            rulesByUid = mapOf(
                APP_UID to DataControlRule.default(APP_UID, PACKAGE_NAME, LABEL)
                    .copy(
                        autoBlockedLocalDate = TODAY,
                        autoBlockedReason = DataControlBlockReason.GlobalLimit,
                    ),
            ),
        )

        assertEquals(
            DataControlBlockReason.GlobalLimit,
            snapshot.blockReasonFor(APP_UID, DataControlNetworkType.Mobile),
        )
        assertNull(snapshot.blockReasonFor(APP_UID, DataControlNetworkType.Wifi))
    }

    @Test
    fun `controlled uids follow the active network`() {
        val snapshot = policySnapshot(
            rulesByUid = mapOf(
                APP_UID to DataControlRule.default(APP_UID, PACKAGE_NAME, LABEL)
                    .copy(blockMobileData = true),
                WIFI_APP_UID to DataControlRule.default(WIFI_APP_UID, WIFI_PACKAGE_NAME, WIFI_LABEL)
                    .copy(blockWifi = true),
            ),
        )

        assertEquals(setOf(APP_UID), snapshot.controlledUidsFor(DataControlNetworkType.Mobile))
        assertEquals(setOf(WIFI_APP_UID), snapshot.controlledUidsFor(DataControlNetworkType.Wifi))
    }

    @Test
    fun `self and system uids are never blocked`() {
        val snapshot = policySnapshot(
            settings = DataControlSettings(
                enabled = true,
                disclosureAccepted = true,
                globalAutoBlockEnabled = true,
                globalAutoBlockBytes = 1L,
            ),
            selfUid = SELF_UID,
        )

        assertNull(snapshot.blockReasonFor(SELF_UID, DataControlNetworkType.Mobile))
        assertNull(snapshot.autoBlockReasonFor(SELF_UID, Long.MAX_VALUE))
        assertNull(snapshot.blockReasonFor(Process.SYSTEM_UID, DataControlNetworkType.Mobile))
        assertNull(snapshot.autoBlockReasonFor(Process.SYSTEM_UID, Long.MAX_VALUE))
    }

    private fun policySnapshot(
        settings: DataControlSettings = DataControlSettings(
            enabled = true,
            disclosureAccepted = true,
        ),
        rulesByUid: Map<Int, DataControlRule> = emptyMap(),
        selfUid: Int = SELF_UID,
    ): DataControlPolicySnapshot {
        return DataControlPolicySnapshot(
            settings = settings,
            rulesByUid = rulesByUid,
            todayMobileBytesByUid = emptyMap(),
            localDate = TODAY,
            selfUid = selfUid,
        )
    }

    private companion object {
        const val APP_UID = Process.FIRST_APPLICATION_UID + 42
        const val SELF_UID = Process.FIRST_APPLICATION_UID + 7
        const val WIFI_APP_UID = Process.FIRST_APPLICATION_UID + 55
        const val PACKAGE_NAME = "com.example.app"
        const val LABEL = "Example"
        const val WIFI_PACKAGE_NAME = "com.example.wifi"
        const val WIFI_LABEL = "Wifi Example"
        const val LIMIT_BYTES = 1_000L
        const val TODAY = "2026-09-08"
    }
}
