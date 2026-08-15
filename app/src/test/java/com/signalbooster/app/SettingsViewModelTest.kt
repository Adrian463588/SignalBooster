package com.signalbooster.app

import com.signalbooster.app.presentation.settings.SettingsViewModel
import com.signalbooster.app.testdoubles.FakeRadioTelemetrySource
import com.signalbooster.app.testdoubles.FakeSettingsRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var fakeRadioTelemetrySource: FakeRadioTelemetrySource
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeSettingsRepository = FakeSettingsRepository()
        fakeRadioTelemetrySource = FakeRadioTelemetrySource()
        viewModel = SettingsViewModel(
            settingsRepository = fakeSettingsRepository,
            radioTelemetrySource = fakeRadioTelemetrySource
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testUpdateProbeEndpoint() = runTest(testDispatcher) {
        val newEndpoint = "https://1.1.1.1"
        viewModel.setProbeEndpoint(newEndpoint)

        val state = viewModel.uiState.value
        assertEquals(newEndpoint, state.probeEndpoint)
    }

    @Test
    fun testUpdateByteBudgetAndTimeout() = runTest(testDispatcher) {
        viewModel.setByteBudget(5242880L)
        viewModel.setProbeTimeout(15000L)

        val state = viewModel.uiState.value
        assertEquals(5242880L, state.probeByteBudget)
        assertEquals(15000L, state.probeTimeoutMs)
    }

    @Test
    fun testToggleAdaptiveMonitoring() = runTest(testDispatcher) {
        viewModel.toggleAdaptiveMonitoring(false)

        val state = viewModel.uiState.value
        assertFalse(state.isAdaptiveMonitoring)
    }

    @Test
    fun testWipeLocalData() = runTest(testDispatcher) {
        viewModel.wipeAllLocalData()

        val state = viewModel.uiState.value
        assertNotNull(state.wipeMessage)
        assertTrue("Repository wipe should have been triggered", fakeSettingsRepository.wasWiped)
    }
}
