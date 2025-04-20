/*
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.habdroid.core.connection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_FOREGROUND
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN
import android.net.NetworkCapabilities.NET_CAPABILITY_TRUSTED
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_VPN
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkCapabilities.TRANSPORT_WIFI_AWARE
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.openhab.habdroid.core.OpenHabApplication
import org.openhab.habdroid.core.connection.ConnectionManagerHelper.ConnectionType
import org.openhab.habdroid.util.extractWifiSsid
import org.openhab.habdroid.util.getCurrentWifiSsid
import org.openhab.habdroid.util.registerExportedReceiver

typealias ConnectionChangedCallback = () -> Unit

interface ConnectionManagerHelper {
    val currentConnections: List<ConnectionType>
    var changeCallback: ConnectionChangedCallback?

    fun start()
    fun shutdown()

    sealed class ConnectionType(val network: Network, val caps: NetworkCapabilities) {
        class Bluetooth(network: Network, caps: NetworkCapabilities) : ConnectionType(network, caps)

        class Ethernet(network: Network, caps: NetworkCapabilities) : ConnectionType(network, caps)

        class Mobile(network: Network, caps: NetworkCapabilities) : ConnectionType(network, caps)

        class Unknown(network: Network, caps: NetworkCapabilities) : ConnectionType(network, caps)

        class Vpn(network: Network, caps: NetworkCapabilities) : ConnectionType(network, caps)

        class Wifi(network: Network, caps: NetworkCapabilities) : ConnectionType(network, caps) {
            fun fetchSsid(context: Context) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (caps.transportInfo as? WifiInfo)?.ssid?.extractWifiSsid()
            } else {
                context.getCurrentWifiSsid(OpenHabApplication.DATA_ACCESS_TAG_SERVER_DISCOVERY)
            }
        }

        override fun toString() = "ConnectionType(type = ${javaClass.simpleName}, network=$network)"
    }

    companion object {
        fun create(context: Context): ConnectionManagerHelper = when (Build.VERSION.SDK_INT) {
            in 21..25 -> HelperApi21(context)
            in 26..30 -> HelperApi26(context)
            else -> HelperApi31(context)
        }
    }

    private class HelperApi21(context: Context) :
        ChangeCallbackHelperApi21(context),
        ConnectionManagerHelper {
        private val typeHelper = NetworkTypeHelperApi21(context)
        override val currentConnections: List<ConnectionType> get() = typeHelper.currentConnections
    }

    @RequiresApi(26)
    private class HelperApi26(context: Context) :
        ChangeCallbackHelperApi26(context),
        ConnectionManagerHelper {
        private val typeHelper = NetworkTypeHelperApi21(context)
        override val currentConnections: List<ConnectionType> get() = typeHelper.currentConnections
    }

    @RequiresApi(31)
    private class HelperApi31(context: Context) :
        NetworkTypeHelperApi31(context),
        ConnectionManagerHelper

    private open class ChangeCallbackHelperApi21(context: Context) : BroadcastReceiver() {
        var changeCallback: ConnectionChangedCallback? = null
        private val context = context.applicationContext
        private var ignoreNextBroadcast = false

        fun start() {
            @Suppress("DEPRECATION")
            val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
            // Make sure to ignore the initial sticky broadcast, as we're only interested in changes
            ignoreNextBroadcast = context.registerExportedReceiver(null, filter) != null
            context.registerExportedReceiver(this, filter)
        }

        fun shutdown() {
            context.unregisterReceiver(this)
        }

        override fun onReceive(context: Context?, intent: Intent?) {
            if (ignoreNextBroadcast) {
                ignoreNextBroadcast = false
            } else {
                changeCallback?.invoke()
            }
        }
    }

    @RequiresApi(26)
    private open class ChangeCallbackHelperApi26(context: Context) : ConnectivityManager.NetworkCallback() {
        var changeCallback: ConnectionChangedCallback? = null
        private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)!!
        private val lastKnownCaps = HashMap<Network, NetworkCapabilities>()
        private var callbackJob: Job? = null

        fun start() {
            connectivityManager.registerDefaultNetworkCallback(this)
        }

        fun shutdown() {
            callbackJob?.cancel()
            connectivityManager.unregisterNetworkCallback(this)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val knownCaps = lastKnownCaps[network]
            if (knownCaps?.isUsable() != networkCapabilities.isUsable()) {
                scheduleCallback()
            }
            lastKnownCaps[network] = networkCapabilities
            super.onCapabilitiesChanged(network, networkCapabilities)
        }

        override fun onLost(network: Network) {
            val caps = lastKnownCaps.remove(network)
            if (caps?.isUsable() == true) {
                scheduleCallback()
            }
            super.onLost(network)
        }

        override fun onAvailable(network: Network) {
            val caps = connectivityManager.getNetworkCapabilities(network)
            if (caps != null) {
                if (caps.isUsable()) {
                    scheduleCallback()
                }
                lastKnownCaps[network] = caps
            }
            super.onAvailable(network)
        }

        private fun scheduleCallback() {
            callbackJob?.cancel()
            callbackJob = GlobalScope.launch(Dispatchers.Main) {
                delay(500)
                changeCallback?.invoke()
            }
        }
    }

    private class NetworkTypeHelperApi21(context: Context) {
        private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val currentConnections: List<ConnectionType> get() {
            @Suppress("DEPRECATION")
            return connectivityManager.allNetworks
                .map { network -> network to connectivityManager.getNetworkCapabilities(network) }
                .filter { (_, caps) -> caps?.isUsable() == true }
                // nullable cast is safe, since null caps are filtered out above
                .associate { (network, caps) -> network to caps!! }
                .toMap()
                .toConnectionTypeList()
        }
    }

    @RequiresApi(31)
    private open class NetworkTypeHelperApi31(context: Context) :
        ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        var changeCallback: ConnectionChangedCallback? = null
        private val networkCapsMap = mutableMapOf<Network, NetworkCapabilities>()
        private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val currentConnections: List<ConnectionType> get() = networkCapsMap.toConnectionTypeList()
        private var callbackJob: Job? = null

        fun start() {
            val request = NetworkRequest.Builder()
                // Capability set used here needs to stay in sync with isUsable() below
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_RESTRICTED)
                .addCapability(NET_CAPABILITY_FOREGROUND)
                .addCapability(NET_CAPABILITY_NOT_SUSPENDED)
                .removeCapability(NET_CAPABILITY_NOT_VPN)
                .removeCapability(NET_CAPABILITY_TRUSTED)
                .build()
            connectivityManager.registerNetworkCallback(request, this)
        }

        fun shutdown() {
            callbackJob?.cancel()
            connectivityManager.unregisterNetworkCallback(this)
        }

        override fun onLost(network: Network) {
            val caps = networkCapsMap.remove(network)
            if (caps?.isUsable() == true) {
                scheduleCallback()
            }
            super.onLost(network)
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (caps.isUsable()) {
                if (networkCapsMap.put(network, caps) == null) {
                    scheduleCallback()
                }
            } else {
                if (networkCapsMap.remove(network) != null) {
                    scheduleCallback()
                }
            }
        }

        private fun scheduleCallback() {
            callbackJob?.cancel()
            callbackJob = GlobalScope.launch(Dispatchers.Main) {
                delay(500)
                changeCallback?.invoke()
            }
        }
    }
}

fun Map<Network, NetworkCapabilities>.toConnectionTypeList() = map { (network, caps) ->
    when {
        caps.hasTransport(TRANSPORT_VPN) -> ConnectionType.Vpn(network, caps)
        caps.hasTransport(TRANSPORT_WIFI) ||
            caps.hasTransport(TRANSPORT_WIFI_AWARE) -> ConnectionType.Wifi(network, caps)
        caps.hasTransport(TRANSPORT_BLUETOOTH) -> ConnectionType.Bluetooth(network, caps)
        caps.hasTransport(TRANSPORT_ETHERNET) -> ConnectionType.Ethernet(network, caps)
        caps.hasTransport(TRANSPORT_CELLULAR) -> ConnectionType.Mobile(network, caps)
        else -> ConnectionType.Unknown(network, caps)
    }
}

fun NetworkCapabilities.isUsable(): Boolean {
    // Note: checks here need to stay in sync with NetworkTypeHelperApi31
    if (!hasCapability(NET_CAPABILITY_INTERNET) || !hasCapability(NET_CAPABILITY_NOT_RESTRICTED)) {
        return false
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        if (!hasCapability(NET_CAPABILITY_FOREGROUND) || !hasCapability(NET_CAPABILITY_NOT_SUSPENDED)) {
            return false
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        // We don't need validation for e.g. Wifi as we'll use a local connection there,
        // but we definitely need a working internet connection on mobile
        if (hasTransport(TRANSPORT_CELLULAR) && !hasCapability(NET_CAPABILITY_VALIDATED)) {
            return false
        }
    }

    return true
}
