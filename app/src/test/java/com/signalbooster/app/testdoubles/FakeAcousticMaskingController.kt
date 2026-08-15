package com.signalbooster.app.testdoubles

import com.signalbooster.app.domain.interfaces.AcousticMaskingController
import com.signalbooster.app.domain.models.AcousticMaskState
import com.signalbooster.app.domain.models.AcousticMaskingConfig
import com.signalbooster.app.domain.models.MaskingNoiseType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAcousticMaskingController : AcousticMaskingController {
    private val _state = MutableStateFlow(AcousticMaskState.STOPPED)
    override val maskState: StateFlow<AcousticMaskState> = _state.asStateFlow()

    private val _config = MutableStateFlow(AcousticMaskingConfig())
    override val currentConfig: StateFlow<AcousticMaskingConfig> = _config.asStateFlow()

    private val _remaining = MutableStateFlow(300)
    override val remainingDurationSeconds: StateFlow<Int> = _remaining.asStateFlow()

    override suspend fun startMasking(config: AcousticMaskingConfig): Boolean {
        _config.value = config
        _remaining.value = (config.durationMillis / 1000).toInt()
        _state.value = AcousticMaskState.RUNNING
        return true
    }

    override suspend fun stopMasking() {
        _state.value = AcousticMaskState.STOPPED
    }

    override fun setVolume(volume: Float) {
        _config.value = _config.value.copy(volumeLevel = volume.coerceIn(0.05f, 1.0f))
    }

    override fun setNoiseType(noiseType: MaskingNoiseType) {
        _config.value = _config.value.copy(noiseType = noiseType)
    }

    override fun setDurationMinutes(minutes: Int) {
        val millis = minutes * 60 * 1000L
        _config.value = _config.value.copy(durationMillis = millis)
        _remaining.value = minutes * 60
    }
}
