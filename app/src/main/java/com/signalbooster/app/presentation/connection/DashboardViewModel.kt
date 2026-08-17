package com.signalbooster.app.presentation.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signalbooster.app.domain.interfaces.NetworkMonitor
import com.signalbooster.app.domain.interfaces.ProbeType
import com.signalbooster.app.domain.interfaces.QualityProbe
import com.signalbooster.app.domain.interfaces.RecoveryCoordinator
import com.signalbooster.app.domain.interfaces.SettingsRepository
import com.signalbooster.app.domain.models.CapabilityState
import com.signalbooster.app.domain.models.NetworkRecommendation
import com.signalbooster.app.domain.models.NetworkSnapshot
import com.signalbooster.app.domain.models.QualityMetrics
import com.signalbooster.app.domain.models.RecoveryState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val networkMonitor: NetworkMonitor,
    private val qualityProbe: QualityProbe,
    private val recoveryCoordinator: RecoveryCoordinator,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var adaptiveMonitoringJob: Job? = null

    init {
        // Collect network snapshot changes
        viewModelScope.launch {
            networkMonitor.networkSnapshot.collect { snapshot ->
                _uiState.update { current ->
                    current.copy(
                        networkSnapshot = snapshot,
                        isLoading = false,
                        isMonitoring = networkMonitor.isMonitoring()
                    )
                }
                refreshRecommendation(snapshot)
            }
        }

        // Collect recovery coordinator state machine
        viewModelScope.launch {
            recoveryCoordinator.recoveryState.collect { recState ->
                _uiState.update { it.copy(recoveryState = recState) }
            }
        }

        startMonitoring()
    }

    fun startMonitoring() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = networkMonitor.startMonitoring()
            val isSuccess = result == CapabilityState.RUNNING || result == CapabilityState.AVAILABLE
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isMonitoring = isSuccess,
                    monitoringCapability = result
                )
            }

            if (isSuccess) {
                startAdaptiveProbes()
            }
        }
    }

    fun stopMonitoring() {
        viewModelScope.launch {
            adaptiveMonitoringJob?.cancel()
            adaptiveMonitoringJob = null
            networkMonitor.stopMonitoring()
            _uiState.update { it.copy(isMonitoring = false) }
        }
    }

    fun runQualityProbe(probeType: ProbeType = ProbeType.HTTP) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProbing = true) }
            try {
                val timeout = settingsRepository.probeTimeoutMs.first()
                val budget = settingsRepository.probeByteBudget.first()
                qualityProbe.startProbe(probeType, timeout, budget).collect { metrics ->
                    _uiState.update { it.copy(qualityMetrics = metrics) }
                    refreshRecommendation(_uiState.value.networkSnapshot, metrics)
                }
            } finally {
                _uiState.update { it.copy(isProbing = false) }
            }
        }
    }

    fun flushDnsAndSockets() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRecovering = true) }
            val success = recoveryCoordinator.invalidateDnsAndSockets()
            _uiState.update {
                it.copy(
                    isRecovering = false,
                    lastRecoveryMessage = if (success) "DNS caches refreshed & sockets reset" else "DNS flush failed"
                )
            }
            runQualityProbe(ProbeType.DNS)
        }
    }

    private fun startAdaptiveProbes() {
        adaptiveMonitoringJob?.cancel()
        adaptiveMonitoringJob = viewModelScope.launch {
            settingsRepository.isAdaptiveMonitoringEnabled.collect { isAdaptiveEnabled ->
                if (!isAdaptiveEnabled) return@collect
                var currentBackoffMs = 1000L
                val maxBackoffMs = 30000L

                while (isActive && networkMonitor.isMonitoring()) {
                    val currentMetrics = _uiState.value.qualityMetrics
                    val isDegraded = (currentMetrics.latencyRttMs ?: 0) > 200 || (currentMetrics.lossRatio ?: 0f) > 0.05f

                    val intervalMs = if (isDegraded) {
                        // Exponential backoff during degradation: 1s -> 2s -> 4s -> 8s -> 15s -> 30s
                        val next = currentBackoffMs
                        currentBackoffMs = (currentBackoffMs * 2).coerceAtMost(maxBackoffMs)
                        next
                    } else {
                        currentBackoffMs = 1000L
                        maxBackoffMs
                    }

                    delay(intervalMs)
                    if (networkMonitor.isMonitoring()) {
                        val timeout = settingsRepository.probeTimeoutMs.first().coerceAtMost(5000L)
                        val budget = settingsRepository.probeByteBudget.first()
                        qualityProbe.startProbe(ProbeType.HTTP, timeout, budget).collect { metrics ->
                            _uiState.update { it.copy(qualityMetrics = metrics) }
                            refreshRecommendation(_uiState.value.networkSnapshot, metrics)
                        }
                    }
                }
            }
        }
    }

    fun attemptRecovery() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRecovering = true) }
            val currentSnapshot = _uiState.value.networkSnapshot
            val result = recoveryCoordinator.attemptRecovery(currentSnapshot)
            _uiState.update {
                it.copy(
                    isRecovering = false,
                    lastRecoveryMessage = result.actionTaken + (result.details?.let { d -> ": $d" } ?: "")
                )
            }
            refreshRecommendation(_uiState.value.networkSnapshot)
        }
    }

    private suspend fun refreshRecommendation(snapshot: NetworkSnapshot, metrics: QualityMetrics? = _uiState.value.qualityMetrics) {
        val rec = recoveryCoordinator.getRecommendation(snapshot, metrics)
        _uiState.update { it.copy(currentRecommendation = rec) }
    }
}

data class DashboardUiState(
    val networkSnapshot: NetworkSnapshot = NetworkSnapshot.EMPTY,
    val qualityMetrics: QualityMetrics = QualityMetrics(),
    val isMonitoring: Boolean = false,
    val isProbing: Boolean = false,
    val isRecovering: Boolean = false,
    val isLoading: Boolean = true,
    val monitoringCapability: CapabilityState = CapabilityState.INSUFFICIENT_DATA,
    val currentRecommendation: NetworkRecommendation? = null,
    val lastRecoveryMessage: String? = null,
    val recoveryState: com.signalbooster.app.domain.models.RecoveryState = com.signalbooster.app.domain.models.RecoveryState.HEALTHY
)