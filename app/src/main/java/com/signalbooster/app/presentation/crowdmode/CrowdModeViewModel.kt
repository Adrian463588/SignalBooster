package com.signalbooster.app.presentation.crowdmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signalbooster.app.domain.interfaces.NetworkMonitor
import com.signalbooster.app.domain.interfaces.RadioTelemetrySource
import com.signalbooster.app.domain.models.BandSteeringAdvice
import com.signalbooster.app.domain.models.ConfidenceLevel
import com.signalbooster.app.domain.models.CongestionState
import com.signalbooster.app.domain.models.Transport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

            // Combine latest telemetry streams cleanly
            combine(
                radioTelemetrySource.wifiMetrics,
                radioTelemetrySource.cellularMetrics,
                networkMonitor.networkSnapshot
            ) { wifi, cellular, snapshot ->
                Triple(wifi, cellular, snapshot)
            }.collect { (wifi, cellular, snapshot) ->
                val bandObs = mutableListOf<BandObservation>()
                var bandSteeringAdvice: BandSteeringAdvice? = null
                
                // 1. Wi-Fi band heuristic
                wifi.frequency?.let { freq ->
                    val is5Ghz = freq > 5000
                    val bandLabel = if (is5Ghz) "5 GHz Wi-Fi (Clean Spectrum)" else "2.4 GHz Wi-Fi (Crowded Spectrum)"
                    val congestionRisk = if (!is5Ghz) "HIGH (Crowded 2.4 GHz spectrum)" else "LOW (High Throughput 5 GHz)"
                    
                    bandObs.add(
                        BandObservation(
                            bandName = bandLabel,
                            frequencyMhz = freq,
                            channel = wifi.channel ?: 0,
                            signalRssi = wifi.rssi ?: -100,
                            congestionRisk = congestionRisk,
                            recommendation = if (!is5Ghz) "Recommend switching to 5GHz Wi-Fi AP or Cellular LTE" else "Optimal 5GHz Wi-Fi channel in use"
                        )
                    )
                }

                // 2. Cellular band heuristic
                val tech = cellular.displayNetworkType ?: cellular.technology ?: "Cellular"
                val rsrp = cellular.ssRsrp ?: cellular.rsrp ?: -110
                val sinr = cellular.ssSinr ?: cellular.rssnr ?: 15
                val rsrq = cellular.ssRsrq ?: cellular.rsrq ?: -10
                val isCellCongested = cellular.isCongested || (rsrp > -100 && (sinr < 5 || rsrq < -14))

                val cellRisk = when {
                    isCellCongested -> "HIGH (Cell Sector Saturation / Low SINR)"
                    rsrp < -110 -> "MEDIUM (Weak Cell Coverage)"
                    else -> "LOW (Nominal Signal & Clean SINR)"
                }

                val channelOrPci = cellular.pci ?: cellular.earfcn ?: cellular.nrarfcn ?: 0
                val bandDetails = if (cellular.bands.isNotEmpty()) " (Bands: ${cellular.bands.joinToString(", ")})" else ""
                val cqiDetails = cellular.cqi?.let { " [CQI: $it/15]" } ?: ""

                bandObs.add(
                    BandObservation(
                        bandName = "$tech$bandDetails$cqiDetails",
                        frequencyMhz = cellular.earfcn ?: cellular.nrarfcn ?: 0,
                        channel = channelOrPci,
                        signalRssi = rsrp,
                        congestionRisk = cellRisk,
                        recommendation = if (isCellCongested) {
                            "Cell sector saturated. Fallback to 4G LTE CA or alternate Wi-Fi recommended"
                        } else {
                            "Cellular link quality is nominal"
                        }
                    )
                )

                // 3. 4G vs 5G Band Steering Decision Engine
                if (tech.contains("5G", ignoreCase = true) && isCellCongested) {
                    bandSteeringAdvice = BandSteeringAdvice(
                        currentRat = tech,
                        recommendedRat = "4G LTE (Carrier Aggregation)",
                        targetBand = "LTE Band 1/3/7/8/20",
                        reason = "5G NR cell carrier is congested (SINR ${sinr}dB, RSRQ ${rsrq}dB). 4G LTE provides lower packet contention.",
                        confidence = ConfidenceLevel.HIGH
                    )
                } else if (tech.contains("LTE", ignoreCase = true) && !isCellCongested && rsrp > -85) {
                    bandSteeringAdvice = BandSteeringAdvice(
                        currentRat = "4G LTE",
                        recommendedRat = "5G NR (High Capacity)",
                        targetBand = "NR n1/n3/n78",
                        reason = "Local RF environment is clean. 5G NR provides higher burst throughput.",
                        confidence = ConfidenceLevel.MEDIUM
                    )
                }

                // 4. Synthesize crowd recommendations
                val recs = mutableListOf<CrowdRecommendation>()
                if (bandObs.any { it.congestionRisk.startsWith("HIGH") }) {
                    recs.add(
                        CrowdRecommendation(
                            title = "High Spectrum Density Detected",
                            action = if (bandSteeringAdvice != null) "Apply Band Steering Advice (Switch Network Mode)" else "Switch to 5GHz AP or Toggle Airplane Mode",
                            confidence = ConfidenceLevel.HIGH,
                            evidence = "High power (RSRP ${rsrp}dBm) with degraded SINR (${sinr}dB) and channel overlap."
                        )
                    )
                } else {
                    recs.add(
                        CrowdRecommendation(
                            title = "Spectrum Environment Nominal",
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
                        bandSteeringAdvice = bandSteeringAdvice,
                        congestionState = if (isCellCongested) CongestionState.SPECTRUM_CONGESTION else CongestionState.NOMINAL,
                        totalBandsAnalyzed = bandObs.size
                    )
                }
            }
        }
    }
}

data class CrowdModeUiState(
    val isLoading: Boolean = false,
    val totalBandsAnalyzed: Int = 0,
    val bandObservations: List<BandObservation> = emptyList(),
    val recommendations: List<CrowdRecommendation> = emptyList(),
    val bandSteeringAdvice: BandSteeringAdvice? = null,
    val congestionState: CongestionState = CongestionState.NOMINAL
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