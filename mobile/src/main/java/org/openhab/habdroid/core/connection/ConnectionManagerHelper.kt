/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

typealias ConnectionChangedCallback = () -> Unit
interface ConnectionManagerHelper {
    val currentConnection: ConnectionResult
    var changeCallback: ConnectionChangedCallback?

    fun shutdown()

    enum class ConnectionType {
        None,
        Wifi,
        Ethernet,
        Mobile,
        Unknown,
        Vpn
    }

    data class ConnectionResult(val type: ConnectionType, val network: Network?)

    companion object {
        fun create(context: Context): ConnectionManagerHelper {
            return when (Build.VERSION.SDK_INT) {
                in 19..22 -> HelperApi22(context)
                in 23..25 -> HelperApi23To25(context)
                else -> HelperApi26(context)
            }
        }
    }

    private class HelperApi22 constructor(context: Context) :
        ConnectionManagerHelper, ChangeCallbackHelperApi25(context) {
        private val typeHelper = NetworkTypeHelperApi22(context)
        override val currentConnection: ConnectionResult get() = typeHelper.currentConnection
    }

    private class HelperApi23To25 constructor(context: Context) :
        ConnectionManagerHelper, ChangeCallbackHelperApi25(context) {
        private val typeHelper = NetworkTypeHelperApi23(context)
        override val currentConnection: ConnectionResult get() = typeHelper.currentConnection
    }

    private class HelperApi26 constructor(context: Context) :
        ConnectionManagerHelper, ChangeCallbackHelperApi26(context) {
        private val typeHelper = NetworkTypeHelperApi26(context)
        override val currentConnection: ConnectionResult get() = typeHelper.currentConnection
    }

    private open class ChangeCallbackHelperApi25 constructor(context: Context) : BroadcastReceiver() {
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

    @Suppress("DEPRECATION")
    private class NetworkTypeHelperApi22 constructor(context: Context) {
        private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val currentConnection: ConnectionResult get() {
            val activeConnectionTypes = connectivityManager.allNetworkInfo
                    .filter { info -> info.isConnected }
                    .map { info -> info.type }
            val type = when {
                activeConnectionTypes.isEmpty() -> ConnectionType.None
                ConnectivityManager.TYPE_VPN in activeConnectionTypes -> ConnectionType.Vpn
                ConnectivityManager.TYPE_WIFI in activeConnectionTypes -> ConnectionType.Wifi
                ConnectivityManager.TYPE_ETHERNET in activeConnectionTypes -> ConnectionType.Ethernet
                ConnectivityManager.TYPE_MOBILE in activeConnectionTypes -> ConnectionType.Mobile
                else -> ConnectionType.Unknown
            }
            return ConnectionResult(type, null)
        }
    }

    @TargetApi(23)
    @Suppress("DEPRECATION")
    private class NetworkTypeHelperApi23 constructor(context: Context) {
        private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)!!
        val currentConnection: ConnectionResult get() {
            val activeConnections = connectivityManager.allNetworks
                    .map { network -> Pair(network, connectivityManager.getNetworkInfo(network)) }
                    .filter { (network, info) -> info?.isConnected == true }

            val knownTypes= listOf(
                ConnectivityManager.TYPE_VPN to ConnectionType.Vpn,
                ConnectivityManager.TYPE_WIFI to ConnectionType.Wifi,
                ConnectivityManager.TYPE_ETHERNET to ConnectionType.Ethernet,
                ConnectivityManager.TYPE_MOBILE to ConnectionType.Mobile
            )

            if (activeConnections.isEmpty()) {
                return ConnectionResult(ConnectionType.None, null)
            }
            for ((cmType, type) in knownTypes) {
                val entry = activeConnections.firstOrNull { (network, info) ->
                    info?.type == cmType
                }
                if (entry != null) {
                    return ConnectionResult(type, entry.first)
                }
            }
            return ConnectionResult(ConnectionType.Unknown, null)
        }
    }

    @TargetApi(26)
    private class NetworkTypeHelperApi26 constructor(context: Context) {
        private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)!!
        val currentConnection: ConnectionResult get() {
            val activeNetworksWithCaps = connectivityManager.allNetworks
                    .map { network -> Pair(network, connectivityManager.getNetworkCapabilities(network)) }
                    .filter { (network, caps) -> caps?.isUsable() == true }

            val knownTransports = listOf(
                NetworkCapabilities.TRANSPORT_VPN to ConnectionType.Vpn,
                NetworkCapabilities.TRANSPORT_WIFI to ConnectionType.Wifi,
                NetworkCapabilities.TRANSPORT_WIFI_AWARE to ConnectionType.Wifi,
                NetworkCapabilities.TRANSPORT_ETHERNET to ConnectionType.Ethernet,
                NetworkCapabilities.TRANSPORT_CELLULAR to ConnectionType.Mobile
            )
            if (activeNetworksWithCaps.isEmpty()) {
                return ConnectionResult(ConnectionType.None, null)
            }
            for ((transport, type) in knownTransports) {
                val entry = activeNetworksWithCaps.firstOrNull { (network, caps) ->
                    caps?.hasTransport(transport) == true
                }
                if (entry != null) {
                    return ConnectionResult(type, entry.first)
                }
            }
            return ConnectionResult(ConnectionType.Unknown, null)
        }
    }
}

@TargetApi(26)
fun NetworkCapabilities.isUsable(): Boolean {
    if (!hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
        !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
    ) {
        return false
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        if (!hasCapability(NetworkCapabilities.NET_CAPABILITY_FOREGROUND) ||
            !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
        ) {
            return false
        }
    }
    // We don't need validation for e.g. Wifi as we'll use a local connection there,
    // but we definitely need a working internet connection on mobile
    if (hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
        !hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    ) {
        return false
    }

    return true
}
