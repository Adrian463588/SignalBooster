package com.signalbooster.app.testdoubles

import com.signalbooster.app.domain.interfaces.BluetoothScanResults
import com.signalbooster.app.domain.interfaces.CellularMetrics
import com.signalbooster.app.domain.interfaces.InterferenceClassifier
import com.signalbooster.app.domain.interfaces.RadioTelemetrySource
import com.signalbooster.app.domain.interfaces.WifiMetrics
import com.signalbooster.app.domain.models.BleCorroborationState
import com.signalbooster.app.domain.models.BluetoothConnectionState
import com.signalbooster.app.domain.models.BluetoothPosture
import com.signalbooster.app.domain.models.CellularPosture
import com.signalbooster.app.domain.models.ConfidenceLevel
import com.signalbooster.app.domain.models.InterferenceConfidence
import com.signalbooster.app.domain.models.InterferenceTier
import com.signalbooster.app.domain.models.NetworkValidation
import com.signalbooster.app.domain.models.PermissionState
import com.signalbooster.app.domain.models.PrivacyPosture
import com.signalbooster.app.domain.models.WifiPosture
import com.signalbooster.app.domain.models.WifiSecurity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

class FakeRadioTelemetrySource : RadioTelemetrySource {
    private val _posture = MutableStateFlow(
        PrivacyPosture(
            bluetoothState = BluetoothPosture(
                isEnabled = true,
                isDiscoverable = false,
                connectionState = BluetoothConnectionState.DISCONNECTED
            ),
            wifiState = WifiPosture(
                securityLevel = WifiSecurity.WPA2,
                validationState = NetworkValidation.VALIDATED,
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
    )
    override val privacyPosture: Flow<PrivacyPosture> = _posture.asStateFlow()

    private val _cellular = MutableStateFlow(CellularMetrics(rsrp = -75, operator = "TestNet"))
    override val cellularMetrics: Flow<CellularMetrics> = _cellular.asStateFlow()

    private val _wifi = MutableStateFlow(WifiMetrics(rssi = -55, ssid = "TestWiFi"))
    override val wifiMetrics: Flow<WifiMetrics> = _wifi.asStateFlow()

    private val _bluetooth = MutableStateFlow(BluetoothScanResults(deviceCount = 3, isEnabled = true))
    override val bluetoothScanResults: Flow<BluetoothScanResults> = _bluetooth.asStateFlow()

    fun emitCellularMetrics(metrics: CellularMetrics) {
        _cellular.value = metrics
    }

    fun emitWifiMetrics(metrics: WifiMetrics) {
        _wifi.value = metrics
    }

    override suspend fun startCollection() {}
    override suspend fun stopCollection() {}
}

class FakeInterferenceClassifier : InterferenceClassifier {
    private val _confidence = MutableStateFlow(
        InterferenceConfidence(
            tier = InterferenceTier.NORMAL_OR_UNKNOWN,
            reason = "Radio baselines operating in nominal range",
            observations = emptyList(),
            confidence = ConfidenceLevel.HIGH,
            limitation = "Passive heuristics only"
        )
    )
    override val interferenceConfidence: Flow<InterferenceConfidence> = _confidence.asStateFlow()

    var wasBaselinesCleared: Boolean = false

    override suspend fun classifyInterference(
        cellularMetrics: CellularMetrics?,
        wifiMetrics: WifiMetrics?
    ): InterferenceConfidence = _confidence.value

    override suspend fun updateBaseline(cellularMetrics: CellularMetrics?, wifiMetrics: WifiMetrics?) {}

    override suspend fun clearBaselines() {
        wasBaselinesCleared = true
    }
}
