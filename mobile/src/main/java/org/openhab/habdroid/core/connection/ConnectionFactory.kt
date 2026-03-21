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

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.NetworkCapabilities
import android.security.KeyChain
import android.security.KeyChainException
import android.util.Log
import androidx.annotation.VisibleForTesting
import de.duenndns.ssl.MemorizingTrustManager
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.CancellationException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509KeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.internal.tls.OkHostnameVerifier
import okhttp3.logging.HttpLoggingInterceptor
import org.openhab.habdroid.model.ServerConfiguration
import org.openhab.habdroid.util.CacheManager
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getActiveServerId
import org.openhab.habdroid.util.getPrimaryServerId
import org.openhab.habdroid.util.getStringOrNull
import org.openhab.habdroid.util.isDebugModeEnabled
import org.openhab.habdroid.util.isDemoModeEnabled

/**
 * A factory class, which is the main entry point to get a Connection to a specific openHAB
 * server. Use this factory class whenever you need to obtain a connection to load additional
 * data from the openHAB server or another supported source
 * (see the constants in [Connection]).
 */
class ConnectionFactory internal constructor(
    private val context: Application,
    private val prefs: SharedPreferences,
    private val secretPrefs: SharedPreferences,
    private val connectionHelper: ConnectionManagerHelper
) : CoroutineScope by CoroutineScope(Dispatchers.Main),
    SharedPreferences.OnSharedPreferenceChangeListener {

    val trustManager: MemorizingTrustManager
    private val httpLogger: HttpLoggingInterceptor
    private var httpClient: OkHttpClient
    private var lastClientCertAlias: String? = null

    private var primaryConn: ServerConnections? = null
    private var activeConn: ServerConnections? = null

    private var needsUpdate = false
    private var pendingChecks = mutableListOf<Job>()
    private var subscriptionCount = 0

    private data class ServerConnections(val local: Connection?, val remote: AbstractConnection?)

    data class ConnectionResult(val connection: Connection?, val failureReason: ConnectionException?)

    private data class ConnectionResultWithSource(val result: ConnectionResult, val connections: ServerConnections?)

    data class CloudConnectionResult(val connection: CloudConnection?, val failureReason: Exception?)

    data class ConnectionInfo(
        val conn: ConnectionResult?,
        val cloud: CloudConnectionResult?,
        val hasLocal: Boolean,
        val hasRemote: Boolean
    )

    private data class StateHolder(
        val intermediate: Boolean,
        val primary: ConnectionResultWithSource?,
        val active: ConnectionResultWithSource?,
        val primaryCloud: CloudConnectionResult?,
        val activeCloud: CloudConnectionResult?
    ) {
        fun isReady(): Boolean {
            if (intermediate) {
                return false
            }
            if (active == null && primary == null && primaryCloud == null && activeCloud == null) {
                return true
            }
            return active != null && primary != null && primaryCloud != null && activeCloud != null
        }

        fun toActiveConnectionInfo() = toInfo(active, activeCloud)
        fun toPrimaryConnectionInfo() = toInfo(primary, primaryCloud)

        private fun toInfo(conn: ConnectionResultWithSource?, cloud: CloudConnectionResult?) = ConnectionInfo(
            conn?.result,
            cloud,
            conn?.connections?.local != null,
            conn?.connections?.remote != null
        )
    }

    private val stateFlow = MutableStateFlow(StateHolder(true, null, null, null, null))

    /**
     * Returns a {@link Flow} that emits information about the current connection to the active server
     */
    val activeFlow get() = stateFlow
        .filter { it.isReady() }
        .map { it.toActiveConnectionInfo() }

    /**
     * Like {@link activeFlow}, but for the primary instead of the active server
     */
    val primaryFlow get() = stateFlow
        .filter { it.isReady() }
        .map { it.toPrimaryConnectionInfo() }

    /**
     * Returns the current information about the connection to the active server
     */
    val currentActive get() = stateFlow.value
        .takeIf { it.isReady() }
        ?.toActiveConnectionInfo()

    /**
     * Like {@link currentActive}, but for the primary instead of the active server
     */
    val currentPrimary get() = stateFlow.value
        .takeIf { it.isReady() }
        ?.toPrimaryConnectionInfo()

    init {
        prefs.registerOnSharedPreferenceChangeListener(this)
        secretPrefs.registerOnSharedPreferenceChangeListener(this)

        httpLogger = HttpLoggingInterceptor()
        updateHttpLoggerSettings()

        trustManager = MemorizingTrustManager(context)
        httpClient = OkHttpClient.Builder()
            .cache(CacheManager.getInstance(context).httpCache)
            .addInterceptor(httpLogger)
            .hostnameVerifier(trustManager.wrapHostnameVerifier(OkHostnameVerifier))
            .build()
        updateHttpClientForClientCert(true)

        // For video widgets
        SSLContext.getInstance("TLS").apply {
            init(null, MemorizingTrustManager.getInstanceList(context), null)
            HttpsURLConnection.setDefaultSSLSocketFactory(socketFactory)
            val mtmHostnameVerifier = MemorizingTrustManager(context)
                .wrapHostnameVerifier(OkHostnameVerifier)
            HttpsURLConnection.setDefaultHostnameVerifier(mtmHostnameVerifier)
        }

        // Relax per-host connection limit, as the default limit (max 5 connections per host) is
        // too low considering SSE connections count against that limit.
        httpClient.dispatcher.maxRequestsPerHost = httpClient.dispatcher.maxRequests

        connectionHelper.changeCallback = {
            if (subscriptionCount == 0) {
                // We're running in background. Clear current state and postpone update for next
                // listener registration.
                updateState(true, active = null, primary = null)
                needsUpdate = true
            } else {
                triggerConnectionUpdateIfNeeded()
            }
        }

        launch {
            stateFlow.subscriptionCount.collect {
                subscriptionCount = it
                if (!triggerConnectionUpdateIfNeededAndPending() && it == 1) {
                    // When coming back from background, re-do connectivity check for
                    // local connections, as the reachability of the local server might have
                    // changed since we went to background
                    val (_, _, active, _, _) = stateFlow.value
                    val result = active?.result
                    val local = result?.connection === active?.connections?.local ||
                        (result?.failureReason as? NoUrlInformationException)?.wouldHaveUsedLocalConnection() == true
                    if (local) {
                        triggerConnectionUpdateIfNeeded()
                    }
                }
            }
        }
    }

    fun start() {
        launch {
            connectionHelper.start()
            updateConnections()
        }
    }

    fun shutdown() {
        connectionHelper.shutdown()
    }

    fun restartNetworkCheck() {
        triggerConnectionUpdateIfNeeded()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == PrefKeys.DEBUG_MESSAGES) {
            updateHttpLoggerSettings()
        }
        val serverId = prefs.getActiveServerId()
        if (key in UPDATE_TRIGGERING_KEYS ||
            CLIENT_CERT_UPDATE_TRIGGERING_PREFIXES.any { prefix -> key == PrefKeys.buildServerKey(serverId, prefix) }
        ) {
            updateHttpClientForClientCert(false)
        }
        if (key in UPDATE_TRIGGERING_KEYS ||
            UPDATE_TRIGGERING_PREFIXES.any { prefix -> key == PrefKeys.buildServerKey(serverId, prefix) }
        ) {
            launch {
                // if the active server changed, we need to invalidate the old connection immediately,
                // as we don't want the user to see old server data while we're validating the new one
                updateConnections(key == PrefKeys.ACTIVE_SERVER_ID)
            }
        }
    }

    @VisibleForTesting
    fun updateConnections(updateStateImmediately: Boolean = false) {
        if (prefs.isDemoModeEnabled()) {
            if (activeConn?.local is DemoConnection) {
                // demo mode already was enabled
                return
            }
            val conn = DemoConnection(httpClient)
            activeConn = ServerConnections(conn, conn)
            primaryConn = activeConn
            val connResult = ConnectionResultWithSource(ConnectionResult(conn, null), activeConn)
            updateState(false, connResult, connResult, CloudConnectionResult(null, null))
        } else {
            val activeServer = prefs.getActiveServerId()
            activeConn = loadServerConnections(activeServer)

            val primaryServer = prefs.getPrimaryServerId()
            primaryConn = if (primaryServer == activeServer) {
                activeConn
            } else {
                loadServerConnections(primaryServer)
            }

            updateState(!updateStateImmediately, null, null, null)
        }
        triggerConnectionUpdateIfNeeded()
    }

    private fun loadServerConnections(serverId: Int): ServerConnections? {
        val config = ServerConfiguration.load(prefs, secretPrefs, serverId) ?: return null
        val local = config.localPath?.let { path -> DefaultConnection(httpClient, Connection.TYPE_LOCAL, path) }
        val remote = config.remotePath?.let { path -> DefaultConnection(httpClient, Connection.TYPE_REMOTE, path) }
        return ServerConnections(local, remote)
    }

    private fun updateHttpLoggerSettings() {
        with(httpLogger) {
            if (prefs.isDebugModeEnabled()) {
                redactHeader("Authorization")
                redactHeader("set-cookie")
                level = HttpLoggingInterceptor.Level.HEADERS
            } else {
                level = HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    private fun updateHttpClientForClientCert(forceUpdate: Boolean) {
        val clientCertAlias = if (prefs.isDemoModeEnabled()) {
            // No client cert in demo mode
            null
        } else {
            prefs.getStringOrNull(PrefKeys.buildServerKey(prefs.getActiveServerId(), PrefKeys.SSL_CLIENT_CERT_PREFIX))
        }
        val keyManagers = if (clientCertAlias != null) {
            arrayOf<KeyManager>(ClientKeyManager(context, clientCertAlias))
        } else {
            null
        }

        // Updating the SSL socket factory is an expensive call;
        // make sure to only do this if really needed.
        if (!forceUpdate) {
            if (clientCertAlias == null && lastClientCertAlias == null) {
                // No change: no client cert at all
                return
            } else if (clientCertAlias != null && clientCertAlias == lastClientCertAlias) {
                // No change: client cert stayed the same
                return
            }
        }

        try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(keyManagers, arrayOf<TrustManager>(trustManager), null)
            httpClient = httpClient.newBuilder()
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                .build()
            lastClientCertAlias = clientCertAlias
        } catch (e: Exception) {
            Log.d(TAG, "Applying certificate trust settings failed", e)
        }
    }

    private fun updateState(
        isIntermediate: Boolean,
        primary: ConnectionResultWithSource? = stateFlow.value.primary,
        active: ConnectionResultWithSource? = stateFlow.value.active,
        primaryCloud: CloudConnectionResult? = stateFlow.value.primaryCloud,
        activeCloud: CloudConnectionResult? = stateFlow.value.activeCloud
    ) {
        val newState = StateHolder(isIntermediate, primary, active, primaryCloud, activeCloud)
        stateFlow.tryEmit(newState)
    }

    private fun triggerConnectionUpdateIfNeededAndPending(): Boolean {
        if (!needsUpdate) {
            return false
        }
        needsUpdate = false
        triggerConnectionUpdateIfNeeded()
        return true
    }

    private fun triggerConnectionUpdateIfNeeded() {
        pendingChecks.forEach { it.cancel() }
        pendingChecks.clear()

        if (activeConn?.local is DemoConnection) {
            return
        }

        val active = activeConn
        val primary = primaryConn

        val updateActive = { result: ConnectionResultWithSource ->
            if (active === primary) {
                updateState(false, active = result, primary = result)
            } else {
                updateState(false, active = result)
            }
        }
        val updateActiveCloud = { result: CloudConnectionResult ->
            if (active === primary) {
                updateState(false, activeCloud = result, primaryCloud = result)
            } else {
                updateState(false, activeCloud = result)
            }
        }

        pendingChecks += launch {
            try {
                val usable = withContext(Dispatchers.IO) {
                    checkAvailableConnection(active?.local, active?.remote)
                }
                updateActive(ConnectionResultWithSource(ConnectionResult(usable, null), active))
            } catch (e: ConnectionException) {
                updateActive(ConnectionResultWithSource(ConnectionResult(null, e), active))
            }
        }

        if (active !== primary) {
            pendingChecks += launch {
                try {
                    val usable = withContext(Dispatchers.IO) {
                        checkAvailableConnection(primary?.local, primary?.remote)
                    }
                    updateState(false, primary = ConnectionResultWithSource(ConnectionResult(usable, null), primary))
                } catch (e: ConnectionException) {
                    updateState(false, primary = ConnectionResultWithSource(ConnectionResult(null, e), primary))
                }
            }
        }

        pendingChecks += launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    active?.remote?.toCloudConnection()
                }
                updateActiveCloud(CloudConnectionResult(result, null))
            } catch (_: CancellationException) {
                // ignored
            } catch (e: Exception) {
                updateActiveCloud(CloudConnectionResult(null, e))
            }
        }

        if (active !== primary) {
            pendingChecks += launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        primary?.remote?.toCloudConnection()
                    }
                    updateState(false, primaryCloud = CloudConnectionResult(result, null))
                } catch (_: CancellationException) {
                    // ignored
                } catch (e: Exception) {
                    updateState(false, primaryCloud = CloudConnectionResult(null, e))
                }
            }
        }
    }

    private suspend fun checkAvailableConnection(local: Connection?, remote: Connection?): Connection {
        val available = connectionHelper.currentConnections.sortedBy { type ->
            when (type) {
                is ConnectionManagerHelper.ConnectionType.Vpn -> 1
                is ConnectionManagerHelper.ConnectionType.Ethernet -> 2
                is ConnectionManagerHelper.ConnectionType.Wifi -> 3
                is ConnectionManagerHelper.ConnectionType.Bluetooth -> 4
                is ConnectionManagerHelper.ConnectionType.Mobile -> 5
                is ConnectionManagerHelper.ConnectionType.Unknown -> 6
            }
        }

        Log.d(TAG, "checkAvailableConnection: found types $available")
        if (available.isEmpty()) {
            Log.e(TAG, "Network is not available")
            throw NetworkNotAvailableException()
        }

        var hasWrongWifi = false
        val restrictedSsids = ServerConfiguration.load(prefs, secretPrefs, prefs.getActiveServerId())?.let { config ->
            if (config.restrictToWifiSsids) config.wifiSsids else null
        }

        if (local != null && local is DefaultConnection) {
            val localCandidates = available.filter { type ->
                when (type) {
                    is ConnectionManagerHelper.ConnectionType.Wifi -> {
                        val ssid = type.fetchSsid(context)
                        when {
                            ssid.isNullOrEmpty() -> true

                            // assume missing permissions
                            restrictedSsids == null -> true

                            // SSID restriction disabled
                            !restrictedSsids.contains(ssid) -> {
                                Log.d(
                                    TAG,
                                    "Skip Wi-Fi ${type.network} (SSID $ssid, server restricted to $restrictedSsids)"
                                )
                                hasWrongWifi = true
                                false
                            }

                            else -> true
                        }
                    }

                    is ConnectionManagerHelper.ConnectionType.Bluetooth -> true

                    is ConnectionManagerHelper.ConnectionType.Ethernet -> true

                    is ConnectionManagerHelper.ConnectionType.Vpn -> true

                    else -> false
                }
            }
            val usableLocalNetwork = localCandidates.firstOrNull { local.isReachableViaNetwork(it.network) }
            if (usableLocalNetwork != null) {
                Log.d(TAG, "Connecting to local URL via $usableLocalNetwork")
                local.network = usableLocalNetwork.network
                local.isMetered = !usableLocalNetwork.caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                return local
            }
        }

        if (available[0] is ConnectionManagerHelper.ConnectionType.Unknown) {
            Log.d(TAG, "Network type ${available[0]} is unknown")
        }

        if (remote != null) {
            // If local URL is not reachable or not configured, use remote URL
            Log.d(TAG, "Connecting to remote URL")
            if (remote is DefaultConnection) {
                remote.isMetered = available.any { type ->
                    !type.caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                }
            }
            return remote
        }

        throw if (hasWrongWifi) WrongWifiException() else NoUrlInformationException(true)
    }

    private class ClientKeyManager(context: Context, private val alias: String?) : X509KeyManager {
        private val context: Context = context.applicationContext

        override fun chooseClientAlias(
            keyTypes: Array<String>?,
            issuers: Array<out Principal>?,
            socket: Socket?
        ): String? {
            Log.d(TAG, "chooseClientAlias - alias: $alias")
            return alias
        }

        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? {
            Log.d(TAG, "chooseServerAlias")
            return null
        }

        override fun getCertificateChain(alias: String?): Array<X509Certificate>? {
            Log.d(TAG, "getCertificateChain", Throwable())
            return try {
                alias?.let { KeyChain.getCertificateChain(context, alias) }
            } catch (e: KeyChainException) {
                Log.e(TAG, "Failed loading certificate chain", e)
                null
            } catch (e: InterruptedException) {
                Log.e(TAG, "Failed loading certificate chain", e)
                null
            }
        }

        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? {
            Log.d(TAG, "getClientAliases")
            return alias?.let { arrayOf(it) }
        }

        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? {
            Log.d(TAG, "getServerAliases")
            return null
        }

        override fun getPrivateKey(alias: String?): PrivateKey? {
            Log.d(TAG, "getPrivateKey")
            return try {
                alias?.let { KeyChain.getPrivateKey(context, alias) }
            } catch (e: KeyChainException) {
                Log.e(TAG, "Failed loading private key", e)
                null
            } catch (e: InterruptedException) {
                Log.e(TAG, "Failed loading private key", e)
                null
            }
        }

        companion object {
            private val TAG = ClientKeyManager::class.java.simpleName
        }
    }

    companion object {
        private val TAG = ConnectionFactory::class.java.simpleName
        private val UPDATE_TRIGGERING_KEYS = listOf(
            PrefKeys.DEMO_MODE,
            PrefKeys.ACTIVE_SERVER_ID,
            PrefKeys.PRIMARY_SERVER_ID
        )
        private val UPDATE_TRIGGERING_PREFIXES = listOf(
            PrefKeys.LOCAL_URL_PREFIX,
            PrefKeys.REMOTE_URL_PREFIX,
            PrefKeys.LOCAL_USERNAME_PREFIX,
            PrefKeys.LOCAL_PASSWORD_PREFIX,
            PrefKeys.REMOTE_USERNAME_PREFIX,
            PrefKeys.REMOTE_PASSWORD_PREFIX,
            PrefKeys.SSL_CLIENT_CERT_PREFIX,
            PrefKeys.WIFI_SSID_PREFIX,
            PrefKeys.RESTRICT_TO_SSID_PREFIX
        )
        private val CLIENT_CERT_UPDATE_TRIGGERING_PREFIXES = listOf(PrefKeys.SSL_CLIENT_CERT_PREFIX)
    }
}
