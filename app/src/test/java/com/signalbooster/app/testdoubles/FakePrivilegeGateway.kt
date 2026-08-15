package com.signalbooster.app.testdoubles

import com.signalbooster.app.domain.interfaces.PrivilegeGateway
import com.signalbooster.app.domain.models.ActionResult
import com.signalbooster.app.domain.models.AllowlistedAction
import com.signalbooster.app.domain.models.CapabilityStatus
import com.signalbooster.app.domain.models.PrivilegedActionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePrivilegeGateway : PrivilegeGateway {
    private val _status = MutableStateFlow(CapabilityStatus.DEFAULT)
    override val capabilityStatus: Flow<CapabilityStatus> = _status.asStateFlow()

    override suspend fun executePrivilegedAction(
        action: AllowlistedAction,
        parameters: Map<String, String>
    ): PrivilegedActionResult {
        return PrivilegedActionResult(
            action = action,
            result = ActionResult.SUCCESS,
            details = "Action executed cleanly",
            requiresUserConfirmation = false
        )
    }

    override suspend fun isActionAvailable(action: AllowlistedAction): Boolean = true

    override suspend fun getActionDescription(action: AllowlistedAction): String = "Fake execution of $action"

    override suspend fun revokeAllCapabilities() {
        _status.value = CapabilityStatus.DEFAULT
    }
}
