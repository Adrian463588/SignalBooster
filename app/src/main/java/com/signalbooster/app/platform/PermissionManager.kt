package com.signalbooster.app.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.signalbooster.app.domain.models.Permission
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Android runtime permissions according to AGENTS.md Section 7.
 * Follows least privilege principle and requests permissions at point of use.
 */
@Singleton
class PermissionManager @Inject constructor(
    private val context: Context
) {
    
    /**
     * Check if a specific permission is granted.
     */
    fun isPermissionGranted(permission: Permission): Boolean {
        return when (permission) {
            Permission.INTERNET -> true // Granted by manifest
            Permission.ACCESS_NETWORK_STATE -> hasPermission(Manifest.permission.ACCESS_NETWORK_STATE)
            Permission.ACCESS_WIFI_STATE -> hasPermission(Manifest.permission.ACCESS_WIFI_STATE)
            Permission.CHANGE_WIFI_STATE -> hasPermission(Manifest.permission.CHANGE_WIFI_STATE)
            Permission.NEARBY_WIFI_DEVICES -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                hasPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                true
            }
            Permission.READ_PHONE_STATE -> hasPermission(Manifest.permission.READ_PHONE_STATE)

            Permission.ACCESS_COARSE_LOCATION -> hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            Permission.ACCESS_FINE_LOCATION -> hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            Permission.BLUETOOTH -> hasPermission(Manifest.permission.BLUETOOTH)
            Permission.BLUETOOTH_ADMIN -> hasPermission(Manifest.permission.BLUETOOTH_ADMIN)
            Permission.BLUETOOTH_SCAN -> hasPermission(Manifest.permission.BLUETOOTH_SCAN)
            Permission.BLUETOOTH_ADVERTISE -> hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
            Permission.BLUETOOTH_CONNECT -> hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
            Permission.POST_NOTIFICATIONS -> hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    
    /**
     * Get required permissions for a specific feature.
     * Follows PRD.md Section 10 permission matrix.
     */
    fun getRequiredPermissionsForFeature(feature: Feature): Set<Permission> {
        return when (feature) {
            Feature.INTERNET_PROBES -> setOf(Permission.INTERNET, Permission.ACCESS_NETWORK_STATE)
            Feature.WIFI_STATE -> setOf(Permission.ACCESS_WIFI_STATE)
            Feature.WIFI_SUGGESTIONS -> setOf(Permission.CHANGE_WIFI_STATE, Permission.ACCESS_WIFI_STATE)
            Feature.CELLULAR_METRICS -> setOf(Permission.READ_PHONE_STATE, Permission.ACCESS_FINE_LOCATION)
            Feature.BLE_CORROBORATION -> setOf(Permission.BLUETOOTH_SCAN, Permission.BLUETOOTH_ADVERTISE)
            Feature.NOTIFICATIONS -> setOf(Permission.POST_NOTIFICATIONS)
            Feature.ACOUSTIC_OUTPUT -> emptySet() // No permission required for audio output
            Feature.NETWORK_MONITORING -> setOf(Permission.ACCESS_NETWORK_STATE)
        }
    }
    
    /**
     * Get missing permissions for a feature.
     */
    fun getMissingPermissionsForFeature(feature: Feature): Set<Permission> {
        val required = getRequiredPermissionsForFeature(feature)
        return required.filterNot { isPermissionGranted(it) }.toSet()
    }
    
    /**
     * Get all granted permissions.
     */
    fun getGrantedPermissions(): Set<Permission> {
        return Permission.entries.filter { isPermissionGranted(it) }.toSet()
    }
    
    /**
     * Get human-readable explanation for why a permission is needed.
     */
    fun getPermissionExplanation(permission: Permission): String {
        return when (permission) {
            Permission.ACCESS_FINE_LOCATION -> 
                "Android treats cellular tower information as location-sensitive. " +
                "Your location is processed on the device and not uploaded."
            Permission.READ_PHONE_STATE -> 
                "Required to read cellular signal strength and network operator information."
            Permission.BLUETOOTH_SCAN -> 
                "Required for optional BLE corroboration to detect nearby devices anonymously."
            Permission.BLUETOOTH_ADVERTISE -> 
                "Required for optional BLE corroboration to advertise anonymous presence."
            Permission.POST_NOTIFICATIONS -> 
                "Required to show foreground service status and important alerts."
            Permission.ACCESS_WIFI_STATE -> 
                "Required to read Wi-Fi connection information and signal strength."
            Permission.CHANGE_WIFI_STATE -> 
                "Required to suggest Wi-Fi networks for reconnection."
            else -> "Required for network diagnostics and connectivity monitoring."
        }
    }
    
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

enum class Feature {
    INTERNET_PROBES,
    WIFI_STATE,
    WIFI_SUGGESTIONS,
    CELLULAR_METRICS,
    BLE_CORROBORATION,
    NOTIFICATIONS,
    ACOUSTIC_OUTPUT,
    NETWORK_MONITORING
}