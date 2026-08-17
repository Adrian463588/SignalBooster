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
}
