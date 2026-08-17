package com.signalbooster.app.privilege

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.signalbooster.app.domain.interfaces.PrivilegeGateway
import com.signalbooster.app.domain.models.AllowlistedAction
import com.signalbooster.app.domain.models.BinderStatus
import com.signalbooster.app.domain.models.CapabilityState
import com.signalbooster.app.domain.models.CapabilityStatus
import com.signalbooster.app.domain.models.CapabilityTier
import com.signalbooster.app.domain.models.DeviceSupport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports verified capability facts only.
 *
 * No Shizuku provider dependency is bundled in this application, so a package
 * being installed is not treated as a live or authorized Binder. RAT switching
 * and recovery remain Settings-only and no privileged action is executed here.
 */
@Singleton
class RealPrivilegeGateway @Inject constructor(
    @ApplicationContext private val context: Context
) : PrivilegeGateway {

    private val _capabilityStatus = MutableStateFlow(CapabilityStatus.DEFAULT)
    override val capabilityStatus: Flow<CapabilityStatus> = _capabilityStatus.asStateFlow()

    init {
        refreshCapabilityStatus()
    }

    private fun refreshCapabilityStatus() {
        val shizukuInstalled = isPackageInstalled("moe.shizuku.privileged.api") ||
            isPackageInstalled("rikka.shizuku")
        val modifyPhoneStateGranted = context.checkSelfPermission(Manifest.permission.MODIFY_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        val bluetoothPrivilegedGranted = context.checkSelfPermission(Manifest.permission.BLUETOOTH_PRIVILEGED) ==
            PackageManager.PERMISSION_GRANTED
        val protectedPermissionsAvailable = modifyPhoneStateGranted && bluetoothPrivilegedGranted

        _capabilityStatus.value = CapabilityStatus(
            tier = CapabilityTier.NORMAL_API,
            state = if (protectedPermissionsAvailable) {
                CapabilityState.AVAILABLE
            } else {
                CapabilityState.CAPABILITY_UNAVAILABLE
            },
            actions = emptySet(),
            deviceSupport = DeviceSupport(
                isShizukuInstalled = shizukuInstalled,
                isShizukuRunning = false,
                isShizukuAuthorized = false,
                isRootAvailable = false,
                isSuiAvailable = false,
                oem = Build.MANUFACTURER,
                model = Build.MODEL,
                androidVersion = Build.VERSION.SDK_INT,
                isModifyPhoneStateGranted = modifyPhoneStateGranted,
                isBluetoothPrivilegedGranted = bluetoothPrivilegedGranted
            ),
            binderStatus = BinderStatus.UNKNOWN,
            timestamp = Instant.now()
        )
    }

    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    override suspend fun isActionAvailable(action: AllowlistedAction): Boolean =
        _capabilityStatus.value.actions.contains(action)
}
