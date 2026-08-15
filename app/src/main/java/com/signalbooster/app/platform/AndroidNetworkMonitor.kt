package com.signalbooster.app.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.signalbooster.app.domain.interfaces.NetworkMonitor
import com.signalbooster.app.domain.models.CapabilityState
import com.signalbooster.app.domain.models.DataAvailability
import com.signalbooster.app.domain.models.NetworkSnapshot
import com.signalbooster.app.domain.models.NetworkValidation
import com.signalbooster.app.domain.models.Transport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android platform implementation of NetworkMonitor using ConnectivityManager.
 * Follows Clean Architecture by translating Android framework objects to domain models.
 */
@Singleton
class AndroidNetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) : NetworkMonitor {
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    
    private val _networkSnapshot = MutableStateFlow(NetworkSnapshot.EMPTY)
    override val networkSnapshot: Flow<NetworkSnapshot> = _networkSnapshot.asStateFlow()
    
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isMonitoringActive = false
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    override suspend fun startMonitoring(): CapabilityState {
        val cm = connectivityManager ?: return CapabilityState.CAPABILITY_UNAVAILABLE

        return try {
            if (isMonitoringActive) {
                return CapabilityState.RUNNING
            }
            
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .build()
            
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    coroutineScope.launch {
                        updateNetworkSnapshot(network)
                    }
                }
                
                override fun onLost(network: Network) {
                    coroutineScope.launch {
                        val activeNetwork = cm.activeNetwork
                        if (activeNetwork == null || activeNetwork == network) {
                            _networkSnapshot.value = NetworkSnapshot.EMPTY.copy(
                                availability = DataAvailability.UNAVAILABLE
                            )
                        } else {
                            updateNetworkSnapshot(activeNetwork)
                        }
                    }
                }
                
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    coroutineScope.launch {
                        updateNetworkSnapshot(network, networkCapabilities)
                    }
                }
                
                override fun onLinkPropertiesChanged(network: Network, linkProperties: android.net.LinkProperties) {
                    coroutineScope.launch {
                        updateNetworkSnapshot(network)
                    }
                }
            }
            
            cm.registerNetworkCallback(networkRequest, networkCallback!!)
            isMonitoringActive = true
            
            // Query initial state
            cm.activeNetwork?.let { network ->
                val capabilities = cm.getNetworkCapabilities(network)
                updateNetworkSnapshot(network, capabilities)
            } ?: run {
                _networkSnapshot.value = NetworkSnapshot.EMPTY.copy(
                    availability = DataAvailability.UNAVAILABLE
                )
            }
            
            CapabilityState.RUNNING
        } catch (e: SecurityException) {
            CapabilityState.PERMISSION_REQUIRED
        } catch (e: Exception) {
            CapabilityState.FAILED
        }
    }
    
    override suspend fun stopMonitoring() {
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
            networkCallback = null
        }
        isMonitoringActive = false
        _networkSnapshot.value = NetworkSnapshot.EMPTY.copy(
            availability = DataAvailability.STOPPED
        )
    }
    
    override fun isMonitoring(): Boolean = isMonitoringActive
    
    private fun updateNetworkSnapshot(
        network: Network,
        capabilities: NetworkCapabilities? = null
    ) {
        val cm = connectivityManager ?: return
        val actualCapabilities = capabilities ?: cm.getNetworkCapabilities(network)
        
        val transport = when {
            actualCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> Transport.WIFI
            actualCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> Transport.CELLULAR
            actualCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> Transport.ETHERNET
            actualCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> Transport.VPN
            else -> Transport.UNKNOWN
        }
        
        val validation = when {
            actualCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true -> 
                NetworkValidation.VALIDATED
            actualCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true -> 
                NetworkValidation.CAPTIVE_PORTAL
            actualCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true ->
                NetworkValidation.UNVALIDATED
            else -> NetworkValidation.UNKNOWN
        }
        
        val isMetered = actualCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true
        val isVpnActive = actualCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val isCaptivePortal = validation == NetworkValidation.CAPTIVE_PORTAL
        
        _networkSnapshot.value = NetworkSnapshot(
            transport = transport,
            validation = validation,
            isMetered = isMetered,
            isCaptivePortal = isCaptivePortal,
            isVpnActive = isVpnActive,
            availability = DataAvailability.AVAILABLE
        )
    }
}