package com.datameter.data.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.datameter.core.DatameterServiceLocator
import com.datameter.data.control.DataControlVpnController

class UsageAlertBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            UsageAlertScheduler.sync(context)
            val settings = DatameterServiceLocator.dataControlRepository(context).readSettings()
            if (settings.enabled && settings.disclosureAccepted) {
                DataControlVpnController.start(context)
            }
        }
    }
}
