package com.signalbooster.app.testdoubles

import com.signalbooster.app.domain.interfaces.ProbeType
import com.signalbooster.app.domain.interfaces.QualityProbe
import com.signalbooster.app.domain.models.QualityMetrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeQualityProbe : QualityProbe {
    var defaultMetricsToEmit: QualityMetrics = QualityMetrics(latencyRttMs = 30, lossRatio = 0.0f)
    var wasProbeStopped: Boolean = false

    override suspend fun startProbe(
        probeType: ProbeType,
        timeoutMillis: Long,
        byteBudget: Long
    ): Flow<QualityMetrics> = flow {
        emit(defaultMetricsToEmit)
    }

    override suspend fun stopProbe() {
        wasProbeStopped = true
    }
}
