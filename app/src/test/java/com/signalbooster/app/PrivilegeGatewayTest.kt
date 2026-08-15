package com.signalbooster.app

import com.signalbooster.app.domain.models.ActionResult
import com.signalbooster.app.domain.models.AllowlistedAction
import com.signalbooster.app.domain.models.BinderStatus
import com.signalbooster.app.domain.models.CapabilityState
import com.signalbooster.app.domain.models.CapabilityStatus
import com.signalbooster.app.domain.models.CapabilityTier
import com.signalbooster.app.domain.models.DeviceSupport
import com.signalbooster.app.domain.models.PrivilegedActionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegeGatewayTest {

    @Test
    fun testDefaultCapabilityStatusIsNormalApi() {
        val defaultStatus = CapabilityStatus.DEFAULT

        assertEquals(CapabilityTier.NORMAL_API, defaultStatus.tier)
        assertEquals(CapabilityState.AVAILABLE, defaultStatus.state)
        assertFalse(defaultStatus.deviceSupport.isShizukuInstalled)
        assertFalse(defaultStatus.deviceSupport.isRootAvailable)
        assertEquals(BinderStatus.UNKNOWN, defaultStatus.binderStatus)
    }

    @Test
    fun testAllowlistedActionsContainCoreActions() {
        val actions = CapabilityStatus.DEFAULT.actions

        assertTrue(actions.contains(AllowlistedAction.NETWORK_MONITOR_START))
        assertTrue(actions.contains(AllowlistedAction.NETWORK_MONITOR_STOP))
        assertTrue(actions.contains(AllowlistedAction.NETWORK_PROBE_START))
        assertTrue(actions.contains(AllowlistedAction.ACOUSTIC_MASKING_START))
        assertTrue(actions.contains(AllowlistedAction.DATA_WIPE))
    }

    @Test
    fun testPrivilegedActionResultFailClosedModel() {
        val failedResult = PrivilegedActionResult(
            action = AllowlistedAction.WI_FI_RECONNECT,
            result = ActionResult.CAPABILITY_UNAVAILABLE,
            details = "Shizuku binder is not active or unauthorized",
            requiresUserConfirmation = true
        )

        assertEquals(ActionResult.CAPABILITY_UNAVAILABLE, failedResult.result)
        assertTrue(failedResult.requiresUserConfirmation)
        assertFalse(failedResult.isReversible)
    }
}
