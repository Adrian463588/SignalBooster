package com.signalbooster.app

import com.signalbooster.app.domain.models.AcousticMaskState
import com.signalbooster.app.domain.models.MaskingNoiseType
import com.signalbooster.app.presentation.privacy.PrivacyViewModel
import com.signalbooster.app.testdoubles.FakeAcousticMaskingController
import com.signalbooster.app.testdoubles.FakeInterferenceClassifier
import com.signalbooster.app.testdoubles.FakeRadioTelemetrySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeTelemetrySource: FakeRadioTelemetrySource
    private lateinit var fakeClassifier: FakeInterferenceClassifier
    private lateinit var fakeAcousticController: FakeAcousticMaskingController
    private lateinit var viewModel: PrivacyViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeTelemetrySource = FakeRadioTelemetrySource()
        fakeClassifier = FakeInterferenceClassifier()
        fakeAcousticController = FakeAcousticMaskingController()

        viewModel = PrivacyViewModel(
            radioTelemetrySource = fakeTelemetrySource,
            interferenceClassifier = fakeClassifier,
            acousticMaskingController = fakeAcousticController
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testStartAndStopAcousticMasking() = runTest(testDispatcher) {
        viewModel.setNoiseType(MaskingNoiseType.BROWN_NOISE)
        viewModel.setVolume(0.8f)
        viewModel.setDuration(15)
        viewModel.startAcousticMasking()

        var state = viewModel.uiState.value
        assertEquals(AcousticMaskState.RUNNING, state.acousticMaskState)
        assertEquals(MaskingNoiseType.BROWN_NOISE, state.selectedNoiseType)
        assertEquals(0.8f, state.volumeLevel, 0.01f)

        viewModel.stopAcousticMasking()

        state = viewModel.uiState.value
        assertEquals(AcousticMaskState.STOPPED, state.acousticMaskState)
    }

    @Test
    fun testClearInterferenceBaselines() = runTest(testDispatcher) {
        viewModel.clearInterferenceBaselines()
        assertTrue(fakeClassifier.wasBaselinesCleared)
    }
}
