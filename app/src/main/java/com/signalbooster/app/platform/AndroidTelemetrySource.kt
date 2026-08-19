package com.signalbooster.app.platform

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.signalbooster.app.domain.interfaces.BluetoothScanResults
import com.signalbooster.app.domain.interfaces.CellularMetrics
import com.signalbooster.app.domain.interfaces.RadioTelemetrySource
import com.signalbooster.app.domain.interfaces.WifiMetrics
import com.signalbooster.app.domain.models.BleCorroborationState
import com.signalbooster.app.domain.models.BluetoothConnectionState
import com.signalbooster.app.domain.models.BluetoothPosture
import com.signalbooster.app.domain.models.CellularPosture
import com.signalbooster.app.domain.models.NetworkValidation
import com.signalbooster.app.domain.models.Permission
import com.signalbooster.app.domain.models.PermissionState
import com.signalbooster.app.domain.models.PrivacyPosture
import com.signalbooster.app.domain.models.WifiPosture
import com.signalbooster.app.domain.models.WifiSecurity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android platform implementation of RadioTelemetrySource.
 * Extracts LTE/5G cellular metrics, Wi-Fi link parameters, and Bluetooth radio posture.
 * Complies with least-privilege principles and zero-identifier persistence.
 */
@Singleton
class AndroidTelemetrySource @Inject constructor(
    @ApplicationContext private val context: Context
) : RadioTelemetrySource {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val _cellularMetrics = MutableStateFlow(CellularMetrics())
    override val cellularMetrics: Flow<CellularMetrics> = _cellularMetrics.asStateFlow()

    private val _wifiMetrics = MutableStateFlow(WifiMetrics())
    override val wifiMetrics: Flow<WifiMetrics> = _wifiMetrics.asStateFlow()

    private val _bluetoothScanResults = MutableStateFlow(BluetoothScanResults(deviceCount = null))
    override val bluetoothScanResults: Flow<BluetoothScanResults> = _bluetoothScanResults.asStateFlow()

    private val _privacyPosture = MutableStateFlow(PrivacyPosture.DEFAULT)
    override val privacyPosture: Flow<PrivacyPosture> = _privacyPosture.asStateFlow()

    private var telephonyCallback: TelephonyCallback? = null
    private var isCollecting = false
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override suspend fun startCollection() {
        if (isCollecting) return
        isCollecting = true

        coroutineScope.launch {
            updateWifiMetrics()
            updateBluetoothPosture()
            updatePermissionPosture()
            startCellularTelemetry()
        }
    }

    private fun hasPhonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private var currentDisplayOverride: String? = null

    private fun startCellularTelemetry() {
        val tm = telephonyManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasPhonePermission() && hasLocationPermission()) {
            try {
                abstract class MultiTelephonyCallback : TelephonyCallback(), 
                    TelephonyCallback.CellInfoListener,
                    TelephonyCallback.DisplayInfoListener

                telephonyCallback = object : MultiTelephonyCallback() {
                    override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
                        coroutineScope.launch {
                            processCellInfo(cellInfo)
                        }
                    }

                    override fun onDisplayInfoChanged(telephonyDisplayInfo: android.telephony.TelephonyDisplayInfo) {
                        currentDisplayOverride = when (telephonyDisplayInfo.overrideNetworkType) {
                            android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "5G NSA"
                            android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G+"
                            android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO,
                            android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> "LTE+"
                            else -> null
                        }
                        tm.allCellInfo?.let { processCellInfo(it) }
                    }
                }
                tm.registerTelephonyCallback(context.mainExecutor, telephonyCallback as TelephonyCallback)

                tm.allCellInfo?.let { cellInfo ->
                    processCellInfo(cellInfo)
                }
            } catch (_: SecurityException) {
                fallbackCellularMetrics()
            }
        } else {
            fallbackCellularMetrics()
        }
    }

    private fun processCellInfo(cellInfoList: List<CellInfo>) {
        var rsrp: Int? = null
        var rsrq: Int? = null
        var rssnr: Int? = null
        var ssRsrp: Int? = null
        var ssRsrq: Int? = null
        var ssSinr: Int? = null
        var cqi: Int? = null
        var earfcn: Int? = null
        var nrarfcn: Int? = null
        var bands = mutableListOf<Int>()
        var bandwidthKhz: Int? = null
        var technology: String? = null
        var cellId: Long? = null
        var pci: Int? = null

        for (info in cellInfoList) {
            if (info.isRegistered) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr) {
                    val signalStrength = info.cellSignalStrength as? CellSignalStrengthNr
                    signalStrength?.let {
                        ssRsrp = it.ssRsrp.takeIf { v -> v != CellInfo.UNAVAILABLE }
                        ssRsrq = it.ssRsrq.takeIf { v -> v != CellInfo.UNAVAILABLE }
                        ssSinr = it.ssSinr.takeIf { v -> v != CellInfo.UNAVAILABLE }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            cqi = it.csiCqiTableIndex.takeIf { v -> v != CellInfo.UNAVAILABLE }
                        }
                    }
                    val identity = info.cellIdentity as? CellIdentityNr
                    identity?.let {
                        pci = it.pci.takeIf { v -> v != CellInfo.UNAVAILABLE }
                        cellId = it.nci.takeIf { v -> v != CellInfo.UNAVAILABLE_LONG }
                        nrarfcn = it.nrarfcn.takeIf { v -> v != CellInfo.UNAVAILABLE }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            bands.addAll(it.bands.toList())
                        }
                    }
                    technology = currentDisplayOverride ?: "5G SA"
                } else if (info is CellInfoLte) {
                    val signalStrength = info.cellSignalStrength
                    rsrp = signalStrength.rsrp.takeIf { v -> v != CellInfo.UNAVAILABLE }
                    rsrq = signalStrength.rsrq.takeIf { v -> v != CellInfo.UNAVAILABLE }
                    rssnr = signalStrength.rssnr.takeIf { v -> v != CellInfo.UNAVAILABLE }
                    cqi = signalStrength.cqi.takeIf { v -> v != CellInfo.UNAVAILABLE }
                    
                    val identity = info.cellIdentity
                    pci = identity.pci.takeIf { v -> v != CellInfo.UNAVAILABLE }
                    cellId = identity.ci.takeIf { v -> v != CellInfo.UNAVAILABLE }?.toLong()
                    earfcn = identity.earfcn.takeIf { v -> v != CellInfo.UNAVAILABLE }
                    bandwidthKhz = identity.bandwidth.takeIf { v -> v != CellInfo.UNAVAILABLE }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        bands.addAll(identity.bands.toList())
                    }
                    technology = currentDisplayOverride ?: "LTE"
                }
            }
        }

        val operatorName = telephonyManager?.networkOperatorName?.takeIf { it.isNotBlank() }

        // 3GPP congestion inference is only available when all required
        // measurements are exposed by the platform. Missing values remain
        // unknown instead of being converted into nominal signal values.
        val effectiveRsrp = ssRsrp ?: rsrp
        val effectiveSinr = ssSinr ?: rssnr
        val effectiveRsrq = ssRsrq ?: rsrq
        val hasCongestionEvidence = effectiveRsrp != null && effectiveSinr != null && effectiveRsrq != null
        val isCongested = if (effectiveRsrp != null && effectiveSinr != null && effectiveRsrq != null) {
            effectiveRsrp > -100 && (effectiveSinr < 5 || effectiveRsrq < -14)
        } else {
            false
        }

        val newMetrics = CellularMetrics(
            rsrp = rsrp,
            rsrq = rsrq,
            rssnr = rssnr,
            ssRsrp = ssRsrp,
            ssRsrq = ssRsrq,
            ssSinr = ssSinr,
            cqi = cqi,
            earfcn = earfcn,
            nrarfcn = nrarfcn,
            bands = bands.distinct(),
            bandwidthKhz = bandwidthKhz,
            technology = technology ?: getNetworkTypeLabel(),
            displayNetworkType = currentDisplayOverride ?: technology ?: getNetworkTypeLabel(),
            operator = operatorName,
            cellId = cellId,
            pci = pci,
            isCongested = isCongested,
            hasCongestionEvidence = hasCongestionEvidence
        )
        _cellularMetrics.value = newMetrics
    }

    private fun fallbackCellularMetrics() {
        val operatorName = telephonyManager?.networkOperatorName?.takeIf { it.isNotBlank() }
        _cellularMetrics.value = CellularMetrics(
            technology = getNetworkTypeLabel(),
            displayNetworkType = getNetworkTypeLabel(),
            operator = operatorName
        )
    }

    private fun getNetworkTypeLabel(): String? {
        val tm = telephonyManager ?: return null
        return try {
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                else -> null
            }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun updateWifiMetrics() {
        val wm = wifiManager ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val wifiInfo = wm.connectionInfo
        if (wifiInfo != null && wifiInfo.networkId != -1) {
            val rawSsid = wifiInfo.ssid?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                ?.removePrefix("\"")?.removeSuffix("\"")
            
            val redactedSsid = rawSsid?.let {
                if (it.length > 3) it.take(2) + "***" + it.takeLast(1) else "***"
            }

            _wifiMetrics.value = WifiMetrics(
                rssi = wifiInfo.rssi,
                frequency = wifiInfo.frequency,
                channel = wifiFrequencyToChannel(wifiInfo.frequency),
                linkSpeed = wifiInfo.linkSpeed.takeIf { it > 0 },
                ssid = redactedSsid
            )
        }
    }

    private fun updateBluetoothPosture() {
        val adapter: BluetoothAdapter? = bluetoothManager?.adapter
        val hasConnectPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        val isEnabled = if (hasConnectPermission) {
            try {
                adapter?.isEnabled == true
            } catch (_: SecurityException) {
                false
            }
        } else {
            false
        }
        val isDiscoverable = if (hasConnectPermission) {
            try {
                adapter?.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
            } catch (_: SecurityException) {
                false
            }
        } else {
            false
        }
        val isConnected = try {
            hasConnectPermission && isEnabled && (
                adapter?.getProfileConnectionState(android.bluetooth.BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED ||
                adapter?.getProfileConnectionState(android.bluetooth.BluetoothProfile.A2DP) == BluetoothAdapter.STATE_CONNECTED
            )
        } catch (_: SecurityException) {
            false
        }

        _bluetoothScanResults.value = BluetoothScanResults(
            deviceCount = null,
            isEnabled = isEnabled,
            isDiscoverable = isDiscoverable,
            isConnected = isConnected
        )

        val btPosture = BluetoothPosture(
            isEnabled = isEnabled,
            isDiscoverable = isDiscoverable,
            connectionState = when {
                isConnected -> BluetoothConnectionState.CONNECTED
                isEnabled -> BluetoothConnectionState.DISCONNECTED
                else -> BluetoothConnectionState.DISCONNECTED
            }
        )

        _privacyPosture.value = _privacyPosture.value.copy(
            bluetoothState = btPosture,
            timestamp = Instant.now()
        )
    }

    private fun updatePermissionPosture() {
        val granted = mutableSetOf<Permission>()
        val required = setOf(
            Permission.INTERNET,
            Permission.ACCESS_NETWORK_STATE,
            Permission.ACCESS_WIFI_STATE,
            Permission.READ_PHONE_STATE,
            Permission.ACCESS_FINE_LOCATION,
            Permission.POST_NOTIFICATIONS
        )

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED) {
            granted.add(Permission.INTERNET)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED) {
            granted.add(Permission.ACCESS_NETWORK_STATE)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {
            granted.add(Permission.ACCESS_WIFI_STATE)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            granted.add(Permission.READ_PHONE_STATE)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            granted.add(Permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            granted.add(Permission.POST_NOTIFICATIONS)
        }

        _privacyPosture.value = _privacyPosture.value.copy(
            permissionState = PermissionState(
                requiredPermissions = required,
                grantedPermissions = granted,
                lastCheckTime = Instant.now()
            )
        )
    }

    private fun wifiFrequencyToChannel(frequency: Int): Int {
        return when {
            frequency in 2412..2484 -> (frequency - 2412) / 5 + 1 // 2.4 GHz
            frequency in 5170..5825 -> (frequency - 5170) / 5 + 34 // 5 GHz
            frequency in 5945..7125 -> (frequency - 5940) / 5     // 6 GHz (Wi-Fi 6E)
            else -> 0
        }
    }

    override suspend fun stopCollection() {
        isCollecting = false

        telephonyCallback?.let { callback ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyManager?.unregisterTelephonyCallback(callback)
            }
            telephonyCallback = null
        }

        _cellularMetrics.value = CellularMetrics()
        _wifiMetrics.value = WifiMetrics()
    }
}
