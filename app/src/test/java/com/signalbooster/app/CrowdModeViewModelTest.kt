package com.signalbooster.app

import com.signalbooster.app.presentation.crowdmode.CrowdModeViewModel
import com.signalbooster.app.testdoubles.FakeNetworkMonitor
import com.signalbooster.app.testdoubles.FakeRadioTelemetrySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CrowdModeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeNetworkMonitor: FakeNetworkMonitor
    private lateinit var fakeRadioTelemetrySource: FakeRadioTelemetrySource
    private lateinit var viewModel: CrowdModeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeNetworkMonitor = FakeNetworkMonitor()
        fakeRadioTelemetrySource = FakeRadioTelemetrySource()
        viewModel = CrowdModeViewModel(fakeNetworkMonitor, fakeRadioTelemetrySource)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testCrowdAnalysisLoadingAndEmission() = runTest(testDispatcher) {
        viewModel.loadCrowdAnalysis()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.bandObservations)
        assertNotNull(state.recommendations)
    }

    @Test
    fun testCongested5gGenerates4gBandSteeringAdvice() = runTest(testDispatcher) {
        fakeRadioTelemetrySource.emitCellularMetrics(
            com.signalbooster.app.domain.interfaces.CellularMetrics(
                technology = "5G NR",
                displayNetworkType = "5G NSA",
                ssRsrp = -80,
                ssSinr = 2, // Degraded SINR despite high power
                ssRsrq = -16,
                bands = listOf(1, 3, 7),
                isCongested = true
            )
        )
        viewModel.loadCrowdAnalysis()

        val state = viewModel.uiState.value
        assertNotNull(state.bandSteeringAdvice)
        assertEquals("4G LTE (Carrier Aggregation)", state.bandSteeringAdvice?.recommendedRat)
        assertEquals("LTE Band 1, 3, 7", state.bandSteeringAdvice?.targetBand)
        assertEquals(com.signalbooster.app.domain.models.CongestionState.SPECTRUM_CONGESTION, state.congestionState)
    }

    @Test
    fun testCrowded24GhzWifiFlagsHighCongestion() = runTest(testDispatcher) {
        fakeRadioTelemetrySource.emitWifiMetrics(
            com.signalbooster.app.domain.interfaces.WifiMetrics(
                frequency = 2412, // 2.4 GHz
                rssi = -70,
                channel = 1
            )
        )
        viewModel.loadCrowdAnalysis()

        val state = viewModel.uiState.value
        val wifiObs = state.bandObservations.firstOrNull { it.frequencyMhz == 2412 }
        assertNotNull(wifiObs)
        assertEquals("HIGH (Crowded 2.4 GHz spectrum)", wifiObs?.congestionRisk)
    }
}
