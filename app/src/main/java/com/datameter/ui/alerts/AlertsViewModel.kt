package com.datameter.ui.alerts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.datameter.core.DatameterServiceLocator
import com.datameter.data.alerts.AlertSettings
import com.datameter.data.alerts.AlertSettingsRepository
import com.datameter.data.alerts.UsageAlertScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlertsViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository: AlertSettingsRepository =
        DatameterServiceLocator.alertSettingsRepository(appContext)

    private val _uiState = MutableStateFlow(repository.read())
    val uiState: StateFlow<AlertSettings> = _uiState.asStateFlow()

    fun setDailyLimitEnabled(enabled: Boolean) {
        update { it.copy(dailyLimitEnabled = enabled) }
    }

    fun setDailyLimitBytes(bytes: Long) {
        update { it.copy(dailyLimitBytes = bytes) }
    }

    fun setHourlySpikeEnabled(enabled: Boolean) {
        update { it.copy(hourlySpikeEnabled = enabled) }
    }

    fun setHourlySpikeBytes(bytes: Long) {
        update { it.copy(hourlySpikeBytes = bytes) }
    }

    private fun update(block: (AlertSettings) -> AlertSettings) {
        val next = block(_uiState.value)
        repository.save(next)
        UsageAlertScheduler.sync(appContext)
        _uiState.value = next
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AlertsViewModel(application) as T
                }
            }
        }
    }
}
