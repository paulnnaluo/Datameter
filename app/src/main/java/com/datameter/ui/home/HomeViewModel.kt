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
import com.datameter.domain.model.PeriodSummary
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
        val networkFilter = selectedNetworkFilter
        val period = selectedPeriod
        val previousState = _uiState.value
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedNetworkFilter = networkFilter,
                selectedPeriod = period,
                isLoading = true,
                dataFreshness = DataFreshness.Loading,
            )

            val primaryState = runCatching {
                repository.loadPrimaryHomeState(networkFilter, period)
            }.getOrElse { throwable ->
                HomeViewState.initial(networkFilter, period).copy(
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

            if (networkFilter != selectedNetworkFilter || period != selectedPeriod) return@launch
            if (
                primaryState.permissionStatus != PermissionStatus.Granted ||
                primaryState.dataFreshness is DataFreshness.Error
            ) {
                _uiState.value = primaryState
                return@launch
            }

            _uiState.value = primaryState.copy(
                periodSummaries = primaryState.periodSummaries.withCarriedOverTotals(
                    previousSummaries = previousState.periodSummaries.takeIf {
                        previousState.selectedNetworkFilter == networkFilter
                    }.orEmpty(),
                    selectedPeriod = period,
                    selectedPeriodTotalBytes = primaryState.totalBytes,
                ),
                isLoading = true,
                dataFreshness = DataFreshness.Loading,
            )

            val summaries = runCatching {
                repository.loadPeriodSummaries(
                    networkFilter = networkFilter,
                    selectedPeriod = period,
                    selectedPeriodTotalBytes = primaryState.totalBytes,
                )
            }.getOrElse { throwable ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    dataFreshness = DataFreshness.Error(
                        throwable.message ?: "Datameter could not update summaries yet.",
                    ),
                )
                return@launch
            }

            if (networkFilter != selectedNetworkFilter || period != selectedPeriod) return@launch
            _uiState.value = _uiState.value.copy(
                periodSummaries = summaries,
                isLoading = false,
                dataFreshness = primaryState.dataFreshness,
            )
        }
    }

    private fun List<PeriodSummary>.withCarriedOverTotals(
        previousSummaries: List<PeriodSummary>,
        selectedPeriod: UsagePeriod,
        selectedPeriodTotalBytes: Long,
    ): List<PeriodSummary> {
        val previousByPeriod = previousSummaries.associateBy { it.period }
        return map { summary ->
            when {
                summary.period == selectedPeriod -> summary.copy(totalBytes = selectedPeriodTotalBytes)
                previousByPeriod.containsKey(summary.period) -> previousByPeriod.getValue(summary.period)
                else -> summary
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
