package com.signalbooster.app.presentation.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signalbooster.app.domain.interfaces.AcousticMaskingController
import com.signalbooster.app.domain.interfaces.InterferenceClassifier
import com.signalbooster.app.domain.interfaces.RadioTelemetrySource
import com.signalbooster.app.domain.models.AcousticMaskState
import com.signalbooster.app.domain.models.InterferenceConfidence
import com.signalbooster.app.domain.models.MaskingNoiseType
import com.signalbooster.app.domain.models.PrivacyPosture
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val radioTelemetrySource: RadioTelemetrySource,
    private val interferenceClassifier: InterferenceClassifier,
    private val acousticMaskingController: AcousticMaskingController
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacyUiState())
    val uiState: StateFlow<PrivacyUiState> = _uiState.asStateFlow()

    init {
        // Collect Privacy Posture
        viewModelScope.launch {
            radioTelemetrySource.privacyPosture.collect { posture ->
                _uiState.update { it.copy(privacyPosture = posture) }
            }
        }

        // Collect Interference Classifier
        viewModelScope.launch {
            interferenceClassifier.interferenceConfidence.collect { confidence ->
                _uiState.update { it.copy(interferenceConfidence = confidence) }
            }
        }

        // Collect Acoustic Masking State & Timer
        viewModelScope.launch {
            acousticMaskingController.maskingState.collect { maskState ->
                _uiState.update { it.copy(acousticMaskState = maskState) }
            }
        }
        viewModelScope.launch {
            acousticMaskingController.volumeLevel.collect { vol ->
                _uiState.update { it.copy(volumeLevel = vol) }
            }
        }
        viewModelScope.launch {
            acousticMaskingController.remainingSeconds.collect { sec ->
                _uiState.update { it.copy(remainingSeconds = sec) }
            }
        }

        // Periodic interference classification & baseline updates
        viewModelScope.launch {
            radioTelemetrySource.cellularMetrics.collect { cellular ->
                radioTelemetrySource.wifiMetrics.collect { wifi ->
                    interferenceClassifier.updateBaseline(cellular, wifi)
                    interferenceClassifier.classifyInterference(cellular, wifi)
                }
            }
        }
    }

    fun startAcousticMasking(
        volumeLevel: Float = _uiState.value.volumeLevel,
        durationMinutes: Int = _uiState.value.selectedDurationMinutes,
        noiseType: MaskingNoiseType = _uiState.value.selectedNoiseType
    ) {
        viewModelScope.launch {
            acousticMaskingController.startMasking(
                volumeLevel = volumeLevel,
                durationMillis = durationMinutes * 60 * 1000L,
                noiseType = noiseType
            )
        }
    }

    fun stopAcousticMasking() {
        viewModelScope.launch {
            acousticMaskingController.stopMasking()
        }
    }

    fun setVolume(volume: Float) {
        viewModelScope.launch {
            _uiState.update { it.copy(volumeLevel = volume) }
            acousticMaskingController.adjustVolume(volume)
        }
    }

    fun setDuration(minutes: Int) {
        _uiState.update { it.copy(selectedDurationMinutes = minutes) }
    }

    fun setNoiseType(noiseType: MaskingNoiseType) {
        _uiState.update { it.copy(selectedNoiseType = noiseType) }
    }

    fun clearInterferenceBaselines() {
        viewModelScope.launch {
            interferenceClassifier.clearBaselines()
        }
    }
}

data class PrivacyUiState(
    val privacyPosture: PrivacyPosture = PrivacyPosture.DEFAULT,
    val interferenceConfidence: InterferenceConfidence = InterferenceConfidence.NORMAL,
    val acousticMaskState: AcousticMaskState = AcousticMaskState.STOPPED,
    val volumeLevel: Float = 0.5f,
    val remainingSeconds: Long = 0L,
    val selectedDurationMinutes: Int = 5,
    val selectedNoiseType: MaskingNoiseType = MaskingNoiseType.PINK_NOISE
)
