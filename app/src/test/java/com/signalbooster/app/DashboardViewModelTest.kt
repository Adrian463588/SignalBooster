package com.signalbooster.app

import com.signalbooster.app.domain.interfaces.ProbeType
import com.signalbooster.app.domain.models.CapabilityState
import com.signalbooster.app.domain.models.DataAvailability
import com.signalbooster.app.domain.models.NetworkAction
import com.signalbooster.app.domain.models.NetworkRecommendation
import com.signalbooster.app.domain.models.NetworkSnapshot
import com.signalbooster.app.domain.models.NetworkValidation
import com.signalbooster.app.domain.models.QualityMetrics
import com.signalbooster.app.domain.models.Transport
import com.signalbooster.app.presentation.connection.DashboardViewModel
import com.signalbooster.app.testdoubles.FakeNetworkMonitor
import com.signalbooster.app.testdoubles.FakeQualityProbe
import com.signalbooster.app.testdoubles.FakeRecoveryCoordinator
import com.signalbooster.app.testdoubles.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeNetworkMonitor: FakeNetworkMonitor
    private lateinit var fakeQualityProbe: FakeQualityProbe
    private lateinit var fakeRecoveryCoordinator: FakeRecoveryCoordinator
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeNetworkMonitor = FakeNetworkMonitor()
        fakeQualityProbe = FakeQualityProbe()
        fakeRecoveryCoordinator = FakeRecoveryCoordinator()
        fakeSettingsRepository = FakeSettingsRepository()

        viewModel = DashboardViewModel(
            networkMonitor = fakeNetworkMonitor,
            qualityProbe = fakeQualityProbe,
            recoveryCoordinator = fakeRecoveryCoordinator,
            settingsRepository = fakeSettingsRepository
        )
    }

    @After
    fun tearDown() {
        viewModel.stopMonitoring()
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialMonitoringState() = runTest(testDispatcher) {
        runCurrent()
        val state = viewModel.uiState.value
        assertTrue("Monitoring should be active after init", state.isMonitoring)
    }

    @Test
    fun testNetworkSnapshotUpdateUpdatesUiState() = runTest(testDispatcher) {
        val testSnapshot = NetworkSnapshot(
            transport = Transport.WIFI,
            validation = NetworkValidation.VALIDATED,
            isMetered = false,
            isCaptivePortal = false,
            isVpnActive = false,
            availability = DataAvailability.AVAILABLE
        )

        fakeNetworkMonitor.emitSnapshot(testSnapshot)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(Transport.WIFI, state.networkSnapshot.transport)
        assertEquals(NetworkValidation.VALIDATED, state.networkSnapshot.validation)
    }

    @Test
    fun testRunQualityProbeUpdatesMetrics() = runTest(testDispatcher) {
        fakeQualityProbe.defaultMetricsToEmit = QualityMetrics(
            latencyRttMs = 45,
            jitterMs = 5,
            lossRatio = 0.0f,
            throughputMbps = 42.0f
        )

        viewModel.runQualityProbe(ProbeType.HTTP)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(45, state.qualityMetrics.latencyRttMs)
        assertEquals(5, state.qualityMetrics.jitterMs)
        assertFalse("Probing flag should be reset after probe", state.isProbing)
    }

    @Test
    fun testAttemptRecoveryExecutesAndUpdatesRecommendation() = runTest(testDispatcher) {
        fakeRecoveryCoordinator.recommendationToReturn = NetworkRecommendation(
            action = NetworkAction.TRY_ALTERNATIVE,
            evidence = emptyList(),
            confidence = com.signalbooster.app.domain.models.ConfidenceLevel.HIGH,
            limitation = null
        )

        viewModel.attemptRecovery()
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse("Recovering flag should be false", state.isRecovering)
        assertTrue(state.lastRecoveryMessage != null)
    }

    @Test
    fun testStopMonitoringCancelsActiveState() = runTest(testDispatcher) {
        viewModel.stopMonitoring()
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse("Monitoring should be stopped", state.isMonitoring)
    }
}
