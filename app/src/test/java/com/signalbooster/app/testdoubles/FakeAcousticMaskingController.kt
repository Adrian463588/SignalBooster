package com.signalbooster.app.testdoubles

import com.signalbooster.app.domain.interfaces.AcousticMaskingController
import com.signalbooster.app.domain.models.AcousticMaskState
import com.signalbooster.app.domain.models.MaskingNoiseType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAcousticMaskingController : AcousticMaskingController {
    private val _maskingState = MutableStateFlow(AcousticMaskState.STOPPED)
    override val maskingState: Flow<AcousticMaskState> = _maskingState.asStateFlow()

    private val _volumeLevel = MutableStateFlow(0.5f)
    override val volumeLevel: Flow<Float> = _volumeLevel.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(300L)
    override val remainingSeconds: Flow<Long> = _remainingSeconds.asStateFlow()

    override suspend fun startMasking(
        volumeLevel: Float,
        durationMillis: Long,
        noiseType: MaskingNoiseType
    ) {
        _volumeLevel.value = volumeLevel
        _remainingSeconds.value = durationMillis / 1000
        _maskingState.value = AcousticMaskState.RUNNING
    }

    override suspend fun stopMasking() {
        _maskingState.value = AcousticMaskState.STOPPED
    }

    override suspend fun adjustVolume(volumeLevel: Float) {
        _volumeLevel.value = volumeLevel.coerceIn(0.05f, 1.0f)
    }
}
