package com.signalbooster.app.presentation.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signalbooster.app.domain.interfaces.CellularMetrics
import com.signalbooster.app.domain.interfaces.ProbeType
import com.signalbooster.app.domain.interfaces.QualityProbe
import com.signalbooster.app.domain.interfaces.RadioTelemetrySource
import com.signalbooster.app.domain.interfaces.SettingsRepository
import com.signalbooster.app.domain.interfaces.WifiMetrics
import com.signalbooster.app.domain.models.QualityMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val radioTelemetrySource: RadioTelemetrySource,
    private val qualityProbe: QualityProbe,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            radioTelemetrySource.cellularMetrics.collect { cellular ->
                _uiState.update { it.copy(cellularMetrics = cellular) }
            }
        }
        viewModelScope.launch {
            radioTelemetrySource.wifiMetrics.collect { wifi ->
                _uiState.update { it.copy(wifiMetrics = wifi) }
            }
        }
        startTelemetry()
    }

    fun startTelemetry() {
        viewModelScope.launch {
            radioTelemetrySource.startCollection()
            _uiState.update { it.copy(isCollectingTelemetry = true) }
        }
    }

    fun stopTelemetry() {
        viewModelScope.launch {
            radioTelemetrySource.stopCollection()
            _uiState.update { it.copy(isCollectingTelemetry = false) }
        }
    }

    fun runProbe(probeType: ProbeType) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProbing = true, lastProbeType = probeType) }
            try {
                val timeout = settingsRepository.probeTimeoutMs.first()
                val budget = settingsRepository.probeByteBudget.first()
                qualityProbe.startProbe(probeType, timeout, budget).collect { metrics ->
                    _uiState.update { it.copy(qualityMetrics = metrics) }
                }
            } finally {
                _uiState.update { it.copy(isProbing = false) }
            }
        }
    }

    fun clearMetrics() {
        _uiState.update {
            it.copy(qualityMetrics = QualityMetrics(), lastProbeType = null)
        }
    }
}

data class DiagnosticsUiState(
    val cellularMetrics: CellularMetrics = CellularMetrics(),
    val wifiMetrics: WifiMetrics = WifiMetrics(),
    val qualityMetrics: QualityMetrics = QualityMetrics(),
    val isCollectingTelemetry: Boolean = false,
    val isProbing: Boolean = false,
    val lastProbeType: ProbeType? = null
)