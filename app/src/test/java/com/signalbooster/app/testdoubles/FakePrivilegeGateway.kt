package com.signalbooster.app.testdoubles

import com.signalbooster.app.domain.interfaces.PrivilegeGateway
import com.signalbooster.app.domain.models.ActionResult
import com.signalbooster.app.domain.models.AllowlistedAction
import com.signalbooster.app.domain.models.CapabilityStatus
import com.signalbooster.app.domain.models.PrivilegedActionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePrivilegeGateway : PrivilegeGateway {
    private val _status = MutableStateFlow(CapabilityStatus.DEFAULT)
    override val capabilityStatus: StateFlow<CapabilityStatus> = _status.asStateFlow()

    override suspend fun refreshStatus(): CapabilityStatus = _status.value

    override suspend fun executeAllowlistedAction(action: AllowlistedAction): PrivilegedActionResult {
        return PrivilegedActionResult(
            action = action,
            result = ActionResult.SUCCESS,
            details = "Action executed cleanly",
            requiresUserConfirmation = false
        )
    }
}
