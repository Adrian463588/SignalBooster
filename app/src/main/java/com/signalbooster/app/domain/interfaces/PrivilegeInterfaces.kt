package com.signalbooster.app.domain.interfaces

import com.signalbooster.app.domain.models.AllowlistedAction
import com.signalbooster.app.domain.models.CapabilityStatus
import com.signalbooster.app.domain.models.PrivilegedActionResult
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
     * Execute an allowlisted privileged action.
     * @param action The action to execute
     * @param parameters Action parameters (validated)
     * @return Result of the action execution
     */
    suspend fun executePrivilegedAction(
        action: AllowlistedAction,
        parameters: Map<String, String> = emptyMap()
    ): PrivilegedActionResult
    
    /**
     * Check if a specific action is available.
     */
    suspend fun isActionAvailable(action: AllowlistedAction): Boolean
    
    /**
     * Get human-readable description of what an action will do.
     */
    suspend fun getActionDescription(action: AllowlistedAction): String
    
    /**
     * Revoke all granted permissions and capabilities.
     */
    suspend fun revokeAllCapabilities()
}