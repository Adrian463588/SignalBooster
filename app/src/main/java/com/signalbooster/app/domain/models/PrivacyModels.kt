package com.signalbooster.app.domain.models

import java.time.Instant

/**
 * Privacy posture, interference observation, and acoustic masking models per PRD.md section 8.
 */

enum class PrivacyMode {
    OFF,
    PASSIVE_POSTURE,
    ACOUSTIC_MASKING
}

enum class AcousticMaskState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED
}

enum class MaskingNoiseType {
    WHITE_NOISE,
    PINK_NOISE,
    BROWN_NOISE
}

data class AcousticMaskingConfig(
    val volumeLevel: Float = 0.5f,
    val durationMillis: Long = 300000L, // 5 minutes
    val noiseType: MaskingNoiseType = MaskingNoiseType.PINK_NOISE
)

data class PrivacyPosture(
    val bluetoothState: BluetoothPosture,
    val wifiState: WifiPosture,
    val cellularState: CellularPosture,
    val permissionState: PermissionState,
    val bleCorroboration: BleCorroborationState,
    val timestamp: Instant = Instant.now()
) {
    companion object {
        val DEFAULT = PrivacyPosture(
            bluetoothState = BluetoothPosture(
                isEnabled = false,
                isDiscoverable = false,
                connectionState = BluetoothConnectionState.DISCONNECTED
            ),
            wifiState = WifiPosture(
                securityLevel = WifiSecurity.UNKNOWN,
                validationState = NetworkValidation.UNKNOWN,
                isCaptivePortal = false
            ),
            cellularState = CellularPosture(
                availabilityChanges = emptyList(),
                multiSignalAnomalies = emptyList()
            ),
            permissionState = PermissionState(
                requiredPermissions = emptySet(),
                grantedPermissions = emptySet(),
                lastCheckTime = Instant.now()
            ),
            bleCorroboration = BleCorroborationState(
                isEnabled = false,
                isActive = false,
                peerCount = 0,
                uptimeSeconds = 0
            )
        )
    }
}

data class BluetoothPosture(
    val isEnabled: Boolean,
    val isDiscoverable: Boolean,
    val connectionState: BluetoothConnectionState
)

enum class BluetoothConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    UNKNOWN
}

data class WifiPosture(
    val securityLevel: WifiSecurity,
    val validationState: NetworkValidation,
    val isCaptivePortal: Boolean
)

enum class WifiSecurity {
    OPEN,
    WEP,
    WPA_PERSONAL,
    WPA_ENTERPRISE,
    WPA2,
    WPA3,
    UNKNOWN
}

data class CellularPosture(
    val availabilityChanges: List<CellularChange>,
    val multiSignalAnomalies: List<SignalAnomaly>
)

data class CellularChange(
    val timestamp: Instant,
    val changeType: CellularChangeType,
    val details: String
)

enum class CellularChangeType {
    TECHNOLOGY_CHANGE, // LTE -> 5G, etc.
    OPERATOR_CHANGE,
    SIGNAL_STRENGTH_CHANGE,
    CELL_CHANGE
}

data class SignalAnomaly(
    val signalType: SignalType,
    val deviation: Float, // Standard deviation from baseline
    val timestamp: Instant
)

enum class SignalType {
    RSRP,
    RSRQ,
    RSSNR,
    SS_RSRP,
    SS_RSRQ,
    SS_SINR,
    WIFI_RSSI
}

data class PermissionState(
    val requiredPermissions: Set<Permission>,
    val grantedPermissions: Set<Permission>,
    val lastCheckTime: Instant
)

enum class Permission {
    INTERNET,
    ACCESS_NETWORK_STATE,
    ACCESS_WIFI_STATE,
    CHANGE_WIFI_STATE,
    NEARBY_WIFI_DEVICES,
    READ_PHONE_STATE,
    ACCESS_COARSE_LOCATION,
    ACCESS_FINE_LOCATION,
    BLUETOOTH,
    BLUETOOTH_ADMIN,
    BLUETOOTH_SCAN,
    BLUETOOTH_ADVERTISE,
    BLUETOOTH_CONNECT,
    POST_NOTIFICATIONS
}

data class BleCorroborationState(
    val isEnabled: Boolean,
    val isActive: Boolean,
    val peerCount: Int,
    val uptimeSeconds: Long
)

/**
 * Confidence-based interference observation per FR-08.
 */
data class InterferenceConfidence(
    val tier: InterferenceTier,
    val reason: String,
    val observations: List<InterferenceObservation>,
    val peerCount: Int = 0,
    val confidence: ConfidenceLevel,
    val timestamp: Instant = Instant.now(),
    val limitation: String
) {
    companion object {
        val NORMAL = InterferenceConfidence(
            tier = InterferenceTier.NORMAL_OR_UNKNOWN,
            reason = "No anomalous radio pattern detected",
            observations = emptyList(),
            confidence = ConfidenceLevel.LOW,
            limitation = "Passive heuristics only; radio fading or physical obstacles may cause signal drops."
        )
    }
}

enum class InterferenceTier {
    NORMAL_OR_UNKNOWN,
    POSSIBLE_LOCALIZED_INTERFERENCE,
    LIKELY_LOCALIZED_INTERFERENCE
}

data class InterferenceObservation(
    val signal: SignalType,
    val value: Float,
    val baseline: Float,
    val deviation: Float,
    val isAbrupt: Boolean
)