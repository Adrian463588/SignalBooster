package com.signalbooster.app.privacy

import com.signalbooster.app.domain.interfaces.CellularMetrics
import com.signalbooster.app.domain.interfaces.InterferenceClassifier
import com.signalbooster.app.domain.interfaces.WifiMetrics
import com.signalbooster.app.domain.models.ConfidenceLevel
import com.signalbooster.app.domain.models.InterferenceConfidence
import com.signalbooster.app.domain.models.InterferenceObservation
import com.signalbooster.app.domain.models.InterferenceTier
import com.signalbooster.app.domain.models.SignalType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Local-only, confidence-based interference observation classifier per PRD FR-08.
 * Fuses multi-signal anomalies, baseline deviation, abruptness, and cross-radio contrast.
 * Never asserts certainty.
 */
@Singleton
class LocalInterferenceClassifier @Inject constructor() : InterferenceClassifier {

    private val _interferenceConfidence = MutableStateFlow(InterferenceConfidence.NORMAL)
    override val interferenceConfidence: Flow<InterferenceConfidence> = _interferenceConfidence.asStateFlow()

    private val rsrpHistory = mutableListOf<Float>()
    private val rsrqHistory = mutableListOf<Float>()
    private val wifiRssiHistory = mutableListOf<Float>()

    private var lastRsrp: Float? = null
    private var lastWifiRssi: Float? = null
    private var lastTimestamp: Long = System.currentTimeMillis()

    override suspend fun classifyInterference(
        cellularMetrics: CellularMetrics?,
        wifiMetrics: WifiMetrics?
    ): InterferenceConfidence = withContext(Dispatchers.Default) {
        val observations = mutableListOf<InterferenceObservation>()
        val now = System.currentTimeMillis()
        val timeDiffSeconds = ((now - lastTimestamp) / 1000L).coerceAtLeast(1L)

        // 1. Cellular RSRP analysis
        cellularMetrics?.rsrp?.let { rsrpVal ->
            val currentRsrp = rsrpVal.toFloat()
            if (rsrpHistory.size >= 5) {
                val mean = rsrpHistory.average().toFloat()
                val variance = rsrpHistory.map { (it - mean) * (it - mean) }.average().toFloat()
                val stdDev = sqrt(variance).coerceAtLeast(1.0f)
                val deviation = (currentRsrp - mean) / stdDev

                val isAbrupt = lastRsrp?.let { prev ->
                    (prev - currentRsrp) > 25.0f && timeDiffSeconds <= 10L
                } ?: false

                if (deviation < -2.5f || isAbrupt) {
                    observations.add(
                        InterferenceObservation(
                            signal = SignalType.RSRP,
                            value = currentRsrp,
                            baseline = mean,
                            deviation = deviation,
                            isAbrupt = isAbrupt
                        )
                    )
                }
            }
            lastRsrp = currentRsrp
        }

        // 2. Wi-Fi RSSI analysis
        wifiMetrics?.rssi?.let { rssiVal ->
            val currentRssi = rssiVal.toFloat()
            if (wifiRssiHistory.size >= 5) {
                val mean = wifiRssiHistory.average().toFloat()
                val variance = wifiRssiHistory.map { (it - mean) * (it - mean) }.average().toFloat()
                val stdDev = sqrt(variance).coerceAtLeast(1.0f)
                val deviation = (currentRssi - mean) / stdDev

                val isAbrupt = lastWifiRssi?.let { prev ->
                    (prev - currentRssi) > 20.0f && timeDiffSeconds <= 10L
                } ?: false

                if (deviation < -2.5f || isAbrupt) {
                    observations.add(
                        InterferenceObservation(
                            signal = SignalType.WIFI_RSSI,
                            value = currentRssi,
                            baseline = mean,
                            deviation = deviation,
                            isAbrupt = isAbrupt
                        )
                    )
                }
            }
            lastWifiRssi = currentRssi
        }

        lastTimestamp = now

        // 3. Determine tier and confidence
        val abruptCount = observations.count { it.isAbrupt }
        val severeDeviationCount = observations.count { it.deviation < -3.5f }

        val (tier, confidence, reason) = when {
            abruptCount >= 2 || (abruptCount >= 1 && severeDeviationCount >= 2) -> {
                Triple(
                    InterferenceTier.LIKELY_LOCALIZED_INTERFERENCE,
                    ConfidenceLevel.HIGH,
                    "Simultaneous multi-signal rapid drop observed across radio paths."
                )
            }
            abruptCount >= 1 || severeDeviationCount >= 1 -> {
                Triple(
                    InterferenceTier.POSSIBLE_LOCALIZED_INTERFERENCE,
                    ConfidenceLevel.MEDIUM,
                    "Localized signal deviation or rapid RF attenuation observed."
                )
            }
            else -> {
                Triple(
                    InterferenceTier.NORMAL_OR_UNKNOWN,
                    ConfidenceLevel.LOW,
                    "No anomalous radio pattern detected."
                )
            }
        }

        val result = InterferenceConfidence(
            tier = tier,
            reason = reason,
            observations = observations,
            peerCount = 0,
            confidence = confidence,
            timestamp = Instant.now(),
            limitation = "Passive heuristics only. Physical obstruction, moving indoors, or tower congestion may cause similar RF variations."
        )

        _interferenceConfidence.value = result
        result
    }

    override suspend fun updateBaseline(cellularMetrics: CellularMetrics?, wifiMetrics: WifiMetrics?): Unit = withContext(Dispatchers.Default) {
        cellularMetrics?.rsrp?.let {
            rsrpHistory.add(it.toFloat())
            if (rsrpHistory.size > 50) rsrpHistory.removeAt(0)
        }
        cellularMetrics?.rsrq?.let {
            rsrqHistory.add(it.toFloat())
            if (rsrqHistory.size > 50) rsrqHistory.removeAt(0)
        }
        wifiMetrics?.rssi?.let {
            wifiRssiHistory.add(it.toFloat())
            if (wifiRssiHistory.size > 50) wifiRssiHistory.removeAt(0)
        }
        Unit
    }

    override suspend fun clearBaselines(): Unit = withContext(Dispatchers.Default) {
        rsrpHistory.clear()
        rsrqHistory.clear()
        wifiRssiHistory.clear()
        lastRsrp = null
        lastWifiRssi = null
        _interferenceConfidence.value = InterferenceConfidence.NORMAL
    }
}

