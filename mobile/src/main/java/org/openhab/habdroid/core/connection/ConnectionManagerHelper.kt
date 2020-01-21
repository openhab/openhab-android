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
    val currentConnection: ConnectionType
    var changeCallback: ConnectionChangedCallback?

    fun shutdown()

    sealed class ConnectionType {
        class None : ConnectionType()
        class Wifi(val network: Network?) : ConnectionType()
        class Ethernet(val network: Network?) : ConnectionType()
        class Mobile(val network: Network?) : ConnectionType()
        class Unknown(val network: Network?) : ConnectionType()
        class Vpn(val network: Network?): ConnectionType()
    }

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
        override val currentConnection: ConnectionType get() = typeHelper.currentConnection
    }

    private class HelperApi23To25 constructor(context: Context) :
        ConnectionManagerHelper, ChangeCallbackHelperApi25(context) {
        private val typeHelper = NetworkTypeHelperApi23(context)
        override val currentConnection: ConnectionType get() = typeHelper.currentConnection
    }

    private class HelperApi26 constructor(context: Context) :
        ConnectionManagerHelper, ChangeCallbackHelperApi26(context) {
        private val typeHelper = NetworkTypeHelperApi26(context)
        override val currentConnection: ConnectionType get() = typeHelper.currentConnection
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
        val currentConnection: ConnectionType get() {
            val activeConnectionTypes = connectivityManager.allNetworkInfo
                    .filter { info -> info.isConnected }
                    .map { info -> info.type }
            return when {
                activeConnectionTypes.isEmpty() -> ConnectionType.None()
                ConnectivityManager.TYPE_VPN in activeConnectionTypes -> ConnectionType.Vpn(null)
                ConnectivityManager.TYPE_WIFI in activeConnectionTypes -> ConnectionType.Wifi(null)
                ConnectivityManager.TYPE_ETHERNET in activeConnectionTypes -> ConnectionType.Ethernet(null)
                ConnectivityManager.TYPE_MOBILE in activeConnectionTypes -> ConnectionType.Mobile(null)
                else -> ConnectionType.Unknown(null)
            }
        }
    }

    @TargetApi(23)
    @Suppress("DEPRECATION")
    private class NetworkTypeHelperApi23 constructor(context: Context) {
        private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)!!
        val currentConnection: ConnectionType get() {
            val knownConnectionTypesByPrio = listOf(
                ConnectivityManager.TYPE_VPN,
                ConnectivityManager.TYPE_WIFI,
                ConnectivityManager.TYPE_ETHERNET,
                ConnectivityManager.TYPE_MOBILE
            )
            val active = connectivityManager.allNetworks
                    .map { network -> Pair(network, connectivityManager.getNetworkInfo(network)) }
                    .filter { (network, info) -> info?.isConnected == true }
                    .map { (network, info) -> info.type to network }
                    .sortedBy { (type, network) ->
                        val index = knownConnectionTypesByPrio.indexOf(type)
                        if (index < 0) Integer.MAX_VALUE else index
                    }
                    .firstOrNull()

            return when {
                active == null -> ConnectionType.None()
                active.first == ConnectivityManager.TYPE_VPN -> ConnectionType.Vpn(active.second)
                active.first == ConnectivityManager.TYPE_WIFI -> ConnectionType.Wifi(active.second)
                active.first == ConnectivityManager.TYPE_ETHERNET -> ConnectionType.Ethernet(active.second)
                active.first == ConnectivityManager.TYPE_MOBILE -> ConnectionType.Mobile(active.second)
                else -> ConnectionType.Unknown(active.second)
            }
        }
    }

    @TargetApi(26)
    private class NetworkTypeHelperApi26 constructor(context: Context) {
        private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)!!
        val currentConnection: ConnectionType get() {
            val knownTransportsByPrio = listOf(
                NetworkCapabilities.TRANSPORT_VPN,
                NetworkCapabilities.TRANSPORT_WIFI,
                NetworkCapabilities.TRANSPORT_WIFI_AWARE,
                NetworkCapabilities.TRANSPORT_ETHERNET,
                NetworkCapabilities.TRANSPORT_CELLULAR
            )
            fun findMostRelevantTransport(caps: NetworkCapabilities?): Int {
                knownTransportsByPrio.forEachIndexed { index, transport ->
                    if (caps?.hasTransport(transport) == true) {
                        return transport
                    }
                }
                return 0
            }
            val active = connectivityManager.allNetworks
                .map { network -> network to connectivityManager.getNetworkCapabilities(network) }
                .filter { (network, caps) -> caps?.isUsable() == true }
                .map { (network, caps) -> network to findMostRelevantTransport(caps) }
                .filter { (network, transport) -> transport != 0 }
                .sortedBy { (network, transport) -> knownTransportsByPrio.indexOf(transport) }
                .firstOrNull()

            return when {
                active == null -> ConnectionType.None()
                active.second == NetworkCapabilities.TRANSPORT_VPN -> ConnectionType.Vpn(active.first)
                active.second == NetworkCapabilities.TRANSPORT_WIFI ||
                    active.second == NetworkCapabilities.TRANSPORT_WIFI_AWARE -> ConnectionType.Wifi(active.first)
                active.second == NetworkCapabilities.TRANSPORT_ETHERNET -> ConnectionType.Ethernet(active.first)
                active.second == NetworkCapabilities.TRANSPORT_CELLULAR -> ConnectionType.Mobile(active.first)
                else -> ConnectionType.Unknown(active.first)
            }
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
