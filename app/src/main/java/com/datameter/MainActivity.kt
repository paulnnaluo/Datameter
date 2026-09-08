package com.datameter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.datameter.data.alerts.DatameterNotificationChannels
import com.datameter.data.alerts.UsageAlertScheduler
import com.datameter.core.DatameterServiceLocator
import com.datameter.data.control.DataControlVpnController
import com.datameter.ui.DatameterRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DatameterRoot()
        }
        lifecycleScope.launch(Dispatchers.Default) {
            DatameterNotificationChannels.ensureCreated(this@MainActivity)
            UsageAlertScheduler.sync(this@MainActivity)
            val dataControlSettings = DatameterServiceLocator
                .dataControlRepository(this@MainActivity)
                .readSettings()
            if (dataControlSettings.enabled && dataControlSettings.disclosureAccepted) {
                DataControlVpnController.start(this@MainActivity)
            }
        }
    }
}
