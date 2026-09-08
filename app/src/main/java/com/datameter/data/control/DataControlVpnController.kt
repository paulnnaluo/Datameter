package com.datameter.data.control

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DataControlVpnController {
    private val _runtimeState = MutableStateFlow(DataControlRuntimeState())
    val runtimeState: StateFlow<DataControlRuntimeState> = _runtimeState.asStateFlow()

    fun prepareIntent(context: Context): Intent? {
        return VpnService.prepare(context.applicationContext)
    }

    fun start(context: Context) {
        _runtimeState.value = DataControlRuntimeState(DataControlRunStatus.Starting)
        ContextCompat.startForegroundService(
            context.applicationContext,
            Intent(context.applicationContext, DataControlVpnService::class.java)
                .setAction(DataControlVpnService.ACTION_START),
        )
    }

    fun stop(context: Context) {
        context.applicationContext.startService(
            Intent(context.applicationContext, DataControlVpnService::class.java)
                .setAction(DataControlVpnService.ACTION_STOP),
        )
    }

    fun reloadPolicy(context: Context, uid: Int? = null) {
        if (_runtimeState.value.status == DataControlRunStatus.Standby) {
            start(context)
            return
        }

        val intent = Intent(context.applicationContext, DataControlVpnService::class.java)
            .setAction(DataControlVpnService.ACTION_RELOAD_POLICY)
        if (uid != null) intent.putExtra(DataControlVpnService.EXTRA_UID, uid)

        if (_runtimeState.value.status == DataControlRunStatus.Active) {
            context.applicationContext.startService(intent)
        }
    }

    internal fun update(state: DataControlRuntimeState) {
        _runtimeState.value = state
    }
}
