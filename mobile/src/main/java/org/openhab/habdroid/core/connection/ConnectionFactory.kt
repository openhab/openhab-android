/*
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.security.KeyChain
import android.security.KeyChainException
import android.util.Log
import androidx.annotation.VisibleForTesting
import de.duenndns.ssl.MemorizingTrustManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.internal.tls.OkHostnameVerifier
import okhttp3.logging.HttpLoggingInterceptor
import org.openhab.habdroid.core.CloudMessagingHelper
import org.openhab.habdroid.core.connection.exception.ConnectionException
import org.openhab.habdroid.core.connection.exception.ConnectionNotInitializedException
import org.openhab.habdroid.core.connection.exception.NetworkNotAvailableException
import org.openhab.habdroid.core.connection.exception.NetworkNotSupportedException
import org.openhab.habdroid.core.connection.exception.NoUrlInformationException
import org.openhab.habdroid.util.CacheManager
import org.openhab.habdroid.util.Constants
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getString
import org.openhab.habdroid.util.isDebugModeEnabled
import org.openhab.habdroid.util.isDemoModeEnabled
import org.openhab.habdroid.util.toNormalizedUrl
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.HashSet
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509KeyManager

/**
 * A factory class, which is the main entry point to get a Connection to a specific openHAB
 * server. Use this factory class whenever you need to obtain a connection to load additional
 * data from the openHAB server or another supported source
 * (see the constants in [Connection]).
 */
class ConnectionFactory internal constructor(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val connectionHelper: ConnectionManagerHelper
) : CoroutineScope by CoroutineScope(Dispatchers.Main), SharedPreferences.OnSharedPreferenceChangeListener {
    private val trustManager: MemorizingTrustManager
    private val httpLogger: HttpLoggingInterceptor
    private var httpClient: OkHttpClient
    private var lastClientCertAlias: String? = null

    private var localConnection: Connection? = null
    private var remoteConnection: AbstractConnection? = null

    private val listeners = HashSet<UpdateListener>()
    private var needsUpdate: Boolean = false

    private var availableCheck: Job? = null
    private var cloudCheck: Job? = null

    private data class StateHolder constructor(
        val available: Connection?,
        val availableFailureReason: ConnectionException?,
        val cloudInitialized: Boolean,
        val cloud: CloudConnection?
    )
    private val stateChannel = ConflatedBroadcastChannel(StateHolder(null, null, false, null))

    interface UpdateListener {
        fun onAvailableConnectionChanged()
        fun onCloudConnectionChanged(connection: CloudConnection?)
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(this)

        httpLogger = HttpLoggingInterceptor()
        updateHttpLoggerSettings()

        trustManager = MemorizingTrustManager(context)
        httpClient = OkHttpClient.Builder()
            .cache(CacheManager.getInstance(context).httpCache)
            .addInterceptor(httpLogger)
            .hostnameVerifier(trustManager.wrapHostnameVerifier(OkHostnameVerifier.INSTANCE))
            .build()
        updateHttpClientForClientCert(true)

        // Relax per-host connection limit, as the default limit (max 5 connections per host) is
        // too low considering SSE connections count against that limit.
        httpClient.dispatcher().maxRequestsPerHost = httpClient.dispatcher().maxRequests

        connectionHelper.changeCallback = {
            if (listeners.isEmpty()) {
                // We're running in background. Clear current state and postpone update for next
                // listener registration.
                updateState(false, available = null, availableFailureReason = null)
                needsUpdate = true
            } else {
                triggerConnectionUpdateIfNeeded()
            }
        }
    }

    private fun addListenerInternal(l: UpdateListener) {
        if (listeners.add(l)) {
            if (l is Activity) {
                trustManager.bindDisplayActivity(l)
            }
            if (!triggerConnectionUpdateIfNeededAndPending() && localConnection != null && listeners.size == 1) {
                // When coming back from background, re-do connectivity check for
                // local connections, as the reachability of the local server might have
                // changed since we went to background
                val (available, reason, _, _) = stateChannel.value
                val local = available === localConnection ||
                    (reason is NoUrlInformationException && reason.wouldHaveUsedLocalConnection())
                if (local) {
                    triggerConnectionUpdateIfNeeded()
                }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == Constants.PREFERENCE_DEBUG_MESSAGES) {
            updateHttpLoggerSettings()
        }
        if (key in CLIENT_CERT_UPDATE_TRIGGERING_KEYS) {
            updateHttpClientForClientCert(false)
        }
        if (key in UPDATE_TRIGGERING_KEYS) launch {
            updateConnections()
        }
    }

    @VisibleForTesting
    fun updateConnections() {
        if (prefs.isDemoModeEnabled()) {
            if (localConnection is DemoConnection) {
                // demo mode already was enabled
                return
            }
            remoteConnection = DemoConnection(httpClient)
            localConnection = remoteConnection
            updateState(true, available = localConnection, availableFailureReason = null,
                cloudInitialized = true, cloud = null)
        } else {
            localConnection = makeConnection(Connection.TYPE_LOCAL,
                Constants.PREFERENCE_LOCAL_URL,
                Constants.PREFERENCE_LOCAL_USERNAME, Constants.PREFERENCE_LOCAL_PASSWORD)
            remoteConnection = makeConnection(Connection.TYPE_REMOTE,
                Constants.PREFERENCE_REMOTE_URL,
                Constants.PREFERENCE_REMOTE_USERNAME, Constants.PREFERENCE_REMOTE_PASSWORD)

            updateState(false, available = null, availableFailureReason = null, cloudInitialized = false, cloud = null)
            triggerConnectionUpdateIfNeeded()
        }
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
        val clientCertAlias = if (prefs.isDemoModeEnabled()) // No client cert in demo mode
            null else prefs.getString(Constants.PREFERENCE_SSL_CLIENT_CERT, null)
        val keyManagers = if (clientCertAlias != null)
            arrayOf<KeyManager>(ClientKeyManager(context, clientCertAlias)) else null

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
        callListenersOnChange: Boolean,
        available: Connection? = stateChannel.value.available,
        availableFailureReason: ConnectionException? = stateChannel.value.availableFailureReason,
        cloudInitialized: Boolean = stateChannel.value.cloudInitialized,
        cloud: CloudConnection? = stateChannel.value.cloud
    ) {
        val prevState = stateChannel.value
        val newState = StateHolder(available, availableFailureReason, cloudInitialized, cloud)
        stateChannel.offer(newState)
        if (callListenersOnChange) launch {
            if (newState.availableFailureReason != null || prevState.available !== newState.available) {
                listeners.forEach { l -> l.onAvailableConnectionChanged() }
            }
            if (prevState.cloud !== newState.cloud) {
                CloudMessagingHelper.onConnectionUpdated(context, newState.cloud)
                listeners.forEach { l -> l.onCloudConnectionChanged(newState.cloud) }
            }
        }
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
        availableCheck?.cancel()
        cloudCheck?.cancel()

        if (localConnection is DemoConnection) {
            return
        }

        availableCheck = launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    checkAvailableConnection(localConnection, remoteConnection)
                }
                // Check whether the passed connection matches a known one. If not, the
                // connections were updated while the thread was processing and we'll get
                // a new callback.
                if (result != localConnection && result != remoteConnection) {
                    return@launch
                }
                updateState(true, available = result, availableFailureReason = null)
            } catch (e: ConnectionException) {
                updateState(true, available = null, availableFailureReason = e)
            }
        }
        cloudCheck = launch {
            val result = withContext(Dispatchers.IO) {
                remoteConnection?.toCloudConnection()
            }
            updateState(true, cloudInitialized = true, cloud = result)
        }
    }

    private fun makeConnection(
        type: Int,
        urlKey: String,
        userNameKey: String,
        passwordKey: String
    ): AbstractConnection? {
        val url = prefs.getString(urlKey).toNormalizedUrl()
        if (url.isEmpty()) {
            return null
        }
        return DefaultConnection(httpClient, type, url,
            prefs.getString(userNameKey, null),
            prefs.getString(passwordKey, null))
    }

    private fun checkAvailableConnection(local: Connection?, remote: Connection?): Connection {
        val type = connectionHelper.currentConnection
        return when (type) {
            ConnectionManagerHelper.ConnectionType.None -> {
                Log.e(TAG, "Network is not available")
                throw NetworkNotAvailableException()
            }
            // If we are on a mobile network go directly to remote URL from settings
            ConnectionManagerHelper.ConnectionType.Mobile -> {
                remote ?: throw NoUrlInformationException(false)
            }
            // Else if we are on Wifi, Ethernet or VPN network
            ConnectionManagerHelper.ConnectionType.Wifi,
            ConnectionManagerHelper.ConnectionType.Ethernet,
            ConnectionManagerHelper.ConnectionType.Vpn -> when {
                // If local URL is configured and reachable
                local?.checkReachabilityInBackground() == true -> {
                    Log.d(TAG, "Connecting to local URL")
                    local
                }
                remote != null -> {
                    // If local URL is not reachable or not configured, use remote URL
                    Log.d(TAG, "Connecting to remote URL")
                    remote
                }
                else -> throw NoUrlInformationException(true)
            }
            // Else we treat other networks types as unsupported
            else -> {
                Log.e(TAG, "Network type $type is unsupported")
                throw NetworkNotSupportedException()
            }
        }
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
        private val CLIENT_CERT_UPDATE_TRIGGERING_KEYS = listOf(
            Constants.PREFERENCE_DEMO_MODE, Constants.PREFERENCE_SSL_CLIENT_CERT
        )
        private val UPDATE_TRIGGERING_KEYS = listOf(
            Constants.PREFERENCE_LOCAL_URL, Constants.PREFERENCE_REMOTE_URL,
            Constants.PREFERENCE_LOCAL_USERNAME, Constants.PREFERENCE_LOCAL_PASSWORD,
            Constants.PREFERENCE_REMOTE_USERNAME, Constants.PREFERENCE_REMOTE_PASSWORD,
            Constants.PREFERENCE_SSL_CLIENT_CERT, Constants.PREFERENCE_DEMO_MODE
        )

        @VisibleForTesting lateinit var instance: ConnectionFactory

        fun initialize(ctx: Context) {
            instance = ConnectionFactory(ctx, ctx.getPrefs(), ConnectionManagerHelper.create(ctx))
            instance.launch {
                instance.updateConnections()
            }
        }

        @VisibleForTesting
        fun initialize(ctx: Context, settings: SharedPreferences, connectionHelper: ConnectionManagerHelper) {
            instance = ConnectionFactory(ctx, settings, connectionHelper)
        }

        fun shutdown() {
            instance.connectionHelper.shutdown()
        }

        /**
         * Wait for initialization of the factory.
         *
         * This method blocks until all asynchronous work (that is, determination of
         * available and cloud connection) is ready, so that [.getConnection]
         * and [.getUsableConnection] can safely be used.
         */
        suspend fun waitForInitialization() {
            instance.triggerConnectionUpdateIfNeededAndPending()
            val sub = instance.stateChannel.openSubscription()
            do {
                val (available, reason, cloudInitialized, _) = sub.receive()
            } while ((available == null && reason == null) || !cloudInitialized)
        }

        fun addListener(l: UpdateListener) {
            instance.addListenerInternal(l)
        }

        fun removeListener(l: UpdateListener) {
            if (instance.listeners.remove(l) && l is Activity) {
                instance.trustManager.unbindDisplayActivity(l as Activity)
            }
        }

        fun restartNetworkCheck() {
            instance.triggerConnectionUpdateIfNeeded()
        }

        /**
         * Returns any openHAB connection that is most likely to work on the current network. The
         * connections available will be tried in the following order:
         * - TYPE_LOCAL
         * - TYPE_REMOTE
         * - TYPE_CLOUD
         *
         * If there's an issue in configuration or network connectivity, or the connection
         * is not yet initialized, the respective exception is thrown.
         */
        val usableConnection: Connection
            @Throws(ConnectionException::class)
            get() {
                instance.triggerConnectionUpdateIfNeededAndPending()
                val (available, reason, _, _) = instance.stateChannel.value
                if (reason != null) {
                    throw reason
                }
                if (available == null) {
                    throw ConnectionNotInitializedException()
                }
                return available
            }

        /**
         * Like {@link usableConnection}, but returns null instead of throwing in case
         * a connection could not be determined
         */
        val usableConnectionOrNull get() = instance.stateChannel.value.available

        /**
         * Returns the configured local connection, or null if none is configured
         */
        val localConnection get() = instance.localConnection

        /**
         * Returns the configured remote connection, or null if none is configured
         */
        val remoteConnection get() = instance.remoteConnection

        /**
         * Returns the resolved cloud connection.
         * May return null if no remote connection is configured
         * or the remote connection is not usable as cloud connection.
         */
        val cloudConnection get() = instance.stateChannel.value.cloud
    }
}
