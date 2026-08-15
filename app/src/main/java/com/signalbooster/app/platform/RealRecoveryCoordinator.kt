package com.signalbooster.app.platform

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.signalbooster.app.domain.interfaces.RecoveryCoordinator
import com.signalbooster.app.domain.interfaces.RecoveryResult
import com.signalbooster.app.domain.models.ConfidenceLevel
import com.signalbooster.app.domain.models.EvidenceImpact
import com.signalbooster.app.domain.models.MeasurementConfidence
import com.signalbooster.app.domain.models.NetworkAction

import com.signalbooster.app.domain.models.NetworkRecommendation
import com.signalbooster.app.domain.models.NetworkSnapshot
import com.signalbooster.app.domain.models.NetworkValidation
import com.signalbooster.app.domain.models.QualityMetrics
import com.signalbooster.app.domain.models.RecommendationEvidence
import com.signalbooster.app.domain.models.Transport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real platform implementation of RecoveryCoordinator.
 * Generates evidence-based recommendations and coordinates system Settings hand-offs.
 * Complies with AGENTS.md section 5 & PRD FR-03/FR-04.
 */
@Singleton
class RealRecoveryCoordinator @Inject constructor(
    @ApplicationContext private val context: Context
) : RecoveryCoordinator {

    override suspend fun attemptRecovery(currentState: NetworkSnapshot): RecoveryResult = withContext(Dispatchers.Default) {
        when {
            currentState.isCaptivePortal -> {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    data = android.net.Uri.parse("http://connectivitycheck.gstatic.com/generate_204")
                }
                try {
                    context.startActivity(intent)
                    RecoveryResult(
                        success = true,
                        actionTaken = "Opened Captive Portal Authentication",
                        details = "Directed user to captive portal verification web page.",
                        newState = currentState
                    )
                } catch (e: Exception) {
                    RecoveryResult(
                        success = false,
                        actionTaken = "Failed to launch captive portal login",
                        details = e.localizedMessage,
                        newState = currentState
                    )
                }
            }
            currentState.transport == Transport.WIFI && currentState.validation != NetworkValidation.VALIDATED -> {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                    RecoveryResult(
                        success = true,
                        actionTaken = "Opened Wi-Fi Settings",
                        details = "Directed user to Android Wi-Fi settings for manual network selection/reconnect.",
                        newState = currentState
                    )
                } catch (e: Exception) {
                    RecoveryResult(
                        success = false,
                        actionTaken = "Failed to open Wi-Fi settings",
                        details = e.localizedMessage,
                        newState = currentState
                    )
                }
            }
            currentState.transport == Transport.CELLULAR && currentState.validation != NetworkValidation.VALIDATED -> {
                val intent = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                    RecoveryResult(
                        success = true,
                        actionTaken = "Opened Mobile Network Settings",
                        details = "Directed user to cellular network operator settings.",
                        newState = currentState
                    )
                } catch (e: Exception) {
                    RecoveryResult(
                        success = false,
                        actionTaken = "Failed to open cellular settings",
                        details = e.localizedMessage,
                        newState = currentState
                    )
                }
            }
            else -> {
                val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                    RecoveryResult(
                        success = true,
                        actionTaken = "Opened Network & Internet Settings",
                        details = "Handoff to Android network management.",
                        newState = currentState
                    )
                } catch (e: Exception) {
                    RecoveryResult(
                        success = false,
                        actionTaken = "No recovery action available",
                        details = "Current network state requires no automated hand-off.",
                        newState = currentState
                    )
                }
            }
        }
    }

    override suspend fun getRecommendation(
        currentState: NetworkSnapshot,
        qualityMetrics: QualityMetrics?
    ): NetworkRecommendation = withContext(Dispatchers.Default) {
        val evidenceList = mutableListOf<RecommendationEvidence>()

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
            val suggestedAction = when (currentState.transport) {
                Transport.WIFI -> NetworkAction.SWITCH_TO_CELLULAR
                Transport.CELLULAR -> NetworkAction.SWITCH_TO_WIFI
                else -> NetworkAction.RETRY_CONNECTION
            }
            return@withContext NetworkRecommendation(
                action = suggestedAction,
                evidence = evidenceList,
                confidence = ConfidenceLevel.HIGH,
                limitation = "Validation failure confirmed by Android ConnectivityManager."
            )
        }

        // 3. Degraded Quality Metrics (Latency / Loss)
        qualityMetrics?.let { metrics ->
            metrics.latencyRttMs?.let { latency ->
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

            metrics.lossRatio?.let { loss ->
                if (loss > 0.10f) {
                    evidenceList.add(
                        RecommendationEvidence(
                            metric = "Packet Loss",
                            value = "${(loss * 100).toInt()}% (Severe)",
                            impact = EvidenceImpact.NEGATIVE
                        )
                    )
                }
            }
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

        return@withContext when {
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
                if (positiveCount == 0 && evidenceList.isEmpty()) {
                    evidenceList.add(
                        RecommendationEvidence(
                            metric = "Network Transport",
                            value = "${currentState.transport} (${currentState.validation})",
                            impact = EvidenceImpact.POSITIVE
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
                    limitation = "Active connection is currently stable and validated."
                )
            }
        }
    }
}

