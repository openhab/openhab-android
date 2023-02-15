/*
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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

import android.annotation.TargetApi
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
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_VPN
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkCapabilities.TRANSPORT_WIFI_AWARE
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

typealias ConnectionChangedCallback = () -> Unit
interface ConnectionManagerHelper {
    val currentConnections: List<ConnectionType>
    var changeCallback: ConnectionChangedCallback?

    fun shutdown()

    sealed class ConnectionType constructor(val network: Network, val caps: NetworkCapabilities) {
        class Bluetooth(network: Network, caps: NetworkCapabilities) : ConnectionType(network, caps)
        class Ethernet(network: Network, caps: NetworkCapabilities) : ConnectionType(network, caps)
        class Mobile(network: Network, caps: NetworkCapabilities) : ConnectionType(network, caps)
        class Unknown(network: Network, caps: NetworkCapabilities) : ConnectionType(network, caps)
        class Vpn(network: Network, caps: NetworkCapabilities) : ConnectionType(network, caps)
        class Wifi(network: Network, caps: NetworkCapabilities) : ConnectionType(network, caps)

        override fun toString(): String {
            return "ConnectionType(type = ${javaClass.simpleName}, network=$network)"
        }
    }

    companion object {
        fun create(context: Context): ConnectionManagerHelper {
            return when (Build.VERSION.SDK_INT) {
                in 21..25 -> HelperApi21(context)
                else -> HelperApi26(context)
            }
        }
    }

    private class HelperApi21 constructor(context: Context) :
        ConnectionManagerHelper, ChangeCallbackHelperApi21(context) {
        private val typeHelper = NetworkTypeHelper(context)
        override val currentConnections: List<ConnectionType> get() = typeHelper.currentConnections
    }

    @TargetApi(26)
    private class HelperApi26 constructor(context: Context) :
        ConnectionManagerHelper, ChangeCallbackHelperApi26(context) {
        private val typeHelper = NetworkTypeHelper(context)
        override val currentConnections: List<ConnectionType> get() = typeHelper.currentConnections
    }

    private open class ChangeCallbackHelperApi21 constructor(context: Context) : BroadcastReceiver() {
        var changeCallback: ConnectionChangedCallback? = null
        private val context = context.applicationContext
        private var ignoreNextBroadcast: Boolean

        init {
            @Suppress("DEPRECATION")
            val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
            // Make sure to ignore the initial sticky broadcast, as we're only interested in changes
            ignoreNextBroadcast = context.registerReceiver(null, filter) != null
            context.registerReceiver(this, filter)
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

    @TargetApi(26)
    private open class ChangeCallbackHelperApi26 constructor(context: Context) : ConnectivityManager.NetworkCallback() {
        var changeCallback: ConnectionChangedCallback? = null
        private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)!!
        private val lastKnownCaps = HashMap<Network, NetworkCapabilities>()
        private var callbackJob: Job? = null

        init {
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

    private class NetworkTypeHelper constructor(context: Context) {
        private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val currentConnections: List<ConnectionType> get() {
            // TODO: Replace deprecated function
            @Suppress("DEPRECATION")
            return connectivityManager.allNetworks
                .map { network -> network to connectivityManager.getNetworkCapabilities(network) }
                .filter { (_, caps) -> caps?.isUsable() == true }
                // nullable cast is safe, since null caps are filtered out above
                .map { (network, caps) -> network to caps!! }
                .map { (network, caps) ->
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
        }
    }
}

fun NetworkCapabilities.isUsable(): Boolean {
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
