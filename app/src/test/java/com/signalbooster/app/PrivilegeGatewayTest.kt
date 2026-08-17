package com.signalbooster.app

import com.signalbooster.app.domain.models.BinderStatus
import com.signalbooster.app.domain.models.CapabilityState
import com.signalbooster.app.domain.models.CapabilityStatus
import com.signalbooster.app.domain.models.CapabilityTier
import com.signalbooster.app.domain.models.DeviceSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegeGatewayTest {

    @Test
    fun testDefaultCapabilityStatusIsNormalApi() {
        val defaultStatus = CapabilityStatus.DEFAULT

        assertEquals(CapabilityTier.NORMAL_API, defaultStatus.tier)
        assertEquals(CapabilityState.CAPABILITY_UNAVAILABLE, defaultStatus.state)
        assertFalse(defaultStatus.deviceSupport.isShizukuInstalled)
        assertFalse(defaultStatus.deviceSupport.isRootAvailable)
        assertFalse(defaultStatus.deviceSupport.isModifyPhoneStateGranted)
        assertFalse(defaultStatus.deviceSupport.isBluetoothPrivilegedGranted)
        assertEquals(BinderStatus.UNKNOWN, defaultStatus.binderStatus)
    }

    @Test
    fun testUnverifiedActionsAreNotAdvertised() {
        val actions = CapabilityStatus.DEFAULT.actions

        assertTrue(actions.isEmpty())
    }
}
