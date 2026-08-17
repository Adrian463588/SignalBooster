package com.signalbooster.app.domain.models

import java.time.Instant

/**
 * Domain contract for network state snapshot.
 * Framework-independent representation of network state.
 */
data class NetworkSnapshot(
    val transport: Transport,
    val validation: NetworkValidation,
    val isMetered: Boolean,
    val isCaptivePortal: Boolean,
    val isVpnActive: Boolean,
    val identifiers: NetworkIdentifiers? = null,
    val gatewayAddress: String? = null,
    val dnsServers: List<String> = emptyList(),
    val interfaceName: String? = null,
    val mtu: Int? = null,
    val isGatewayReachable: Boolean? = null,
    val timestamp: Instant = Instant.now(),
    val availability: DataAvailability = DataAvailability.INSUFFICIENT_DATA
) {
    companion object {
        val EMPTY = NetworkSnapshot(
            transport = Transport.UNKNOWN,
            validation = NetworkValidation.UNKNOWN,
            isMetered = false,
            isCaptivePortal = false,
            isVpnActive = false
        )
    }
}

data class NetworkIdentifiers(
    val ssid: String? = null,
    val bssid: String? = null, // Redacted in UI for privacy
    val operatorName: String? = null,
    val operatorId: String? = null, // MCC-MNC
    val frequency: Int? = null,
    val channel: Int? = null
)

/**
 * Inferred spectrum and congestion state per Docs1.md & Docs2.md.
 */
enum class CongestionState {
    NOMINAL,
    WEAK_COVERAGE,
    SPECTRUM_CONGESTION,
    BUFFERBLOAT_DETECTED,
    UNKNOWN
}

/**
 * 4G vs 5G Band Steering & RAT transition recommendation per Docs1.md Section 11.
 */
data class BandSteeringAdvice(
    val currentRat: String,
    val recommendedRat: String,
    val targetBand: String? = null,
    val reason: String,
    val confidence: ConfidenceLevel,
    val timestamp: Instant = Instant.now()
)

/**
 * Multi-stage recovery state machine states per Docs1.md lines 598-642.
 */
enum class RecoveryState {
    HEALTHY,
    DEGRADED,
    VERIFYING,
    RECOVERING,
    VALIDATING
}

/**
 * Network transport types.
 */
enum class Transport {
    WIFI,
    CELLULAR,
    ETHERNET,
    VPN,
    UNKNOWN
}

/**
 * Network validation states.
 */
enum class NetworkValidation {
    VALIDATED,      // Internet is reachable and validated
    CAPTIVE_PORTAL, // Captive portal login page detected
    UNVALIDATED,    // Connected but not yet validated
    FAILED,         // Validation failed (no internet access)
    UNKNOWN
}

/**
 * Data availability states per AGENTS.md section 6.
 */
enum class DataAvailability {
    AVAILABLE,
    UNAVAILABLE,
    PERMISSION_REQUIRED,
    CAPABILITY_UNAVAILABLE,
    INSUFFICIENT_DATA,
    RUNNING,
    STOPPED,
    FAILED
}

/**
 * Quality metrics for network performance measurement.
 */
data class QualityMetrics(
    val latencyRttMs: Int? = null,
    val jitterMs: Int? = null,
    val lossRatio: Float? = null, // 0.0 to 1.0
    val throughputMbps: Float? = null,
    val signalStrengthDbm: Int? = null,
    val signalQuality: SignalQuality? = null,
    val probeScope: ProbeScope = ProbeScope.UNKNOWN,
    val measurementConfidence: MeasurementConfidence = MeasurementConfidence.LOW,
    val timestamp: Instant = Instant.now()
) {
    /**
     * Calculates a composite Quality of Experience score (0 - 100).
     */
    fun calculateQoEScore(): Int {
        var score = 70.0
        
        latencyRttMs?.let { latency ->
            score += when {
                latency < 50 -> 15.0
                latency < 100 -> 10.0
                latency < 200 -> 0.0
                latency < 400 -> -15.0
                else -> -30.0
            }
        }
        
        jitterMs?.let { jitter ->
            score += when {
                jitter < 10 -> 5.0
                jitter < 30 -> 0.0
                jitter < 60 -> -5.0
                else -> -10.0
            }
        }
        
        lossRatio?.let { loss ->
            score += when {
                loss <= 0.01f -> 10.0
                loss <= 0.05f -> 0.0
                loss <= 0.15f -> -15.0
                else -> -30.0
            }
        }
        
        throughputMbps?.let { speed ->
            score += when {
                speed >= 25.0f -> 10.0
                speed >= 10.0f -> 5.0
                speed >= 2.0f -> 0.0
                else -> -10.0
            }
        }
        
        return score.toInt().coerceIn(0, 100)
    }
}

enum class SignalQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    NONE
}

enum class ProbeScope {
    DNS,
    TCP,
    TLS,
    HTTP,
    THROUGHPUT,
    GATEWAY,
    UNKNOWN
}

enum class MeasurementConfidence {
    HIGH,
    MEDIUM,
    LOW
}

/**
 * Network recommendation per FR-04.
 */
data class NetworkRecommendation(
    val action: NetworkAction,
    val evidence: List<RecommendationEvidence>,
    val confidence: ConfidenceLevel,
    val limitation: String? = null,
    val timestamp: Instant = Instant.now()
)

enum class NetworkAction {
    STAY,
    TRY_ALTERNATIVE,
    OPEN_SETTINGS,
    RETRY_CONNECTION,
    SWITCH_TO_WIFI,
    SWITCH_TO_CELLULAR
}

data class RecommendationEvidence(
    val metric: String,
    val value: String,
    val impact: EvidenceImpact
)

enum class EvidenceImpact {
    POSITIVE,
    NEGATIVE,
    NEUTRAL
}

enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW
}