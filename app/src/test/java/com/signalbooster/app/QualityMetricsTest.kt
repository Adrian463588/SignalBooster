package com.signalbooster.app

import com.signalbooster.app.domain.models.MeasurementConfidence
import com.signalbooster.app.domain.models.ProbeScope
import com.signalbooster.app.domain.models.QualityMetrics
import com.signalbooster.app.domain.models.SignalQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityMetricsTest {

    @Test
    fun testQoEScoreBounds() {
        val maxMetrics = QualityMetrics(
            latencyRttMs = 20,
            jitterMs = 2,
            lossRatio = 0.0f,
            throughputMbps = 100.0f
        )
        val minMetrics = QualityMetrics(
            latencyRttMs = 999,
            jitterMs = 200,
            lossRatio = 0.80f,
            throughputMbps = 0.1f
        )

        val maxScore = maxMetrics.calculateQoEScore()
        val minScore = minMetrics.calculateQoEScore()

        assertTrue("Max QoE score should be bounded at 100, was $maxScore", maxScore <= 100)
        assertTrue("Max QoE score should be high, was $maxScore", maxScore >= 90)
        assertTrue("Min QoE score should be bounded at 0, was $minScore", minScore >= 0)
        assertTrue("Min QoE score should be very low, was $minScore", minScore <= 20)
    }

    @Test
    fun testProbeScopeAndConfidenceDefaults() {
        val metrics = QualityMetrics()

        assertEquals(ProbeScope.UNKNOWN, metrics.probeScope)
        assertEquals(MeasurementConfidence.LOW, metrics.measurementConfidence)
    }
}
