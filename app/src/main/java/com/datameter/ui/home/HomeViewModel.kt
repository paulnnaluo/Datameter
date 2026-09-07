package com.datameter.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.datameter.core.DatameterServiceLocator
import com.datameter.data.usage.UsageAccessManager
import com.datameter.domain.HomeUsageRepository
import com.datameter.domain.model.DataFreshness
import com.datameter.domain.model.HomeViewState
import com.datameter.domain.model.NetworkFilter
import com.datameter.domain.model.PermissionStatus
import com.datameter.domain.model.UsagePeriod
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: HomeUsageRepository =
        DatameterServiceLocator.homeUsageRepository(application)
    private val usageAccessManager: UsageAccessManager =
        DatameterServiceLocator.usageAccessManager(application)

    private var selectedNetworkFilter = NetworkFilter.Mobile
    private var selectedPeriod = UsagePeriod.Today
    private var refreshJob: Job? = null

    private val _uiState = MutableStateFlow(HomeViewState.initial())
    val uiState: StateFlow<HomeViewState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun selectNetworkFilter(filter: NetworkFilter) {
        if (selectedNetworkFilter == filter) return
        selectedNetworkFilter = filter
        refresh()
    }

    fun selectPeriod(period: UsagePeriod) {
        if (selectedPeriod == period) return
        selectedPeriod = period
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedNetworkFilter = selectedNetworkFilter,
                selectedPeriod = selectedPeriod,
                isLoading = true,
                dataFreshness = DataFreshness.Loading,
            )

            _uiState.value = runCatching {
                repository.loadHomeState(selectedNetworkFilter, selectedPeriod)
            }.getOrElse { throwable ->
                HomeViewState.initial(selectedNetworkFilter, selectedPeriod).copy(
                    isLoading = false,
                    permissionStatus = if (usageAccessManager.hasUsageAccess()) {
                        PermissionStatus.Granted
                    } else {
                        PermissionStatus.Missing
                    },
                    dataFreshness = DataFreshness.Error(
                        throwable.message ?: "Datameter could not read usage yet.",
                    ),
                    primaryInsight = "Datameter could not read usage yet.",
                )
            }
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(application) as T
                }
            }
        }
    }
}
