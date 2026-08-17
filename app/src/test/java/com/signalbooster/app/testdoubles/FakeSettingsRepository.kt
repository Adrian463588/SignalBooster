package com.signalbooster.app.testdoubles

import com.signalbooster.app.domain.interfaces.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSettingsRepository : SettingsRepository {
    private val _probeEndpoint = MutableStateFlow("https://connectivitycheck.gstatic.com/generate_204")
    override val probeEndpoint: Flow<String> = _probeEndpoint.asStateFlow()

    private val _probeTimeoutMs = MutableStateFlow(10000L)
    override val probeTimeoutMs: Flow<Long> = _probeTimeoutMs.asStateFlow()

    private val _probeByteBudget = MutableStateFlow(1048576L)
    override val probeByteBudget: Flow<Long> = _probeByteBudget.asStateFlow()

    private val _isAdaptive = MutableStateFlow(false)
    override val isAdaptiveMonitoringEnabled: Flow<Boolean> = _isAdaptive.asStateFlow()

    var wasWiped: Boolean = false

    override suspend fun setProbeEndpoint(endpoint: String) {
        _probeEndpoint.value = endpoint
    }

    override suspend fun setProbeTimeoutMs(timeoutMs: Long) {
        _probeTimeoutMs.value = timeoutMs
    }

    override suspend fun setProbeByteBudget(byteBudget: Long) {
        _probeByteBudget.value = byteBudget
    }

    override suspend fun setAdaptiveMonitoringEnabled(enabled: Boolean) {
        _isAdaptive.value = enabled
    }

    override suspend fun wipeLocalData(): Boolean {
        wasWiped = true
        _probeEndpoint.value = "https://connectivitycheck.gstatic.com/generate_204"
        _probeTimeoutMs.value = 10000L
        _probeByteBudget.value = 1048576L
        _isAdaptive.value = true
        return true
    }
}
