package com.signalbooster.app.domain.models

import java.time.Instant

/**
 * Capability and Shizuku models per AGENTS.md section 5.
 */

enum class CapabilityState {
    AVAILABLE,
    UNAVAILABLE,
    PERMISSION_REQUIRED,
    CAPABILITY_UNAVAILABLE,
    INSUFFICIENT_DATA,
    RUNNING,
    STOPPED,
    FAILED
}

enum class CapabilityTier {
    NORMAL_API,
    SHIZUKU,
    ROOT_SUI,
    UNKNOWN
}

data class CapabilityStatus(
    val tier: CapabilityTier,
    val state: CapabilityState,
    val actions: Set<AllowlistedAction>,
    val deviceSupport: DeviceSupport,
    val binderStatus: BinderStatus,
    val timestamp: Instant = Instant.now()
) {
    companion object {
        val DEFAULT = CapabilityStatus(
            tier = CapabilityTier.NORMAL_API,
            state = CapabilityState.AVAILABLE,
            actions = setOf(
                AllowlistedAction.NETWORK_MONITOR_START,
                AllowlistedAction.NETWORK_MONITOR_STOP,
                AllowlistedAction.NETWORK_PROBE_START,
                AllowlistedAction.NETWORK_PROBE_STOP,
                AllowlistedAction.ACOUSTIC_MASKING_START,
                AllowlistedAction.ACOUSTIC_MASKING_STOP,
                AllowlistedAction.DATA_WIPE
            ),
            deviceSupport = DeviceSupport(
                isShizukuInstalled = false,
                isShizukuRunning = false,
                isShizukuAuthorized = false,
                isRootAvailable = false,
                isSuiAvailable = false
            ),
            binderStatus = BinderStatus.UNKNOWN
        )
    }
}

enum class AllowlistedAction {
    NETWORK_MONITOR_START,
    NETWORK_MONITOR_STOP,
    NETWORK_PROBE_START,
    NETWORK_PROBE_STOP,
    WI_FI_SUGGESTION_ADD,
    WI_FI_SUGGESTION_REMOVE,
    WI_FI_RECONNECT,
    CELLULAR_METRICS_READ,
    ACOUSTIC_MASKING_START,
    ACOUSTIC_MASKING_STOP,
    DATA_WIPE
}

data class DeviceSupport(
    val isShizukuInstalled: Boolean,
    val isShizukuRunning: Boolean,
    val isShizukuAuthorized: Boolean,
    val isRootAvailable: Boolean,
    val isSuiAvailable: Boolean,
    val oem: String? = null,
    val model: String? = null,
    val androidVersion: Int = 0
)

enum class BinderStatus {
    ALIVE,
    DEAD,
    UNKNOWN
}

data class PrivilegedActionResult(
    val action: AllowlistedAction,
    val result: ActionResult,
    val details: String? = null,
    val requiresUserConfirmation: Boolean = true,
    val isReversible: Boolean = false,
    val timestamp: Instant = Instant.now()
)

enum class ActionResult {
    SUCCESS,
    FAILED,
    CAPABILITY_UNAVAILABLE,
    PERMISSION_DENIED,
    BINDER_DEAD,
    UNSUPPORTED_OEM,
    USER_CANCELLED
}

/**
 * Recovery action models per FR-03.
 */
data class RecoveryAction(
    val action: RecoveryActionType,
    val parameters: Map<String, String> = emptyMap(),
    val timeoutMillis: Long = 10000L,
    val maxRetries: Int = 3
)

enum class RecoveryActionType {
    RETRY_CONNECTION,
    WI_FI_NETWORK_SUGGESTION,
    OPEN_SETTINGS,
    SWITCH_TRANSPORT
}