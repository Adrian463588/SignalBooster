package com.signalbooster.app.domain.interfaces

import kotlinx.coroutines.flow.Flow

/**
 * Settings and local baseline persistence interface.
 * Handles data budget, probe endpoints, and local data wipe.
 */
interface SettingsRepository {
    /**
     * Probe timeout in milliseconds.
     */
    val probeTimeoutMs: Flow<Long>

    /**
     * Probe byte budget in bytes.
     */
    val probeByteBudget: Flow<Long>

    /**
     * Disclosed probe target HTTP/HTTPS endpoint.
     */
    val probeEndpoint: Flow<String>

    /**
     * Adaptive monitoring enabled.
     */
    val isAdaptiveMonitoringEnabled: Flow<Boolean>

    /**
     * Update probe timeout.
     */
    suspend fun setProbeTimeoutMs(timeoutMs: Long)

    /**
     * Update probe byte budget.
     */
    suspend fun setProbeByteBudget(byteBudget: Long)

    /**
     * Update probe endpoint from allowlisted options.
     */
    suspend fun setProbeEndpoint(endpoint: String)

    /**
     * Set adaptive monitoring flag.
     */
    suspend fun setAdaptiveMonitoringEnabled(enabled: Boolean)

    /**
     * Wipe all local data, baselines, and reset preferences to defaults.
     * Complies with PRD FR-11 Data Privacy requirement.
     */
    suspend fun wipeLocalData(): Boolean
}
