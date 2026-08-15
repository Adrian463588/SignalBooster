package com.signalbooster.app.testdoubles

import com.signalbooster.app.domain.interfaces.CellularMetrics
import com.signalbooster.app.domain.interfaces.InterferenceClassifier
import com.signalbooster.app.domain.interfaces.RadioTelemetrySource
import com.signalbooster.app.domain.interfaces.WifiMetrics
import com.signalbooster.app.domain.models.BluetoothPosture
import com.signalbooster.app.domain.models.CellularPosture
import com.signalbooster.app.domain.models.ConfidenceLevel
import com.signalbooster.app.domain.models.InterferenceConfidence
import com.signalbooster.app.domain.models.InterferenceTier
import com.signalbooster.app.domain.models.PrivacyPosture
import com.signalbooster.app.domain.models.WifiPosture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeRadioTelemetrySource : RadioTelemetrySource {
    private val _posture = MutableStateFlow(
        PrivacyPosture(
            bluetoothState = BluetoothPosture(isEnabled = true, isDiscoverable = false),
            wifiState = WifiPosture(),
            cellularState = CellularPosture()
        )
    )
    override val privacyPosture: StateFlow<PrivacyPosture> = _posture.asStateFlow()

    private val _cellular = MutableStateFlow(CellularMetrics(rsrp = -75, operator = "TestNet"))
    override val cellularMetrics: StateFlow<CellularMetrics> = _cellular.asStateFlow()

    private val _wifi = MutableStateFlow(WifiMetrics(rssi = -55, ssid = "TestWiFi"))
    override val wifiMetrics: StateFlow<WifiMetrics> = _wifi.asStateFlow()

    override suspend fun startCollection() {}
    override suspend fun stopCollection() {}
    override fun isCollecting(): Boolean = true
}

class FakeInterferenceClassifier : InterferenceClassifier {
    private val _confidence = MutableStateFlow(
        InterferenceConfidence(
            tier = InterferenceTier.NORMAL_OR_UNKNOWN,
            confidence = ConfidenceLevel.HIGH,
            reason = "Radio baselines operating in nominal range"
        )
    )
    override val currentConfidence: StateFlow<InterferenceConfidence> = _confidence.asStateFlow()

    var wasBaselinesCleared: Boolean = false

    override suspend fun classifyInterference(
        cellularMetrics: CellularMetrics,
        wifiMetrics: WifiMetrics
    ): InterferenceConfidence = _confidence.value

    override fun updateBaseline(cellularMetrics: CellularMetrics, wifiMetrics: WifiMetrics) {}

    override fun clearBaselines() {
        wasBaselinesCleared = true
    }
}
