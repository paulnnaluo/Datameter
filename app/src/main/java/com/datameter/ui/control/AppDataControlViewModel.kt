package com.datameter.ui.control

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.datameter.core.DatameterServiceLocator
import com.datameter.data.control.DataControlAppResolver
import com.datameter.data.control.DataControlBlockEvent
import com.datameter.data.control.DataControlDailyUsage
import com.datameter.data.control.DataControlRule
import com.datameter.data.control.DataControlVpnController
import com.datameter.data.control.DEFAULT_APP_LIMIT_BYTES
import com.datameter.domain.model.UsagePeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.ZoneId

data class SelectedUsageApp(
    val uid: Int,
    val packageName: String,
    val label: String,
    val selectedPeriod: UsagePeriod,
    val selectedPeriodBytes: Long,
)

data class AppDataControlUiState(
    val app: SelectedUsageApp,
    val rule: DataControlRule,
    val todayUsage: DataControlDailyUsage?,
    val recentEvents: List<DataControlBlockEvent>,
)

class AppDataControlViewModel(
    application: Application,
    private val app: SelectedUsageApp,
) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = DatameterServiceLocator.dataControlRepository(appContext)
    private val appResolver = DataControlAppResolver(appContext)

    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<AppDataControlUiState> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.value = loadState()
    }

    fun setBlockMobileData(enabled: Boolean) {
        updateRule { it.copy(blockMobileData = enabled) }
    }

    fun setBlockWifi(enabled: Boolean) {
        updateRule { it.copy(blockWifi = enabled) }
    }

    fun setDailyLimitEnabled(enabled: Boolean) {
        updateRule { it.copy(dailyLimitEnabled = enabled) }
    }

    fun setDailyLimitBytes(bytes: Long) {
        updateRule {
            it.copy(dailyLimitBytes = bytes.coerceAtLeast(DEFAULT_APP_LIMIT_BYTES / 10L))
        }
    }

    fun clearAutoBlock() {
        repository.clearAutoBlock(app.uid)
        refresh()
        DataControlVpnController.reloadPolicy(appContext, app.uid)
    }

    private fun updateRule(block: (DataControlRule) -> DataControlRule) {
        val current = _uiState.value.rule
        val next = block(current)
        repository.saveRule(next)
        _uiState.value = loadState(rule = next)
        DataControlVpnController.reloadPolicy(appContext, app.uid)
    }

    private fun loadState(rule: DataControlRule? = null): AppDataControlUiState {
        val identity = appResolver.identityFor(app.uid, app.packageName, app.label)
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        val resolvedRule = rule ?: repository.readRule(
            uid = identity.uid,
            packageName = identity.packageName,
            label = identity.displayLabel,
        )
        return AppDataControlUiState(
            app = app.copy(
                packageName = identity.packageName,
                label = identity.displayLabel,
            ),
            rule = resolvedRule,
            todayUsage = repository.readDailyUsage(app.uid, today),
            recentEvents = repository.recentBlockEvents(app.uid, RECENT_EVENT_LIMIT),
        )
    }

    companion object {
        fun factory(
            application: Application,
            app: SelectedUsageApp,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AppDataControlViewModel(application, app) as T
                }
            }
        }

        private const val RECENT_EVENT_LIMIT = 8
    }
}
