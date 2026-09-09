package com.datameter.ui.control

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.datameter.core.DatameterServiceLocator
import com.datameter.data.control.DataControlRunStatus
import com.datameter.data.control.DataControlRuntimeState
import com.datameter.data.control.DataControlSettings
import com.datameter.data.control.DataControlVpnController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class DataControlUiState(
    val settings: DataControlSettings = DataControlSettings(),
    val runtimeState: DataControlRuntimeState = DataControlRuntimeState(),
    val blockedAppsToday: Int = 0,
    val activeRuleCount: Int = 0,
    val monitoredUids: Set<Int> = emptySet(),
) {
    val isRunning: Boolean
        get() = runtimeState.status == DataControlRunStatus.Active
}

class DataControlViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = DatameterServiceLocator.dataControlRepository(appContext)

    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<DataControlUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            DataControlVpnController.runtimeState.collect { runtimeState ->
                _uiState.value = loadState(runtimeState)
            }
        }
    }

    fun refresh() {
        _uiState.value = loadState(DataControlVpnController.runtimeState.value)
    }

    fun acceptDisclosure() {
        repository.acceptDisclosure()
        refresh()
    }

    fun enableAfterVpnConsent() {
        val settings = repository.readSettings().copy(
            enabled = true,
            disclosureAccepted = true,
        )
        repository.saveSettings(settings)
        _uiState.value = loadState(DataControlRuntimeState(DataControlRunStatus.Starting))
        DataControlVpnController.start(appContext)
    }

    fun disableDataControl() {
        repository.saveSettings(repository.readSettings().copy(enabled = false))
        _uiState.value = loadState(DataControlRuntimeState(DataControlRunStatus.Off))
        DataControlVpnController.stop(appContext)
    }

    fun setGlobalAutoBlockEnabled(enabled: Boolean) {
        repository.saveSettings(repository.readSettings().copy(globalAutoBlockEnabled = enabled))
        refresh()
        DataControlVpnController.reloadPolicy(appContext)
    }

    fun setGlobalAutoBlockBytes(bytes: Long) {
        repository.saveSettings(
            repository.readSettings().copy(globalAutoBlockBytes = bytes.coerceAtLeast(1L)),
        )
        refresh()
        DataControlVpnController.reloadPolicy(appContext)
    }

    private fun loadState(
        runtimeState: DataControlRuntimeState = DataControlVpnController.runtimeState.value,
    ): DataControlUiState {
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        val settings = repository.readSettings()
        val visibleRuntimeState = if (
            settings.enabled &&
            runtimeState.status == DataControlRunStatus.Off
        ) {
            DataControlRuntimeState(
                status = DataControlRunStatus.Standby,
                message = "Choose an app rule or turn on app auto-block.",
            )
        } else {
            runtimeState
        }
        return DataControlUiState(
            settings = settings,
            runtimeState = visibleRuntimeState,
            blockedAppsToday = repository.blockedAppsCount(today),
            activeRuleCount = repository.activeRuleCount(),
            monitoredUids = if (settings.enabled) {
                repository.activeControlledUids(today)
            } else {
                emptySet()
            },
        )
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DataControlViewModel(application) as T
                }
            }
        }
    }
}
