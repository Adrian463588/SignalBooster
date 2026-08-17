package com.signalbooster.app.domain.interfaces

import com.signalbooster.app.domain.models.AllowlistedAction
import com.signalbooster.app.domain.models.CapabilityStatus
import kotlinx.coroutines.flow.Flow

/**
 * Privilege gateway interface per AGENTS.md section 5.
 * Manages Shizuku and root capability detection and access.
 */
interface PrivilegeGateway {
    /**
     * Current capability status.
     */
    val capabilityStatus: Flow<CapabilityStatus>
    
    /**
     * Check if a specific action is available.
     */
    suspend fun isActionAvailable(action: AllowlistedAction): Boolean
}
