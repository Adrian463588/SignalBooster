package com.signalbooster.app.presentation.crowdmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signalbooster.app.domain.interfaces.NetworkMonitor
import com.signalbooster.app.domain.interfaces.RadioTelemetrySource
import com.signalbooster.app.domain.models.ConfidenceLevel
import com.signalbooster.app.domain.models.Transport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CrowdModeViewModel @Inject constructor(
    private val networkMonitor: NetworkMonitor,
    private val radioTelemetrySource: RadioTelemetrySource
) : ViewModel() {

    private val _uiState = MutableStateFlow(CrowdModeUiState())
    val uiState: StateFlow<CrowdModeUiState> = _uiState.asStateFlow()

    init {
        loadCrowdAnalysis()
    }

    fun loadCrowdAnalysis() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Collect latest telemetry
            radioTelemetrySource.wifiMetrics.collect { wifi ->
                radioTelemetrySource.cellularMetrics.collect { cellular ->
                    val bandObs = mutableListOf<BandObservation>()
                    
                    // 1. Wi-Fi band heuristic
                    wifi.frequency?.let { freq ->
                        val is5Ghz = freq > 5000
                        val bandLabel = if (is5Ghz) "5 GHz Wi-Fi" else "2.4 GHz Wi-Fi"
                        val congestionRisk = if (!is5Ghz) "HIGH (Crowded 2.4 GHz spectrum)" else "MEDIUM"
                        
                        bandObs.add(
                            BandObservation(
                                bandName = bandLabel,
                                frequencyMhz = freq,
                                channel = wifi.channel ?: 0,
                                signalRssi = wifi.rssi ?: -100,
                                congestionRisk = congestionRisk,
                                recommendation = if (!is5Ghz) "Consider switching to 5GHz Wi-Fi or Cellular LTE" else "Optimal Wi-Fi band"
                            )
                        )
                    }

                    // 2. Cellular band heuristic
                    cellular.technology?.let { tech ->
                        val rsrp = cellular.rsrp ?: -110
                        val sinr = cellular.rssnr ?: cellular.ssSinr ?: 0
                        val isCongested = rsrp > -85 && sinr < 5 // Strong power but low SINR -> interference/congestion

                        bandObs.add(
                            BandObservation(
                                bandName = "$tech Cellular",
                                frequencyMhz = 0,
                                channel = cellular.pci ?: 0,
                                signalRssi = rsrp,
                                congestionRisk = if (isCongested) "HIGH (Cell Sector Congestion / High Interference)" else "LOW",
                                recommendation = if (isCongested) "Consider Wi-Fi alternative if available" else "Cellular path is healthy"
                            )
                        )
                    }

                    // Synthesize crowd recommendations
                    val recs = mutableListOf<CrowdRecommendation>()
                    if (bandObs.any { it.congestionRisk.startsWith("HIGH") }) {
                        recs.add(
                            CrowdRecommendation(
                                title = "Spectrum Congestion Detected",
                                action = "Open Wi-Fi Settings & Select 5GHz AP or Toggle Airplane Mode",
                                confidence = ConfidenceLevel.MEDIUM,
                                evidence = "High signal strength with degraded SINR/channel saturation."
                            )
                        )
                    } else {
                        recs.add(
                            CrowdRecommendation(
                                title = "Environment Stable",
                                action = "Maintain Current Connection",
                                confidence = ConfidenceLevel.HIGH,
                                evidence = "No extreme multi-device saturation or channel overlap detected."
                            )
                        )
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            bandObservations = bandObs,
                            recommendations = recs,
                            totalBandsAnalyzed = bandObs.size
                        )
                    }
                }
            }
        }
    }
}

data class CrowdModeUiState(
    val isLoading: Boolean = false,
    val totalBandsAnalyzed: Int = 0,
    val bandObservations: List<BandObservation> = emptyList(),
    val recommendations: List<CrowdRecommendation> = emptyList()
)

data class BandObservation(
    val bandName: String,
    val frequencyMhz: Int,
    val channel: Int,
    val signalRssi: Int,
    val congestionRisk: String,
    val recommendation: String
)

data class CrowdRecommendation(
    val title: String,
    val action: String,
    val confidence: ConfidenceLevel,
    val evidence: String
)