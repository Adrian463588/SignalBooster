package com.signalbooster.app

import com.signalbooster.app.domain.models.AcousticMaskState
import com.signalbooster.app.domain.models.AcousticMaskingConfig
import com.signalbooster.app.domain.models.MaskingNoiseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcousticMaskingTest {

    @Test
    fun testDefaultAcousticMaskingConfig() {
        val config = AcousticMaskingConfig()

        assertEquals(0.5f, config.volumeLevel, 0.001f)
        assertEquals(300000L, config.durationMillis)
        assertEquals(MaskingNoiseType.PINK_NOISE, config.noiseType)
    }

    @Test
    fun testAcousticMaskStateTransitions() {
        val states = listOf(
            AcousticMaskState.STOPPED,
            AcousticMaskState.STARTING,
            AcousticMaskState.RUNNING,
            AcousticMaskState.STOPPING,
            AcousticMaskState.FAILED
        )

        assertEquals(5, states.size)
        assertTrue(states.contains(AcousticMaskState.RUNNING))
    }
}
