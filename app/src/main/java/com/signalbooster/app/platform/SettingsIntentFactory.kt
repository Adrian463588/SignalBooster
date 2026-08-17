package com.signalbooster.app.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.telephony.SubscriptionManager
import com.signalbooster.app.domain.models.SettingsDestination
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and launches the supported Android Settings hand-offs.
 *
 * This is intentionally a concrete class: there is one platform implementation,
 * and the shared mapping prevents recovery and UI from drifting apart. It does
 * not change radio state itself. Android Settings remains authoritative.
 */
@Singleton
class SettingsIntentFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Creates a Settings intent for the current active data subscription.
     */
    fun createIntent(destination: SettingsDestination): Intent = buildIntent(
        destination = destination,
        subscriptionId = activeDataSubscriptionId()
    )

    /**
     * Launches a resolvable Settings intent or returns an explicit failure.
     */
    fun launch(destination: SettingsDestination): SettingsLaunchResult {
        val intent = createIntent(destination).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
            return SettingsLaunchResult.Unavailable(
                destination = destination,
                reason = "No Android Settings activity can handle ${intent.action}."
            )
        }

        return try {
            context.startActivity(intent)
            SettingsLaunchResult.Started(destination)
        } catch (_: ActivityNotFoundException) {
            SettingsLaunchResult.Failed(
                destination = destination,
                reason = "Android Settings activity disappeared before launch."
            )
        } catch (exception: SecurityException) {
            SettingsLaunchResult.Failed(
                destination = destination,
                reason = "Android denied the Settings hand-off: ${exception.javaClass.simpleName}."
            )
        }
    }

    private fun activeDataSubscriptionId(): Int? {
        val subscriptionId = SubscriptionManager.getActiveDataSubscriptionId()
        return subscriptionId.takeUnless { it == SubscriptionManager.INVALID_SUBSCRIPTION_ID }
    }

    companion object {
        /**
         * Pure intent construction used by regression tests.
         */
        fun buildIntent(destination: SettingsDestination, subscriptionId: Int?): Intent =
            Intent(destination.action).apply {
                subscriptionId
                    ?.takeUnless { it == SubscriptionManager.INVALID_SUBSCRIPTION_ID }
                    ?.let { putExtra(Settings.EXTRA_SUB_ID, it) }
            }

        private val SettingsDestination.action: String
            get() = when (this) {
                SettingsDestination.NETWORK_OPERATOR -> Settings.ACTION_NETWORK_OPERATOR_SETTINGS
                SettingsDestination.WIFI -> Settings.ACTION_WIFI_SETTINGS
                SettingsDestination.WIRELESS -> Settings.ACTION_WIRELESS_SETTINGS
            }
    }
}

sealed interface SettingsLaunchResult {
    val destination: SettingsDestination

    data class Started(override val destination: SettingsDestination) : SettingsLaunchResult

    data class Unavailable(
        override val destination: SettingsDestination,
        val reason: String
    ) : SettingsLaunchResult

    data class Failed(
        override val destination: SettingsDestination,
        val reason: String
    ) : SettingsLaunchResult
}
