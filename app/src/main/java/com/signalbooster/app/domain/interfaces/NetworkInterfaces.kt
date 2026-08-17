package com.signalbooster.app.domain.interfaces

import com.signalbooster.app.domain.models.CapabilityState
import com.signalbooster.app.domain.models.NetworkRecommendation
import com.signalbooster.app.domain.models.NetworkSnapshot
import com.signalbooster.app.domain.models.QualityMetrics
import com.signalbooster.app.domain.models.RecoveryState
import kotlinx.coroutines.flow.Flow

/**
 * Network monitoring interface.
 * Observes network changes and provides state snapshots.
 */
interface NetworkMonitor {
    /**
     * Current network snapshot observable.
     */
    val networkSnapshot: Flow<NetworkSnapshot>
    
    /**
     * Start monitoring network changes.
     * @return CapabilityState indicating if monitoring started successfully.
     */
    suspend fun startMonitoring(): CapabilityState
    
    /**
     * Stop monitoring network changes.
     */
    suspend fun stopMonitoring()
    
    /**
     * Check if monitoring is active.
     */
    fun isMonitoring(): Boolean
}

/**
 * Quality probing interface.
 * Runs bounded network probes to measure quality metrics.
 */
interface QualityProbe {
    /**
     * Start a bounded quality probe.
     * @param probeType Type of probe to run
     * @param timeoutMillis Maximum time for the probe
     * @param byteBudget Maximum bytes to use for throughput test
     * @return Flow of quality metrics as they become available
     */
    suspend fun startProbe(
        probeType: ProbeType,
        timeoutMillis: Long = 10000L,
        byteBudget: Long = 1048576L // 1 MB
    ): Flow<QualityMetrics>
    
    /**
     * Stop any ongoing probe.
     */
    suspend fun stopProbe()
}

enum class ProbeType {
    DNS,        // DNS resolution latency
    TCP,        // TCP connection latency
    HTTP,       // HTTP request/response latency & status
    THROUGHPUT, // Download speed (bounded by byte budget)
    GATEWAY     // Local hop / default router reachability
}

/**
 * Recovery coordination interface.
 * Manages network recovery actions and recommendations.
 */
interface RecoveryCoordinator {
    /**
     * Observable recovery state machine state.
     */
    val recoveryState: Flow<RecoveryState>

    /**
     * Attempt network recovery based on current state.
     * @param currentState Current network snapshot
     * @return Result of recovery attempt
     */
    suspend fun attemptRecovery(currentState: NetworkSnapshot): RecoveryResult
    
    /**
     * Get recommended action for current network state.
     */
    suspend fun getRecommendation(currentState: NetworkSnapshot, qualityMetrics: QualityMetrics? = null): NetworkRecommendation

    /**
     * Invalidate internal DNS resolution caches and reset active socket connections.
     */
    suspend fun invalidateDnsAndSockets(): Boolean
}

data class RecoveryResult(
    val success: Boolean,
    val actionTaken: String,
    val details: String? = null,
    val newState: NetworkSnapshot
)