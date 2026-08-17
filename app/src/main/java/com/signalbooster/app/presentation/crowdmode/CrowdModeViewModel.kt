package com.signalbooster.app.presentation.crowdmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signalbooster.app.domain.interfaces.CellularMetrics
import com.signalbooster.app.domain.interfaces.RadioTelemetrySource
import com.signalbooster.app.domain.interfaces.WifiMetrics
import com.signalbooster.app.domain.models.BandSteeringAdvice
import com.signalbooster.app.domain.models.CongestionState
import com.signalbooster.app.domain.models.ConfidenceLevel
import com.signalbooster.app.domain.models.SettingsDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CrowdModeViewModel @Inject constructor(
    private val radioTelemetrySource: RadioTelemetrySource
) : ViewModel() {

    private val _uiState = MutableStateFlow(CrowdModeUiState())
    val uiState: StateFlow<CrowdModeUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<CrowdModeEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    init {
        loadCrowdAnalysis()
    }

    fun loadCrowdAnalysis() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            combine(
                radioTelemetrySource.wifiMetrics,
                radioTelemetrySource.cellularMetrics
            ) { wifi, cellular ->
                analyze(wifi, cellular)
            }.collect { state ->
                _uiState.value = state.copy(isLoading = false)
            }
        }
    }

    fun openRatSettings() {
        _effects.tryEmit(CrowdModeEffect.OpenSettings(SettingsDestination.NETWORK_OPERATOR))
    }

    fun openWirelessSettings() {
        _effects.tryEmit(CrowdModeEffect.OpenSettings(SettingsDestination.WIRELESS))
    }

    private fun analyze(wifi: WifiMetrics, cellular: CellularMetrics): CrowdModeUiState {
        val observations = buildList {
            if (wifi.frequency != null || wifi.channel != null || wifi.rssi != null) {
                add(
                    BandObservation(
                        bandName = wifi.frequency?.let { frequency ->
                            if (frequency > 5000) "5 GHz Wi-Fi" else "2.4 GHz Wi-Fi"
                        } ?: "Wi-Fi frequency unavailable",
                        frequencyMhz = wifi.frequency,
                        channel = wifi.channel,
                        signalRssi = wifi.rssi,
                        congestionRisk = "UNKNOWN (QoE probe required)",
                        recommendation = "Run a bounded quality probe before choosing a connection."
                    )
                )
            }

            val technology = cellular.displayNetworkType ?: cellular.technology
            val rsrp = cellular.ssRsrp ?: cellular.rsrp
            val sinr = cellular.ssSinr ?: cellular.rssnr
            val rsrq = cellular.ssRsrq ?: cellular.rsrq
            val hasSignalEvidence = rsrp != null || sinr != null || rsrq != null
            val hasCellularObservation = technology != null || hasSignalEvidence ||
                cellular.bands.isNotEmpty() || cellular.earfcn != null || cellular.nrarfcn != null

            if (hasCellularObservation) {
                val isCongested = cellular.hasCongestionEvidence && cellular.isCongested
                val risk = when {
                    isCongested -> "HIGH (Measured signal-quality congestion indicators)"
                    hasSignalEvidence && rsrp != null && rsrp < -110 -> "MEDIUM (Weak observed coverage)"
                    hasSignalEvidence -> "UNKNOWN (Congestion not established by QoE)"
                    else -> "UNKNOWN (Insufficient signal data)"
                }
                val channelOrPci = cellular.pci ?: cellular.earfcn ?: cellular.nrarfcn
                val bandDetails = cellular.bands.takeIf { it.isNotEmpty() }
                    ?.joinToString(", ", prefix = " bands=")
                val cqiDetails = cellular.cqi?.let { " CQI=$it" } ?: ""

                add(
                    BandObservation(
                        bandName = (technology ?: "Cellular telemetry") + (bandDetails ?: "") + cqiDetails,
                        frequencyMhz = null,
                        channel = channelOrPci,
                        signalRssi = rsrp,
                        congestionRisk = risk,
                        recommendation = if (isCongested) {
                            "Use Android operator Settings to let the user review an alternative RAT."
                        } else {
                            "Collect QoE measurements before recommending a RAT change."
                        }
                    )
                )
            }
        }

        val technology = cellular.displayNetworkType ?: cellular.technology
        val rsrp = cellular.ssRsrp ?: cellular.rsrp
        val sinr = cellular.ssSinr ?: cellular.rssnr
        val rsrq = cellular.ssRsrq ?: cellular.rsrq
        val isCongested = cellular.hasCongestionEvidence && cellular.isCongested

        val advice = if (technology?.contains("5G", ignoreCase = true) == true && isCongested) {
            BandSteeringAdvice(
                currentRat = technology,
                recommendedRat = "Review 4G/LTE in Android Settings",
                targetBand = null,
                reason = "Observed 5G quality indicators suggest congestion" +
                    listOfNotNull(
                        sinr?.let { "SINR ${it} dB" },
                        rsrq?.let { "RSRQ ${it} dB" },
                        rsrp?.let { "RSRP ${it} dBm" }
                    ).joinToString(prefix = " (", postfix = ")") +
                    ". The app cannot force a RAT or band.",
                confidence = ConfidenceLevel.MEDIUM
            )
        } else {
            null
        }

        val recommendations = when {
            advice != null -> listOf(
                CrowdRecommendation(
                    title = "Measured cellular degradation",
                    action = "Review the preferred network in Android Settings",
                    confidence = advice.confidence,
                    evidence = advice.reason
                )
            )
            observations.isEmpty() -> listOf(
                CrowdRecommendation(
                    title = "Insufficient radio observations",
                    action = "Enable an available Wi-Fi or cellular connection",
                    confidence = ConfidenceLevel.LOW,
                    evidence = "No current radio telemetry was exposed by Android."
                )
            )
            else -> listOf(
                CrowdRecommendation(
                    title = "No RAT change established",
                    action = "Run a bounded quality probe before changing networks",
                    confidence = ConfidenceLevel.LOW,
                    evidence = "Radio observations alone do not prove congestion or a better alternative."
                )
            )
        }

        return CrowdModeUiState(
            bandObservations = observations,
            recommendations = recommendations,
            bandSteeringAdvice = advice,
            congestionState = when {
                advice != null -> CongestionState.SPECTRUM_CONGESTION
                cellular.hasCongestionEvidence && cellular.isCongested -> CongestionState.SPECTRUM_CONGESTION
                rsrp != null && rsrp < -110 -> CongestionState.WEAK_COVERAGE
                observations.isEmpty() -> CongestionState.UNKNOWN
                else -> CongestionState.UNKNOWN
            },
            totalBandsAnalyzed = observations.size
        )
    }
}

sealed interface CrowdModeEffect {
    data class OpenSettings(val destination: SettingsDestination) : CrowdModeEffect
}

data class CrowdModeUiState(
    val isLoading: Boolean = false,
    val totalBandsAnalyzed: Int = 0,
    val bandObservations: List<BandObservation> = emptyList(),
    val recommendations: List<CrowdRecommendation> = emptyList(),
    val bandSteeringAdvice: BandSteeringAdvice? = null,
    val congestionState: CongestionState = CongestionState.UNKNOWN
)

data class BandObservation(
    val bandName: String,
    val frequencyMhz: Int?,
    val channel: Int?,
    val signalRssi: Int?,
    val congestionRisk: String,
    val recommendation: String
)

data class CrowdRecommendation(
    val title: String,
    val action: String,
    val confidence: ConfidenceLevel,
    val evidence: String
)
