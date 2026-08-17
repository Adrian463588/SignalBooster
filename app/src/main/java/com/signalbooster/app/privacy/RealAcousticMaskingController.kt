package com.signalbooster.app.privacy

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.signalbooster.app.domain.interfaces.AcousticMaskingController
import com.signalbooster.app.domain.models.AcousticMaskState
import com.signalbooster.app.domain.models.MaskingNoiseType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin

/**
 * Real platform implementation of AcousticMaskingController using AudioTrack output.
 * Generates local synthetic audio masking (White, Pink, or Brown noise) to aid privacy.
 * Strictly output-only; ZERO microphone recording per AGENTS.md section 7.
 */
@Singleton
class RealAcousticMaskingController @Inject constructor(
    @ApplicationContext private val context: Context
) : AcousticMaskingController {

    private val _maskingState = MutableStateFlow(AcousticMaskState.STOPPED)
    override val maskingState: Flow<AcousticMaskState> = _maskingState.asStateFlow()

    private val _volumeLevel = MutableStateFlow(0.5f)
    override val volumeLevel: Flow<Float> = _volumeLevel.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0L)
    override val remainingSeconds: Flow<Long> = _remainingSeconds.asStateFlow()

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private var timerJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private val sampleRate = 44100
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(4096)

    override suspend fun startMasking(
        volumeLevel: Float,
        durationMillis: Long,
        noiseType: MaskingNoiseType
    ) = withContext(Dispatchers.Default) {
        if (_maskingState.value == AcousticMaskState.RUNNING || _maskingState.value == AcousticMaskState.STARTING) {
            return@withContext
        }

        _maskingState.value = AcousticMaskState.STARTING
        val clampedVolume = volumeLevel.coerceIn(0.05f, 1.0f)
        _volumeLevel.value = clampedVolume
        val initialSeconds = (durationMillis / 1000L).coerceIn(10L, 7200L)
        _remainingSeconds.value = initialSeconds

        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.setVolume(clampedVolume)
            audioTrack?.play()

            _maskingState.value = AcousticMaskState.RUNNING

            // Start Foreground Service for user-visible status
            startAcousticService()

            // Start synthetic noise synthesis loop with voice-band envelope modulation
            playbackJob = coroutineScope.launch(Dispatchers.Default) {
                val random = Random()
                val audioBuffer = ShortArray(bufferSize / 2)
                
                // Pink noise filter state variables (Paul Kellet's 6-pole filter)
                var b0 = 0.0
                var b1 = 0.0
                var b2 = 0.0
                var b3 = 0.0
                var b4 = 0.0
                var b5 = 0.0
                var b6 = 0.0
                
                // Brown noise state
                var lastBrownOutput = 0.0
                
                // Voice envelope modulation phase (3 Hz cadence)
                var modPhase = 0.0
                val modPhaseIncrement = 2.0 * Math.PI * 3.0 / sampleRate

                while (isActive && _maskingState.value == AcousticMaskState.RUNNING) {
                    for (i in audioBuffer.indices) {
                        val white = (random.nextDouble() * 2.0 - 1.0)
                        val sample = when (noiseType) {
                            MaskingNoiseType.WHITE_NOISE -> white
                            MaskingNoiseType.PINK_NOISE -> {
                                b0 = 0.99886 * b0 + white * 0.0555179
                                b1 = 0.99332 * b1 + white * 0.0750759
                                b2 = 0.96900 * b2 + white * 0.1538520
                                b3 = 0.86650 * b3 + white * 0.3104856
                                b4 = 0.55000 * b4 + white * 0.5329522
                                b5 = -0.7616 * b5 - white * 0.0168980
                                val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362
                                b6 = white * 0.115926
                                (pink * 0.15).coerceIn(-1.0, 1.0)
                            }
                            MaskingNoiseType.BROWN_NOISE -> {
                                lastBrownOutput = (lastBrownOutput + (0.02 * white)) / 1.02
                                (lastBrownOutput * 3.5).coerceIn(-1.0, 1.0)
                            }
                        }
                        
                        // Apply subtle 3Hz human speech cadence modulation (depth: 0.15)
                        val modulation = 0.85 + 0.15 * sin(modPhase)
                        modPhase += modPhaseIncrement
                        if (modPhase > 2.0 * Math.PI) modPhase -= 2.0 * Math.PI

                        audioBuffer[i] = (sample * modulation * 32767.0 * _volumeLevel.value).toInt().toShort()
                    }
                    audioTrack?.write(audioBuffer, 0, audioBuffer.size)
                }
            }

            // Start countdown timer
            timerJob = coroutineScope.launch(Dispatchers.Default) {
                while (isActive && _remainingSeconds.value > 0) {
                    delay(1000L)
                    _remainingSeconds.value -= 1
                }
                if (_remainingSeconds.value <= 0) {
                    stopMasking()
                }
            }
        } catch (e: Exception) {
            cleanupAudioResources()
            _maskingState.value = AcousticMaskState.FAILED
        }
    }

    override suspend fun stopMasking() = withContext(Dispatchers.Default) {
        if (_maskingState.value == AcousticMaskState.STOPPED) return@withContext
        _maskingState.value = AcousticMaskState.STOPPING

        cleanupAudioResources()
        stopAcousticService()

        _remainingSeconds.value = 0L
        _maskingState.value = AcousticMaskState.STOPPED
    }

    override suspend fun adjustVolume(volumeLevel: Float) {
        val clamped = volumeLevel.coerceIn(0.05f, 1.0f)
        _volumeLevel.value = clamped
        audioTrack?.setVolume(clamped)
    }

    private fun cleanupAudioResources() {
        playbackJob?.cancel()
        playbackJob = null
        timerJob?.cancel()
        timerJob = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    private fun startAcousticService() {
        try {
            val intent = Intent(context, AcousticMaskingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {}
    }

    private fun stopAcousticService() {
        try {
            val intent = Intent(context, AcousticMaskingService::class.java)
            context.stopService(intent)
        } catch (_: Exception) {}
    }
}
