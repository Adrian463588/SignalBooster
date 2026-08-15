package com.signalbooster.app.testdoubles

import com.signalbooster.app.domain.interfaces.RecoveryCoordinator
import com.signalbooster.app.domain.interfaces.RecoveryResult
import com.signalbooster.app.domain.models.ConfidenceLevel
import com.signalbooster.app.domain.models.NetworkAction
import com.signalbooster.app.domain.models.NetworkRecommendation
import com.signalbooster.app.domain.models.NetworkSnapshot
import com.signalbooster.app.domain.models.QualityMetrics

class FakeRecoveryCoordinator : RecoveryCoordinator {
    var recommendationToReturn: NetworkRecommendation = NetworkRecommendation(
        action = NetworkAction.STAY,
        evidence = emptyList(),
        confidence = ConfidenceLevel.HIGH,
        limitation = null
    )

    var recoveryResultToReturn: RecoveryResult = RecoveryResult(
        success = true,
        actionTaken = "Stay on validated network",
        details = null,
        newState = NetworkSnapshot.EMPTY
    )

    override suspend fun getRecommendation(
        currentState: NetworkSnapshot,
        qualityMetrics: QualityMetrics?
    ): NetworkRecommendation = recommendationToReturn

    override suspend fun attemptRecovery(
        currentState: NetworkSnapshot
    ): RecoveryResult = recoveryResultToReturn
}
