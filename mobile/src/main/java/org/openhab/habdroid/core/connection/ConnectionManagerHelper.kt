package org.openhab.habdroid.core.connection

import android.annotation.TargetApi
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.*
import android.os.Build
import kotlinx.coroutines.*

typealias ConnectionChangedCallback = () -> Unit
interface ConnectionManagerHelper {
    val currentConnection: ConnectionType
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

    companion object {
        fun create(context: Context): ConnectionManagerHelper {
            return when (Build.VERSION.SDK_INT) {
                in 14..22 -> HelperApi22(context)
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
        private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        private val lastKnownCaps = HashMap<Network, NetworkCapabilities>()
        private var callbackJob: Job? = null

        init {
            connectivityManager.registerDefaultNetworkCallback(this)
        }

        fun shutdown() {
            callbackJob?.cancel()
            connectivityManager.unregisterNetworkCallback(this)
        }

        override fun onCapabilitiesChanged(network: Network?, networkCapabilities: NetworkCapabilities?) {
            if (network != null) {
                val knownCaps = lastKnownCaps[network]
                if (knownCaps != null && networkCapabilities != null) {
                    if (knownCaps.isUsable() != networkCapabilities.isUsable()) {
                        scheduleCallback()
                    }
                    lastKnownCaps[network] = networkCapabilities
                }
            }
            super.onCapabilitiesChanged(network, networkCapabilities)
        }

        override fun onLost(network: Network?) {
            if (network != null) {
                val caps = lastKnownCaps.remove(network)
                if (caps?.isUsable() == true) {
                    scheduleCallback()
                }
            }
            super.onLost(network)
        }

        override fun onAvailable(network: Network?) {
            if (network != null) {
                val caps = connectivityManager.getNetworkCapabilities(network)
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
                activeConnectionTypes.isEmpty() -> ConnectionType.None
                ConnectivityManager.TYPE_VPN in activeConnectionTypes -> ConnectionType.Vpn
                ConnectivityManager.TYPE_WIFI in activeConnectionTypes -> ConnectionType.Wifi
                ConnectivityManager.TYPE_ETHERNET in activeConnectionTypes -> ConnectionType.Ethernet
                ConnectivityManager.TYPE_MOBILE in activeConnectionTypes -> ConnectionType.Mobile
                else -> ConnectionType.Unknown
            }
        }
    }

    @TargetApi(23)
    @Suppress("DEPRECATION")
    private class NetworkTypeHelperApi23 constructor(context: Context) {
        private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val currentConnection: ConnectionType get() {
            val activeConnectionTypes = connectivityManager.allNetworks
                    .map { network -> connectivityManager.getNetworkInfo(network) }
                    .filter { info -> info.isConnected }
                    .map { info -> info.type }
            return when {
                activeConnectionTypes.isEmpty() -> ConnectionType.None
                ConnectivityManager.TYPE_VPN in activeConnectionTypes -> ConnectionType.Vpn
                ConnectivityManager.TYPE_WIFI in activeConnectionTypes -> ConnectionType.Wifi
                ConnectivityManager.TYPE_ETHERNET in activeConnectionTypes -> ConnectionType.Ethernet
                ConnectivityManager.TYPE_MOBILE in activeConnectionTypes -> ConnectionType.Mobile
                else -> ConnectionType.Unknown
            }
        }
    }

    @TargetApi(26)
    private class NetworkTypeHelperApi26 constructor(context: Context) {
        private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val currentConnection: ConnectionType get() {
            val activeNetworkCaps = connectivityManager.allNetworks
                    .map { network -> connectivityManager.getNetworkCapabilities(network) }
                    .filter { caps -> caps.isUsable() }

            val hasConnectionOver: (transport: Int) -> Boolean = {
                activeNetworkCaps.any { caps -> caps.hasTransport(it) }
            }
            return when {
                activeNetworkCaps.isEmpty() -> ConnectionType.None
                hasConnectionOver(NetworkCapabilities.TRANSPORT_VPN) -> ConnectionType.Vpn
                hasConnectionOver(NetworkCapabilities.TRANSPORT_WIFI) ||
                hasConnectionOver(NetworkCapabilities.TRANSPORT_WIFI_AWARE) -> ConnectionType.Wifi
                hasConnectionOver(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.Ethernet
                hasConnectionOver(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.Mobile
                else -> ConnectionType.Unknown
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
