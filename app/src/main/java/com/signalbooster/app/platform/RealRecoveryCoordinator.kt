package com.signalbooster.app.platform

import com.signalbooster.app.domain.interfaces.RecoveryCoordinator
import com.signalbooster.app.domain.interfaces.RecoveryResult
import com.signalbooster.app.domain.interfaces.RecoveryResultStatus
import com.signalbooster.app.domain.models.ConfidenceLevel
import com.signalbooster.app.domain.models.EvidenceImpact
import com.signalbooster.app.domain.models.MeasurementConfidence
import com.signalbooster.app.domain.models.NetworkAction
import com.signalbooster.app.domain.models.NetworkRecommendation
import com.signalbooster.app.domain.models.NetworkSnapshot
import com.signalbooster.app.domain.models.NetworkValidation
import com.signalbooster.app.domain.models.QualityMetrics
import com.signalbooster.app.domain.models.RecommendationEvidence
import com.signalbooster.app.domain.models.RecoveryState
import com.signalbooster.app.domain.models.SettingsDestination
import com.signalbooster.app.domain.models.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real platform implementation of RecoveryCoordinator.
 * Manages the 9-stage recovery state machine, hysteresis dwell time, and system hand-offs.
 * Complies with AGENTS.md section 5 & PRD FR-03/FR-04.
 */
@Singleton
class RealRecoveryCoordinator @Inject constructor() : RecoveryCoordinator {

    private val _recoveryState = MutableStateFlow(RecoveryState.HEALTHY)
    override val recoveryState: Flow<RecoveryState> = _recoveryState.asStateFlow()

    private var lastRecommendation: NetworkRecommendation? = null
    private var lastRecommendationTime: Long = 0L
    private val hysteresisDwellMillis = 30000L // 30s hysteresis cooldown per Docs1.md

    override suspend fun attemptRecovery(currentState: NetworkSnapshot): RecoveryResult {
        _recoveryState.value = RecoveryState.VERIFYING
        _recoveryState.value = RecoveryState.RECOVERING
        val destination = when {
            currentState.transport == Transport.WIFI -> SettingsDestination.WIFI
            currentState.transport == Transport.CELLULAR -> SettingsDestination.NETWORK_OPERATOR
            else -> SettingsDestination.WIRELESS
        }

        _recoveryState.value = RecoveryState.VALIDATING
        return RecoveryResult(
            status = RecoveryResultStatus.SETTINGS_HANDOFF_READY,
            actionTaken = "Prepared Android Settings hand-off",
            details = "The user must complete network selection or recovery in Android Settings.",
            newState = currentState,
            settingsDestination = destination
        )
    }

    override suspend fun getRecommendation(
        currentState: NetworkSnapshot,
        qualityMetrics: QualityMetrics?
    ): NetworkRecommendation = withContext(Dispatchers.Default) {
        val evidenceList = mutableListOf<RecommendationEvidence>()

        if (currentState.validation == NetworkValidation.VALIDATED &&
            qualityMetrics?.hasMeasuredValues() == true &&
            qualityMetrics.lossRatio?.let { it <= 0.05f } == true
        ) {
            _recoveryState.value = RecoveryState.HEALTHY
        }

        // 1. Captive Portal Evaluation
        if (currentState.isCaptivePortal) {
            evidenceList.add(
                RecommendationEvidence(
                    metric = "Captive Portal",
                    value = "Detected - Web login required",
                    impact = EvidenceImpact.NEGATIVE
                )
            )
            return@withContext NetworkRecommendation(
                action = NetworkAction.OPEN_SETTINGS,
                evidence = evidenceList,
                confidence = ConfidenceLevel.HIGH,
                limitation = "Captive portals require explicit user interaction via browser."
            )
        }

        // 2. Validation Failure
        if (currentState.validation == NetworkValidation.FAILED) {
            evidenceList.add(
                RecommendationEvidence(
                    metric = "Validation",
                    value = "No internet access on active transport",
                    impact = EvidenceImpact.NEGATIVE
                )
            )
            val suggestedAction = NetworkAction.OPEN_SETTINGS
            return@withContext NetworkRecommendation(
                action = suggestedAction,
                evidence = evidenceList,
                confidence = ConfidenceLevel.HIGH,
                limitation = "Validation failure confirmed by Android ConnectivityManager."
            )
        }

        // 3. Degraded Quality Metrics (Latency / Loss / Bufferbloat)
        val latency = qualityMetrics?.latencyRttMs
        if (latency != null) {
            if (latency > 300) {
                evidenceList.add(
                    RecommendationEvidence(
                        metric = "Latency RTT",
                        value = "${latency}ms (High)",
                        impact = EvidenceImpact.NEGATIVE
                    )
                )
            } else if (latency < 100) {
                evidenceList.add(
                    RecommendationEvidence(
                        metric = "Latency RTT",
                        value = "${latency}ms (Good)",
                        impact = EvidenceImpact.POSITIVE
                    )
                )
            }
        }

        val bufferbloatDelta = qualityMetrics?.bufferbloatDeltaMs
        if (bufferbloatDelta != null && bufferbloatDelta > 150) {
            evidenceList.add(
                RecommendationEvidence(
                    metric = "Bufferbloat Delta",
                    value = "+${bufferbloatDelta}ms queue inflation",
                    impact = EvidenceImpact.NEGATIVE
                )
            )
        }

        val loss = qualityMetrics?.lossRatio
        if (loss != null && loss > 0.10f) {
            evidenceList.add(
                RecommendationEvidence(
                    metric = "Packet Loss",
                    value = "${(loss * 100).toInt()}% (Severe)",
                    impact = EvidenceImpact.NEGATIVE
                )
            )
        }


        // 4. Metered Cellular Consideration
        if (currentState.isMetered && currentState.transport == Transport.CELLULAR) {
            evidenceList.add(
                RecommendationEvidence(
                    metric = "Metered Cellular",
                    value = "Active mobile data budget in use",
                    impact = EvidenceImpact.NEUTRAL
                )
            )
        }

        // 5. Final Recommendation synthesis
        val negativeCount = evidenceList.count { it.impact == EvidenceImpact.NEGATIVE }
        val positiveCount = evidenceList.count { it.impact == EvidenceImpact.POSITIVE }

        val freshRecommendation = when {
            negativeCount >= 2 -> {
                NetworkRecommendation(
                    action = NetworkAction.TRY_ALTERNATIVE,
                    evidence = evidenceList,
                    confidence = ConfidenceLevel.MEDIUM,
                    limitation = "Multiple QoE degradation indicators observed on active path."
                )
            }
            currentState.transport == Transport.UNKNOWN -> {
                evidenceList.add(
                    RecommendationEvidence(
                        metric = "Transport",
                        value = "Disconnected / No Route",
                        impact = EvidenceImpact.NEGATIVE
                    )
                )
                NetworkRecommendation(
                    action = NetworkAction.OPEN_SETTINGS,
                    evidence = evidenceList,
                    confidence = ConfidenceLevel.HIGH,
                    limitation = "No active network transport available."
                )
            }
            else -> {
                if (positiveCount == 0 && evidenceList.isEmpty() && qualityMetrics?.hasMeasuredValues() != true) {
                    evidenceList.add(
                        RecommendationEvidence(
                            metric = "Network Transport",
                            value = "${currentState.transport} (${currentState.validation})",
                            impact = EvidenceImpact.NEUTRAL
                        )
                    )
                }
                NetworkRecommendation(
                    action = NetworkAction.STAY,
                    evidence = evidenceList,
                    confidence = when (qualityMetrics?.measurementConfidence) {
                        MeasurementConfidence.HIGH -> ConfidenceLevel.HIGH
                        MeasurementConfidence.MEDIUM -> ConfidenceLevel.MEDIUM
                        else -> ConfidenceLevel.LOW
                    },
                    limitation = if (qualityMetrics?.hasMeasuredValues() != true) {
                        "No QoE probe is available; stability is based on Android validation only."
                    } else {
                        "Active connection is currently stable and validated."
                    }
                )
            }
        }

        val now = System.currentTimeMillis()
        val prev = lastRecommendation
        if (prev != null && prev.action != freshRecommendation.action && currentState.validation == NetworkValidation.VALIDATED) {
            if (now - lastRecommendationTime < hysteresisDwellMillis) {
                // Return stabilized previous recommendation during hysteresis cooldown
                return@withContext prev
            }
        }

        lastRecommendation = freshRecommendation
        lastRecommendationTime = now
        freshRecommendation
    }
}
