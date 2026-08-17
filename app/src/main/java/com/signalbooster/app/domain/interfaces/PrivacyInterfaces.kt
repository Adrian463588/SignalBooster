package com.signalbooster.app.domain.interfaces

import com.signalbooster.app.domain.models.AcousticMaskState
import com.signalbooster.app.domain.models.InterferenceConfidence
import com.signalbooster.app.domain.models.MaskingNoiseType
import com.signalbooster.app.domain.models.PrivacyPosture
import kotlinx.coroutines.flow.Flow

/**
 * Radio telemetry source interface.
 * Provides access to radio signal measurements.
 */
interface RadioTelemetrySource {
    /**
     * Current cellular signal measurements.
     */
    val cellularMetrics: Flow<CellularMetrics>
    
    /**
     * Current Wi-Fi signal measurements.
     */
    val wifiMetrics: Flow<WifiMetrics>
    
    /**
     * Current Bluetooth scan results (anonymized/counts).
     */
    val bluetoothScanResults: Flow<BluetoothScanResults>
    
    /**
     * Current privacy posture.
     */
    val privacyPosture: Flow<PrivacyPosture>
    
    /**
     * Start telemetry collection.
     */
    suspend fun startCollection()
    
    /**
     * Stop telemetry collection.
     */
    suspend fun stopCollection()
}

data class CellularMetrics(
    val rsrp: Int? = null,              // Reference Signal Received Power (dBm)
    val rsrq: Int? = null,              // Reference Signal Received Quality (dB)
    val rssnr: Int? = null,             // Reference Signal Signal to Noise Ratio (dB)
    val ssRsrp: Int? = null,            // SS Reference Signal Received Power (5G)
    val ssRsrq: Int? = null,            // SS Reference Signal Received Quality (5G)
    val ssSinr: Int? = null,            // SS Signal to Interference plus Noise Ratio (5G)
    val cqi: Int? = null,               // Channel Quality Indicator (1 - 15)
    val earfcn: Int? = null,            // LTE E-UTRA Absolute Radio Frequency Channel Number
    val nrarfcn: Int? = null,           // 5G New Radio Absolute Radio Frequency Channel Number
    val bands: List<Int> = emptyList(), // Primary & secondary carrier frequency bands
    val bandwidthKhz: Int? = null,      // Channel bandwidth in kHz
    val technology: String? = null,     // LTE, 5G NSA, 5G SA, etc.
    val displayNetworkType: String? = null, // "5G NSA", "5G+", "5G SA", "LTE"
    val operator: String? = null,
    val cellId: Long? = null,
    val pci: Int? = null,
    val isCongested: Boolean = false    // Inferred cell sector congestion
)

data class WifiMetrics(
    val rssi: Int? = null,      // Received Signal Strength Indicator (dBm)
    val frequency: Int? = null, // MHz
    val channel: Int? = null,
    val linkSpeed: Int? = null, // Mbps
    val ssid: String? = null    // Redacted/hashed
)

data class BluetoothScanResults(
    val deviceCount: Int,
    val isEnabled: Boolean = false,
    val isDiscoverable: Boolean = false,
    val isConnected: Boolean = false
)

/**
 * Interference classifier interface per FR-08.
 */
interface InterferenceClassifier {
    /**
     * Current interference confidence flow.
     */
    val interferenceConfidence: Flow<InterferenceConfidence>

    /**
     * Classify interference based on available signals.
     * @return Confidence-based interference assessment
     */
    suspend fun classifyInterference(
        cellularMetrics: CellularMetrics?,
        wifiMetrics: WifiMetrics?
    ): InterferenceConfidence
    
    /**
     * Update baseline with current measurements.
     */
    suspend fun updateBaseline(cellularMetrics: CellularMetrics?, wifiMetrics: WifiMetrics?)
    
    /**
     * Clear all stored baselines and observations.
     */
    suspend fun clearBaselines()
}

/**
 * Acoustic masking controller interface per FR-09.
 */
interface AcousticMaskingController {
    /**
     * Current acoustic masking state.
     */
    val maskingState: Flow<AcousticMaskState>
    
    /**
     * Current volume level (0.0 to 1.0).
     */
    val volumeLevel: Flow<Float>

    /**
     * Remaining duration in seconds.
     */
    val remainingSeconds: Flow<Long>
    
    /**
     * Start acoustic masking with safe parameters.
     * @param volumeLevel Volume level (0.0 to 1.0)
     * @param durationMillis Maximum duration in milliseconds
     * @param noiseType Noise generation profile
     */
    suspend fun startMasking(
        volumeLevel: Float = 0.5f,
        durationMillis: Long = 300000L,
        noiseType: MaskingNoiseType = MaskingNoiseType.PINK_NOISE
    )
    
    /**
     * Stop acoustic masking.
     */
    suspend fun stopMasking()
    
    /**
     * Adjust masking volume.
     * @param volumeLevel New volume level (0.0 to 1.0)
     */
    suspend fun adjustVolume(volumeLevel: Float)
}