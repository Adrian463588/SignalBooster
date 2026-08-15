package com.signalbooster.app.testdoubles

import com.signalbooster.app.domain.interfaces.NetworkMonitor
import com.signalbooster.app.domain.models.CapabilityState
import com.signalbooster.app.domain.models.NetworkSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeNetworkMonitor : NetworkMonitor {
    private val _snapshot = MutableStateFlow(NetworkSnapshot.EMPTY)
    override val networkSnapshot: StateFlow<NetworkSnapshot> = _snapshot.asStateFlow()

    private var isMonitoringActive = false

    fun emitSnapshot(snapshot: NetworkSnapshot) {
        _snapshot.value = snapshot
    }

    override suspend fun startMonitoring(): CapabilityState {
        isMonitoringActive = true
        return CapabilityState.RUNNING
    }

    override suspend fun stopMonitoring() {
        isMonitoringActive = false
    }

    override fun isMonitoring(): Boolean = isMonitoringActive
}
