package org.openhab.habdroid.core.connection

import android.app.Activity
import android.content.*
import android.net.ConnectivityManager
import android.security.KeyChain
import android.security.KeyChainException
import android.util.Log
import androidx.annotation.VisibleForTesting
import de.duenndns.ssl.MemorizingTrustManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import okhttp3.OkHttpClient
import okhttp3.internal.tls.OkHostnameVerifier
import okhttp3.logging.HttpLoggingInterceptor
import org.openhab.habdroid.core.CloudMessagingHelper
import org.openhab.habdroid.core.connection.exception.ConnectionException
import org.openhab.habdroid.core.connection.exception.NetworkNotAvailableException
import org.openhab.habdroid.core.connection.exception.NetworkNotSupportedException
import org.openhab.habdroid.core.connection.exception.NoUrlInformationException
import org.openhab.habdroid.util.CacheManager
import org.openhab.habdroid.util.Constants
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.toNormalizedUrl
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509KeyManager
import kotlin.coroutines.CoroutineContext

/**
 * A factory class, which is the main entry point to get a Connection to a specific openHAB
 * server. Use this factory class whenever you need to obtain a connection to load additional
 * data from the openHAB server or another supported source
 * (see the constants in [Connection]).
 */
class ConnectionFactory internal constructor(private val context: Context, private val prefs: SharedPreferences) :
        CoroutineScope, BroadcastReceiver(), SharedPreferences.OnSharedPreferenceChangeListener {
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main
    private val trustManager: MemorizingTrustManager
    private val httpLogger: HttpLoggingInterceptor
    private var httpClient: OkHttpClient
    private var lastClientCertAlias: String? = null

    private var localConnection: Connection? = null
    private var remoteConnection: AbstractConnection? = null
    private var cloudConnection: CloudConnection? = null
    private var availableConnection: Connection? = null
    private var connectionFailureReason: ConnectionException? = null

    private val listeners = HashSet<UpdateListener>()
    private var needsUpdate: Boolean = false
    private var ignoreNextConnectivityChange: Boolean = false
    private var availableInitialized: Boolean = false
    private var cloudInitialized: Boolean = false
    private val initDoneChannel = BroadcastChannel<Boolean>(1)

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

        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        // Make sure to ignore the initial sticky broadcast, as we're only interested in changes
        ignoreNextConnectivityChange = context.registerReceiver(null, filter) != null
        context.registerReceiver(this, filter)
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
                val reason = connectionFailureReason
                val local = availableConnection === localConnection
                        || (reason is NoUrlInformationException && reason.wouldHaveUsedLocalConnection())
                if (local) launch {
                    triggerConnectionUpdateIfNeeded()
                }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (Constants.PREFERENCE_DEBUG_MESSAGES == key) {
            updateHttpLoggerSettings()
        }
        if (CLIENT_CERT_UPDATE_TRIGGERING_KEYS.contains(key)) {
            updateHttpClientForClientCert(false)
        }
        if (UPDATE_TRIGGERING_KEYS.contains(key)) {
            launch {
                updateConnections()
            }
        }
    }

    private fun getConnectionInternal(connectionType: Int): Connection? {
        return when (connectionType) {
            Connection.TYPE_LOCAL -> localConnection
            Connection.TYPE_REMOTE -> remoteConnection
            Connection.TYPE_CLOUD -> cloudConnection
            else -> throw IllegalArgumentException("Invalid Connection type requested.")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (ignoreNextConnectivityChange) {
            ignoreNextConnectivityChange = false
            return
        }
        if (listeners.isEmpty()) {
            // We're running in background. Clear current state and postpone update for next
            // listener registration.
            availableConnection = null
            connectionFailureReason = null
            availableInitialized = false
            needsUpdate = true
        } else {
            launch {
                triggerConnectionUpdateIfNeeded()
            }
        }
    }

    private fun updateAvailableConnection(c: Connection?, failureReason: ConnectionException?): Boolean {
        when {
            failureReason != null -> {
                connectionFailureReason = failureReason
                availableConnection = null
            }
            c === availableConnection -> return false
            else -> {
                connectionFailureReason = null
                availableConnection = c
            }
        }
        return true
    }

    @VisibleForTesting
    suspend fun updateConnections() {
        if (prefs.getBoolean(Constants.PREFERENCE_DEMOMODE, false)) {
            remoteConnection = DemoConnection(httpClient)
            localConnection = remoteConnection
            handleAvailableCheckDone(localConnection, null)
            handleCloudCheckDone(null)
        } else {
            localConnection = makeConnection(Connection.TYPE_LOCAL,
                    Constants.PREFERENCE_LOCAL_URL,
                    Constants.PREFERENCE_LOCAL_USERNAME, Constants.PREFERENCE_LOCAL_PASSWORD)
            remoteConnection = makeConnection(Connection.TYPE_REMOTE,
                    Constants.PREFERENCE_REMOTE_URL,
                    Constants.PREFERENCE_REMOTE_USERNAME, Constants.PREFERENCE_REMOTE_PASSWORD)

            availableInitialized = false
            cloudInitialized = false
            availableConnection = null
            cloudConnection = null
            connectionFailureReason = null
            triggerConnectionUpdateIfNeeded()
        }
    }

    private fun updateHttpLoggerSettings() {
        with (httpLogger) {
            if (prefs.getBoolean(Constants.PREFERENCE_DEBUG_MESSAGES, false)) {
                redactHeader("Authorization")
                redactHeader("set-cookie")
                level = HttpLoggingInterceptor.Level.HEADERS
            } else {
                level = HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    private fun updateHttpClientForClientCert(forceUpdate: Boolean) {
        val clientCertAlias = if (prefs.getBoolean(Constants.PREFERENCE_DEMOMODE, false))
            null else prefs.getString(Constants.PREFERENCE_SSLCLIENTCERT, null)// No client cert in demo mode
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


    private fun handleAvailableCheckDone(available: Connection?, failureReason: ConnectionException?) {
        // Check whether the passed connection matches a known one. If not, the
        // connections were updated while the thread was processing and we'll get
        // a new callback.
        if (failureReason != null
                || available === localConnection
                || available === remoteConnection) {
            if (updateAvailableConnection(available, failureReason)) {
                listeners.forEach { l -> l.onAvailableConnectionChanged() }
            }
            availableInitialized = true
            initDoneChannel.offer(availableInitialized && cloudInitialized)
        }
    }

    private fun handleCloudCheckDone(connection: CloudConnection?) {
        if (connection != cloudConnection) {
            cloudConnection = connection
            CloudMessagingHelper.onConnectionUpdated(context, connection)
            listeners.forEach { l -> l.onCloudConnectionChanged(connection) }
        }
        cloudInitialized = true
        initDoneChannel.offer(availableInitialized && cloudInitialized)
    }

    private fun triggerConnectionUpdateIfNeededAndPending(): Boolean {
        if (!needsUpdate) {
            return false
        }
        needsUpdate = false
        launch {
            triggerConnectionUpdateIfNeeded()
        }
        return true
    }

    private suspend fun triggerConnectionUpdateIfNeeded() {
        if (localConnection is DemoConnection) {
            return
        }

        val availableCheck = if (availableInitialized)
            null else checkAvailableConnectionAsync(localConnection, remoteConnection)
        val cloudCheck = if (cloudInitialized)
            null else checkCloudConnectionAsync(remoteConnection)

        if (availableCheck != null) {
            try {
                handleAvailableCheckDone(availableCheck.await(), null)
            } catch (e: ConnectionException) {
                handleAvailableCheckDone(null, e)
            }
        }
        if (cloudCheck != null) {
            handleCloudCheckDone(cloudCheck.await())
        }
    }

    private fun makeConnection(type: Int, urlKey: String,
                               userNameKey: String, passwordKey: String): AbstractConnection? {
        val url = prefs.getString(urlKey, "").toNormalizedUrl()
        if (url.isEmpty()) {
            return null
        }
        return DefaultConnection(httpClient, type, url,
                prefs.getString(userNameKey, null),
                prefs.getString(passwordKey, null))
    }

    private fun checkAvailableConnectionAsync(local: Connection?, remote: Connection?) = GlobalScope.async(Dispatchers.Default) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val info = connectivityManager.activeNetworkInfo

        if (info == null || !info.isConnected) {
            Log.e(TAG, "Network is not available")
            throw NetworkNotAvailableException()
        }

        when {
            // If we are on a mobile network go directly to remote URL from settings
            info.type == ConnectivityManager.TYPE_MOBILE -> {
                if (remote == null) {
                    throw NoUrlInformationException(false)
                }
                remote
            }
            // Else if we are on Wifi, Ethernet, WIMAX or VPN network
            LOCAL_CONNECTION_TYPES.contains(info.type) -> {
                // If local URL is configured and reachable
                if (local != null && local.checkReachabilityInBackground()) {
                    Log.d(TAG, "Connecting to local URL")
                    local
                } else if (remote == null) {
                    throw NoUrlInformationException(true)
                } else {
                    // If local URL is not reachable or not configured, use remote URL
                    Log.d(TAG, "Connecting to remote URL")
                    remote
                }
            }
            // Else we treat other networks types as unsupported
            else -> {
                Log.e(TAG, "Network type (" + info.typeName + ") is unsupported")
                throw NetworkNotSupportedException(info)
            }
        }
    }

    private fun checkCloudConnectionAsync(conn: AbstractConnection?) = GlobalScope.async(Dispatchers.Default) {
        conn?.toCloudConnection()
    }

    private class ClientKeyManager(context: Context, private val alias: String?) : X509KeyManager {
        private val context: Context = context.applicationContext

        override fun chooseClientAlias(keyType: Array<String>, issuers: Array<Principal>, socket: Socket): String? {
            Log.d(TAG, "chooseClientAlias - alias: $alias")
            return alias
        }

        override fun chooseServerAlias(keyType: String, issuers: Array<Principal>, socket: Socket): String? {
            Log.d(TAG, "chooseServerAlias")
            return null
        }

        override fun getCertificateChain(alias: String): Array<X509Certificate>? {
            Log.d(TAG, "getCertificateChain", Throwable())
            return try {
                KeyChain.getCertificateChain(context, alias)
            } catch (e: KeyChainException) {
                Log.e(TAG, "Failed loading certificate chain", e)
                null
            } catch (e: InterruptedException) {
                Log.e(TAG, "Failed loading certificate chain", e)
                null
            }

        }

        override fun getClientAliases(keyType: String, issuers: Array<Principal>): Array<String>? {
            Log.d(TAG, "getClientAliases")
            return if (alias != null) arrayOf(alias) else null
        }

        override fun getServerAliases(keyType: String, issuers: Array<Principal>): Array<String>? {
            Log.d(TAG, "getServerAliases")
            return null
        }

        override fun getPrivateKey(alias: String): PrivateKey? {
            Log.d(TAG, "getPrivateKey", Throwable())
            return try {
                KeyChain.getPrivateKey(context, alias)
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
        private val LOCAL_CONNECTION_TYPES = Arrays.asList(
                ConnectivityManager.TYPE_ETHERNET, ConnectivityManager.TYPE_WIFI,
                ConnectivityManager.TYPE_WIMAX, ConnectivityManager.TYPE_VPN
        )
        private val CLIENT_CERT_UPDATE_TRIGGERING_KEYS = Arrays.asList(
                Constants.PREFERENCE_DEMOMODE, Constants.PREFERENCE_SSLCLIENTCERT
        )
        private val UPDATE_TRIGGERING_KEYS = Arrays.asList(
                Constants.PREFERENCE_LOCAL_URL, Constants.PREFERENCE_REMOTE_URL,
                Constants.PREFERENCE_LOCAL_USERNAME, Constants.PREFERENCE_LOCAL_PASSWORD,
                Constants.PREFERENCE_REMOTE_USERNAME, Constants.PREFERENCE_REMOTE_PASSWORD,
                Constants.PREFERENCE_SSLCLIENTCERT, Constants.PREFERENCE_DEMOMODE
        )

        @VisibleForTesting lateinit var instance: ConnectionFactory

        fun initialize(ctx: Context) {
            instance = ConnectionFactory(ctx, ctx.getPrefs())
            instance.launch {
                instance.updateConnections()
            }
        }

        @VisibleForTesting
        fun initialize(ctx: Context, settings: SharedPreferences) {
            instance = ConnectionFactory(ctx, settings)
        }

        fun shutdown() {
            instance.context.unregisterReceiver(instance)
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
            val sub = instance.initDoneChannel.openSubscription()
            while (!sub.receive()) {}
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
            instance.launch {
                instance.triggerConnectionUpdateIfNeeded()
            }
        }

        /**
         * Returns any openHAB connection that is most likely to work on the current network. The
         * connections available will be tried in the following order:
         * - TYPE_LOCAL
         * - TYPE_REMOTE
         * - TYPE_CLOUD
         *
         * May return null if the available connection has not been initially determined yet.
         * Otherwise a Connection object is returned or, if there's an issue in configuration or
         * network connectivity, the respective exception is thrown.
         */
        val usableConnection: Connection?
            @Throws(ConnectionException::class)
            get() {
                instance.triggerConnectionUpdateIfNeededAndPending()
                val reason = instance.connectionFailureReason
                if (reason != null) {
                    throw reason
                }
                return instance.availableConnection
            }

        /**
         * Returns a Connection of the specified type.
         *
         * May return null if no such connection is available
         * (in case the respective server isn't configured in settings)
         */
        fun getConnection(connectionType: Int): Connection? {
            return instance.getConnectionInternal(connectionType)
        }
    }
}
