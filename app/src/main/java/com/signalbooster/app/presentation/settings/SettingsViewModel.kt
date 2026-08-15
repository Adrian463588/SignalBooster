package com.signalbooster.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signalbooster.app.domain.interfaces.RadioTelemetrySource
import com.signalbooster.app.domain.interfaces.SettingsRepository
import com.signalbooster.app.domain.models.PrivacyPosture
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val radioTelemetrySource: RadioTelemetrySource
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.probeTimeoutMs.collect { timeout ->
                _uiState.update { it.copy(probeTimeoutMs = timeout) }
            }
        }
        viewModelScope.launch {
            settingsRepository.probeByteBudget.collect { budget ->
                _uiState.update { it.copy(probeByteBudget = budget) }
            }
        }
        viewModelScope.launch {
            settingsRepository.probeEndpoint.collect { endpoint ->
                _uiState.update { it.copy(probeEndpoint = endpoint) }
            }
        }
        viewModelScope.launch {
            settingsRepository.isAdaptiveMonitoringEnabled.collect { adaptive ->
                _uiState.update { it.copy(isAdaptiveMonitoring = adaptive) }
            }
        }
        viewModelScope.launch {
            radioTelemetrySource.privacyPosture.collect { posture ->
                _uiState.update { it.copy(privacyPosture = posture) }
            }
        }
    }

    fun setProbeTimeout(timeoutMs: Long) {
        viewModelScope.launch {
            settingsRepository.setProbeTimeoutMs(timeoutMs)
        }
    }

    fun setByteBudget(budgetBytes: Long) {
        viewModelScope.launch {
            settingsRepository.setProbeByteBudget(budgetBytes)
        }
    }

    fun setProbeEndpoint(endpoint: String) {
        viewModelScope.launch {
            settingsRepository.setProbeEndpoint(endpoint)
        }
    }

    fun toggleAdaptiveMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAdaptiveMonitoringEnabled(enabled)
        }
    }

    fun wipeAllLocalData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWipingData = true) }
            val success = settingsRepository.wipeLocalData()
            _uiState.update {
                it.copy(
                    isWipingData = false,
                    wipeMessage = if (success) "All local baselines and data successfully erased." else "Failed to erase data."
                )
            }
        }
    }

    fun clearWipeMessage() {
        _uiState.update { it.copy(wipeMessage = null) }
    }
}

data class SettingsUiState(
    val probeTimeoutMs: Long = 10000L,
    val probeByteBudget: Long = 1048576L,
    val probeEndpoint: String = "https://connectivitycheck.gstatic.com/generate_204",
    val isAdaptiveMonitoring: Boolean = true,
    val privacyPosture: PrivacyPosture = PrivacyPosture.DEFAULT,
    val isWipingData: Boolean = false,
    val wipeMessage: String? = null
)
