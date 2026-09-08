package com.datameter.ui.audit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.datameter.core.DatameterServiceLocator
import com.datameter.data.audit.AuditRepository
import com.datameter.data.audit.AuditSession
import com.datameter.data.usage.NetworkUsageDataSource
import com.datameter.data.usage.UsageAccessManager
import com.datameter.domain.BalanceInputParser
import com.datameter.domain.model.AuditAssessment
import com.datameter.domain.model.AuditMath
import com.datameter.domain.model.DateRange
import com.datameter.domain.model.NetworkFilter
import com.datameter.domain.model.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CompletedAuditResult(
    val networkName: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val startingBalanceBytes: Long,
    val endingBalanceBytes: Long,
    val measuredBytes: Long,
    val deductedBytes: Long,
    val differenceBytes: Long,
    val assessment: AuditAssessment,
)

data class AuditUiState(
    val permissionStatus: PermissionStatus = PermissionStatus.Unknown,
    val networkNameInput: String = "MTN",
    val startingBalanceInput: String = "",
    val currentBalanceInput: String = "",
    val activeSession: AuditSession? = null,
    val completedAudit: CompletedAuditResult? = null,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
)

class AuditViewModel(application: Application) : AndroidViewModel(application) {
    private val usageAccessManager: UsageAccessManager =
        DatameterServiceLocator.usageAccessManager(application)
    private val usageDataSource: NetworkUsageDataSource =
        DatameterServiceLocator.networkUsageDataSource(application)
    private val auditRepository: AuditRepository =
        DatameterServiceLocator.auditRepository(application)

    private val _uiState = MutableStateFlow(AuditUiState())
    val uiState: StateFlow<AuditUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun setNetworkName(value: String) {
        _uiState.value = _uiState.value.copy(networkNameInput = value)
    }

    fun setStartingBalance(value: String) {
        _uiState.value = _uiState.value.copy(startingBalanceInput = value, errorMessage = null)
    }

    fun setCurrentBalance(value: String) {
        _uiState.value = _uiState.value.copy(currentBalanceInput = value, errorMessage = null)
    }

    fun refresh() {
        viewModelScope.launch {
            loadState()
        }
    }

    fun startAudit() {
        viewModelScope.launch {
            if (!usageAccessManager.hasUsageAccess()) {
                _uiState.value = _uiState.value.copy(
                    permissionStatus = PermissionStatus.Missing,
                    errorMessage = "Usage Access is required before starting an audit.",
                )
                return@launch
            }

            val networkName = _uiState.value.networkNameInput.trim()
            if (networkName.isBlank()) {
                _uiState.value = _uiState.value.copy(errorMessage = "Enter the network name.")
                return@launch
            }

            val startingBalance = BalanceInputParser.parseGigabytes(
                _uiState.value.startingBalanceInput,
            )
            if (startingBalance == null) {
                _uiState.value = _uiState.value.copy(errorMessage = "Enter the starting balance in GB.")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isBusy = true, errorMessage = null)
            val now = System.currentTimeMillis()
            val startingMeter = runCatching {
                usageDataSource.query(NetworkFilter.Mobile, DateRange(0L, now)).total.totalBytes
            }.getOrDefault(0L)

            auditRepository.startSession(
                networkName = networkName,
                startingBalanceBytes = startingBalance,
                startingDeviceMeterBytes = startingMeter,
                startedAtMillis = now,
            )
            _uiState.value = _uiState.value.copy(
                startingBalanceInput = "",
                currentBalanceInput = "",
                completedAudit = null,
            )
            loadState()
        }
    }

    fun endAudit() {
        viewModelScope.launch {
            val session = auditRepository.currentSession()
            if (session == null) {
                _uiState.value = _uiState.value.copy(errorMessage = "Start an audit first.")
                return@launch
            }

            val enteredBalance = _uiState.value.currentBalanceInput.trim()
            val typedEndingBalance = if (enteredBalance.isBlank()) {
                null
            } else {
                BalanceInputParser.parseGigabytes(enteredBalance)
            }
            if (enteredBalance.isNotBlank() && typedEndingBalance == null) {
                _uiState.value = _uiState.value.copy(errorMessage = "Enter the ending data balance in GB.")
                return@launch
            }

            val endingBalance = typedEndingBalance ?: session.lastBalanceBytes
            if (endingBalance == null) {
                _uiState.value = _uiState.value.copy(errorMessage = "Enter the latest data balance before finishing.")
                return@launch
            }
            if (endingBalance > session.startingBalanceBytes) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Ending data balance is higher than the starting data balance.",
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(isBusy = true, errorMessage = null)
            val endedAtMillis = if (typedEndingBalance == null && session.lastCheckedAtMillis != null) {
                session.lastCheckedAtMillis
            } else {
                System.currentTimeMillis()
            }
            val completedAudit = buildCompletedAuditResult(
                session = session,
                endingBalanceBytes = endingBalance,
                endedAtMillis = endedAtMillis,
            )

            auditRepository.clearSession()
            _uiState.value = AuditUiState(
                permissionStatus = if (usageAccessManager.hasUsageAccess()) {
                    PermissionStatus.Granted
                } else {
                    PermissionStatus.Missing
                },
                networkNameInput = session.networkName,
                completedAudit = completedAudit,
            )
        }
    }

    fun clearCompletedAudit() {
        _uiState.value = _uiState.value.copy(
            completedAudit = null,
            currentBalanceInput = "",
            startingBalanceInput = "",
            errorMessage = null,
        )
    }

    private suspend fun loadState() {
        val hasPermission = usageAccessManager.hasUsageAccess()
        if (!hasPermission) {
            _uiState.value = _uiState.value.copy(
                permissionStatus = PermissionStatus.Missing,
                activeSession = auditRepository.currentSession(),
                isBusy = false,
            )
            return
        }

        val session = auditRepository.currentSession()
        if (session == null) {
            _uiState.value = _uiState.value.copy(
                permissionStatus = PermissionStatus.Granted,
                activeSession = null,
                isBusy = false,
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            permissionStatus = PermissionStatus.Granted,
            networkNameInput = session.networkName,
            activeSession = session,
            isBusy = false,
        )
    }

    private suspend fun buildCompletedAuditResult(
        session: AuditSession,
        endingBalanceBytes: Long,
        endedAtMillis: Long,
    ): CompletedAuditResult {
        val measuredBytes = runCatching {
            usageDataSource.query(
                NetworkFilter.Mobile,
                DateRange(session.startedAtMillis, endedAtMillis),
            ).total.totalBytes
        }.getOrDefault(0L)
        val deductedBytes = (session.startingBalanceBytes - endingBalanceBytes).coerceAtLeast(0L)
        val differenceBytes = deductedBytes - measuredBytes

        return CompletedAuditResult(
            networkName = session.networkName,
            startedAtMillis = session.startedAtMillis,
            endedAtMillis = endedAtMillis,
            startingBalanceBytes = session.startingBalanceBytes,
            endingBalanceBytes = endingBalanceBytes,
            measuredBytes = measuredBytes,
            deductedBytes = deductedBytes,
            differenceBytes = differenceBytes,
            assessment = AuditMath.assess(measuredBytes, deductedBytes),
        )
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuditViewModel(application) as T
                }
            }
        }
    }
}
