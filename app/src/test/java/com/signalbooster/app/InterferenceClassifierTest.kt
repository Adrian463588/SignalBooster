package com.signalbooster.app

import com.signalbooster.app.domain.interfaces.CellularMetrics
import com.signalbooster.app.domain.interfaces.WifiMetrics
import com.signalbooster.app.domain.models.InterferenceTier
import com.signalbooster.app.privacy.LocalInterferenceClassifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InterferenceClassifierTest {

    private lateinit var classifier: LocalInterferenceClassifier

    @Before
    fun setup() {
        classifier = LocalInterferenceClassifier()
    }

    @Test
    fun testStableBaselineProducesNormalTier() = runBlocking {
        // Build stable baseline around -75 dBm
        for (i in 1..10) {
            val cellular = CellularMetrics(rsrp = -75, rsrq = -10)
            val wifi = WifiMetrics(rssi = -60)
            classifier.updateBaseline(cellular, wifi)
        }

        val currentCellular = CellularMetrics(rsrp = -74, rsrq = -10)
        val currentWifi = WifiMetrics(rssi = -59)
        val result = classifier.classifyInterference(currentCellular, currentWifi)

        assertEquals(InterferenceTier.NORMAL_OR_UNKNOWN, result.tier)
        assertTrue(result.observations.isEmpty())
    }

    @Test
    fun testAbruptMultiSignalDropTriggersInterferenceTier() = runBlocking {
        // Build stable baseline
        for (i in 1..10) {
            val cellular = CellularMetrics(rsrp = -70, rsrq = -8)
            val wifi = WifiMetrics(rssi = -50)
            classifier.updateBaseline(cellular, wifi)
        }

        // Set previous measurement
        classifier.classifyInterference(
            CellularMetrics(rsrp = -70, rsrq = -8),
            WifiMetrics(rssi = -50)
        )

        // Sudden drop of > 25 dBm
        val abruptCellular = CellularMetrics(rsrp = -115, rsrq = -19)
        val abruptWifi = WifiMetrics(rssi = -90)
        val result = classifier.classifyInterference(abruptCellular, abruptWifi)

        assertTrue(
            "Should detect possible or likely interference anomaly",
            result.tier == InterferenceTier.POSSIBLE_LOCALIZED_INTERFERENCE ||
            result.tier == InterferenceTier.LIKELY_LOCALIZED_INTERFERENCE
        )
        assertTrue("Should have non-empty observations", result.observations.isNotEmpty())
        assertTrue("Must include honest limitation", result.limitation.isNotBlank())
    }

    @Test
    fun testClearBaselinesResetsState() = runBlocking {
        for (i in 1..10) {
            classifier.updateBaseline(CellularMetrics(rsrp = -70), WifiMetrics(rssi = -50))
        }
        classifier.clearBaselines()

        val result = classifier.classifyInterference(CellularMetrics(rsrp = -80), WifiMetrics(rssi = -65))
        assertEquals(InterferenceTier.NORMAL_OR_UNKNOWN, result.tier)
    }
}
