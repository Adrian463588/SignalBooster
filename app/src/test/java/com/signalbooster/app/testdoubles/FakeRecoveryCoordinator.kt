package com.signalbooster.app.testdoubles

import com.signalbooster.app.domain.interfaces.RecoveryCoordinator
import com.signalbooster.app.domain.interfaces.RecoveryResult
import com.signalbooster.app.domain.interfaces.RecoveryResultStatus
import com.signalbooster.app.domain.models.ConfidenceLevel
import com.signalbooster.app.domain.models.NetworkAction
import com.signalbooster.app.domain.models.NetworkRecommendation
import com.signalbooster.app.domain.models.NetworkSnapshot
import com.signalbooster.app.domain.models.QualityMetrics

import com.signalbooster.app.domain.models.RecoveryState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeRecoveryCoordinator : RecoveryCoordinator {
    private val _recoveryState = MutableStateFlow(RecoveryState.HEALTHY)
    override val recoveryState: Flow<RecoveryState> = _recoveryState.asStateFlow()

    var recommendationToReturn: NetworkRecommendation = NetworkRecommendation(
        action = NetworkAction.STAY,
        evidence = emptyList(),
        confidence = ConfidenceLevel.HIGH,
        limitation = null
    )

    var recoveryResultToReturn: RecoveryResult = RecoveryResult(
        status = RecoveryResultStatus.NO_ACTION_REQUIRED,
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
