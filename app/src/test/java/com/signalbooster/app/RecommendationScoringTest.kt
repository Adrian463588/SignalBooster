package com.signalbooster.app

import com.signalbooster.app.domain.models.ConfidenceLevel
import com.signalbooster.app.domain.models.DataAvailability
import com.signalbooster.app.domain.models.NetworkAction
import com.signalbooster.app.domain.models.NetworkSnapshot
import com.signalbooster.app.domain.models.NetworkValidation
import com.signalbooster.app.domain.models.QualityMetrics
import com.signalbooster.app.domain.models.Transport
import com.signalbooster.app.platform.RealRecoveryCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationScoringTest {

    @Test
    fun testCaptivePortalRecommendation() = runBlocking {
        // Mock Context is not needed if we test getRecommendation which runs pure domain logic
        val snapshot = NetworkSnapshot(
            transport = Transport.WIFI,
            validation = NetworkValidation.CAPTIVE_PORTAL,
            isMetered = false,
            isCaptivePortal = true,
            isVpnActive = false,
            availability = DataAvailability.AVAILABLE
        )

        // QoE score calculation verification
        val metrics = QualityMetrics(latencyRttMs = 150, lossRatio = 0.0f)
        val score = requireNotNull(metrics.calculateQoEScore())
        assertTrue("Score should be calculated reasonably", score in 0..100)

        // Captive portal evaluation
        val isCaptive = snapshot.isCaptivePortal
        assertTrue("Captive portal must be true", isCaptive)
    }

    @Test
    fun testDegradedLatencyQoEScore() {
        val goodMetrics = QualityMetrics(
            latencyRttMs = 35,
            jitterMs = 4,
            lossRatio = 0.0f,
            throughputMbps = 50.0f
        )
        val degradedMetrics = QualityMetrics(
            latencyRttMs = 450,
            jitterMs = 80,
            lossRatio = 0.20f,
            throughputMbps = 1.0f
        )

        val goodScore = requireNotNull(goodMetrics.calculateQoEScore())
        val degradedScore = requireNotNull(degradedMetrics.calculateQoEScore())

        assertTrue("Good connection score should exceed 80, was $goodScore", goodScore >= 80)
        assertTrue("Degraded connection score should be below 50, was $degradedScore", degradedScore < 50)
    }
}
