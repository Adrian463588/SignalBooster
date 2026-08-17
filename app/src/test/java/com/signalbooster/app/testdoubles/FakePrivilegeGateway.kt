package com.signalbooster.app.testdoubles

import com.signalbooster.app.domain.interfaces.PrivilegeGateway
import com.signalbooster.app.domain.models.AllowlistedAction
import com.signalbooster.app.domain.models.CapabilityStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePrivilegeGateway : PrivilegeGateway {
    private val _status = MutableStateFlow(CapabilityStatus.DEFAULT)
    override val capabilityStatus: Flow<CapabilityStatus> = _status.asStateFlow()

    override suspend fun isActionAvailable(action: AllowlistedAction): Boolean =
        _status.value.actions.contains(action)
}
