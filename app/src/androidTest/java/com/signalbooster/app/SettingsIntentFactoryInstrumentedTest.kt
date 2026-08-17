package com.signalbooster.app

import android.provider.Settings
import android.telephony.SubscriptionManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.signalbooster.app.domain.models.SettingsDestination
import com.signalbooster.app.platform.SettingsIntentFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsIntentFactoryInstrumentedTest {

    @Test
    fun everySupportedDestinationUsesOnlyApprovedSettingsAction() {
        val subscriptionId = 42

        val operator = SettingsIntentFactory.buildIntent(SettingsDestination.NETWORK_OPERATOR, subscriptionId)
        val wifi = SettingsIntentFactory.buildIntent(SettingsDestination.WIFI, subscriptionId)
        val wireless = SettingsIntentFactory.buildIntent(SettingsDestination.WIRELESS, subscriptionId)

        assertEquals(Settings.ACTION_NETWORK_OPERATOR_SETTINGS, operator.action)
        assertEquals(Settings.ACTION_WIFI_SETTINGS, wifi.action)
        assertEquals(Settings.ACTION_WIRELESS_SETTINGS, wireless.action)
        assertTrue(operator.hasExtra(Settings.EXTRA_SUB_ID))
        assertTrue(wifi.hasExtra(Settings.EXTRA_SUB_ID))
        assertTrue(wireless.hasExtra(Settings.EXTRA_SUB_ID))
        assertEquals(subscriptionId, operator.getIntExtra(Settings.EXTRA_SUB_ID, -1))
        assertEquals(subscriptionId, wifi.getIntExtra(Settings.EXTRA_SUB_ID, -1))
        assertEquals(subscriptionId, wireless.getIntExtra(Settings.EXTRA_SUB_ID, -1))
    }

    @Test
    fun invalidSubscriptionDoesNotCreateFabricatedExtra() {
        val intent = SettingsIntentFactory.buildIntent(
            SettingsDestination.NETWORK_OPERATOR,
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        )

        assertFalse(intent.hasExtra(Settings.EXTRA_SUB_ID))
    }
}
