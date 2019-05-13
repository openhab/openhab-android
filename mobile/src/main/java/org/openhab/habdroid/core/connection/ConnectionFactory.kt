package org.openhab.habdroid.core.connection

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.preference.PreferenceManager
import android.security.KeyChain
import android.security.KeyChainException
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.util.Pair

import de.duenndns.ssl.MemorizingTrustManager
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
import org.openhab.habdroid.util.Util

import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Arrays
import java.util.HashSet
import java.util.concurrent.locks.ReentrantLock

import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509KeyManager
import kotlin.concurrent.withLock

/**
 * A factory class, which is the main entry point to get a Connection to a specific openHAB
 * server. Use this factory class whenever you need to obtain a connection to load additional
 * data from the openHAB server or another supported source
 * (see the constants in [Connection]).
 */
class ConnectionFactory internal constructor(private val context: Context, private val prefs: SharedPreferences) :
        BroadcastReceiver(), SharedPreferences.OnSharedPreferenceChangeListener, Handler.Callback {
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
    private val initializationLock = ReentrantLock()
    private val initializationCondition = initializationLock.newCondition()

    private val updateThread: HandlerThread
    @VisibleForTesting var updateHandler: Handler
    @VisibleForTesting var mainHandler: Handler

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

        updateThread = HandlerThread("ConnectionUpdate")
        updateThread.start()
        updateHandler = Handler(updateThread.looper, this)
        mainHandler = Handler(Looper.getMainLooper(), this)
    }

    private fun addListenerInternal(l: UpdateListener) {
        if (listeners.add(l)) {
            if (l is Activity) {
                trustManager.bindDisplayActivity(l as Activity)
            }
            if (!triggerConnectionUpdateIfNeededAndPending()
                    && localConnection != null && listeners.size == 1) {
                // When coming back from background, re-do connectivity check for
                // local connections, as the reachability of the local server might have
                // changed since we went to background
                val nuie = if (connectionFailureReason is NoUrlInformationException)
                    connectionFailureReason as NoUrlInformationException else null
                val local = availableConnection === localConnection || nuie != null && nuie.wouldHaveUsedLocalConnection()
                if (local) {
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
            updateConnections()
        }
    }

    private fun getConnectionInternal(connectionType: Int): Connection? {
        when (connectionType) {
            Connection.TYPE_LOCAL -> return localConnection
            Connection.TYPE_REMOTE -> return remoteConnection
            Connection.TYPE_CLOUD -> return cloudConnection
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
            initializationLock.withLock {
                availableInitialized = false
                needsUpdate = true
            }
        } else {
            triggerConnectionUpdateIfNeeded()
        }
    }

    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            MSG_UPDATE_AVAILABLE -> { // update thread
                val connections = msg.obj as Pair<Connection, Connection>
                val result = mainHandler.obtainMessage(MSG_AVAILABLE_DONE)
                try {
                    result.obj = determineAvailableConnection(context,
                            connections.first, connections.second)
                } catch (e: ConnectionException) {
                    result.obj = e
                }

                result.sendToTarget()
                return true
            }
            MSG_UPDATE_CLOUD -> { // update thread
                val remote = msg.obj as AbstractConnection
                val cloudConnection = CloudConnection.fromConnection(remote)
                mainHandler.obtainMessage(MSG_CLOUD_DONE, cloudConnection).sendToTarget()
                return true
            }
            MSG_AVAILABLE_DONE -> { // main thread
                val available = if (msg.obj is Connection) msg.obj as Connection else null
                val failureReason = if (msg.obj is ConnectionException) msg.obj as ConnectionException else null
                handleAvailableCheckDone(available, failureReason)
                return true
            }
            MSG_CLOUD_DONE -> { // main thread
                handleCloudCheckDone(msg.obj as CloudConnection)
                return true
            }
            else -> return false
        }
    }

    private fun updateAvailableConnection(c: Connection?, failureReason: ConnectionException?): Boolean {
        if (failureReason != null) {
            connectionFailureReason = failureReason
            availableConnection = null
        } else if (c === availableConnection) {
            return false
        } else {
            connectionFailureReason = null
            availableConnection = c
        }
        return true
    }

    @VisibleForTesting
    fun updateConnections() {
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

            initializationLock.withLock {
                availableInitialized = false
                cloudInitialized = false
            }
            availableConnection = null
            cloudConnection = null
            connectionFailureReason = null
            triggerConnectionUpdateIfNeeded()
        }
    }

    private fun updateHttpLoggerSettings() {
        if (prefs.getBoolean(Constants.PREFERENCE_DEBUG_MESSAGES, false)) {
            httpLogger.redactHeader("Authorization")
            httpLogger.redactHeader("set-cookie")
            httpLogger.level = HttpLoggingInterceptor.Level.HEADERS
        } else {
            httpLogger.level = HttpLoggingInterceptor.Level.NONE
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
                for (l in listeners) {
                    l.onAvailableConnectionChanged()
                }
            }
            initializationLock.withLock {
                availableInitialized = true
                initializationCondition.signalAll()
            }
        }
    }

    private fun handleCloudCheckDone(connection: CloudConnection?) {
        if (connection != cloudConnection) {
            cloudConnection = connection
            CloudMessagingHelper.onConnectionUpdated(context, connection)
            for (l in listeners) {
                l.onCloudConnectionChanged(connection)
            }
        }
        initializationLock.withLock {
            cloudInitialized = true
            initializationCondition.signalAll()
        }
    }

    private fun triggerConnectionUpdateIfNeededAndPending(): Boolean {
        initializationLock.withLock {
            if (!needsUpdate) {
                return false
            }
            needsUpdate = false
            triggerConnectionUpdateIfNeeded()
        }
        return true
    }

    private fun triggerConnectionUpdateIfNeeded() {
        updateHandler.removeMessages(MSG_UPDATE_AVAILABLE)
        updateHandler.removeMessages(MSG_UPDATE_CLOUD)

        if (localConnection is DemoConnection) {
            return
        }
        val connections = Pair.create<Connection, Connection>(localConnection, remoteConnection)
        updateHandler.obtainMessage(MSG_UPDATE_AVAILABLE, connections).sendToTarget()
        if (remoteConnection != null) {
            updateHandler.obtainMessage(MSG_UPDATE_CLOUD, remoteConnection).sendToTarget()
        } else {
            mainHandler.obtainMessage(MSG_CLOUD_DONE, null).sendToTarget()
        }
    }

    private fun makeConnection(type: Int, urlKey: String,
                               userNameKey: String, passwordKey: String): AbstractConnection? {
        val url = Util.normalizeUrl(prefs.getString(urlKey, "") as String)
        if (url.isEmpty()) {
            return null
        }
        return DefaultConnection(httpClient, type, url,
                prefs.getString(userNameKey, null),
                prefs.getString(passwordKey, null))
    }

    private class ClientKeyManager(context: Context, private val alias: String?) : X509KeyManager {
        private val context: Context

        init {
            this.context = context.applicationContext
        }

        override fun chooseClientAlias(keyType: Array<String>, issuers: Array<Principal>, socket: Socket): String? {
            Log.d(TAG, "chooseClientAlias - alias: " + alias)
            return alias
        }

        override fun chooseServerAlias(keyType: String, issuers: Array<Principal>, socket: Socket): String? {
            Log.d(TAG, "chooseServerAlias")
            return null
        }

        override fun getCertificateChain(alias: String): Array<X509Certificate>? {
            Log.d(TAG, "getCertificateChain", Throwable())
            try {
                return KeyChain.getCertificateChain(context, alias)
            } catch (e: KeyChainException) {
                Log.e(TAG, "Failed loading certificate chain", e)
                return null
            } catch (e: InterruptedException) {
                Log.e(TAG, "Failed loading certificate chain", e)
                return null
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
            try {
                return KeyChain.getPrivateKey(context, alias)
            } catch (e: KeyChainException) {
                Log.e(TAG, "Failed loading private key", e)
                return null
            } catch (e: InterruptedException) {
                Log.e(TAG, "Failed loading private key", e)
                return null
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

        private val MSG_UPDATE_AVAILABLE = 0
        private val MSG_UPDATE_CLOUD = 1
        private val MSG_AVAILABLE_DONE = 2
        private val MSG_CLOUD_DONE = 3

        @VisibleForTesting lateinit var instance: ConnectionFactory

        fun initialize(ctx: Context) {
            instance = ConnectionFactory(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
            instance.updateConnections()
        }

        @VisibleForTesting
        fun initialize(ctx: Context, settings: SharedPreferences) {
            instance = ConnectionFactory(ctx, settings)
        }

        fun shutdown() {
            instance.context.unregisterReceiver(instance)
            instance.updateThread.quit()
        }

        /**
         * Wait for initialization of the factory.
         *
         * This method blocks until all asynchronous work (that is, determination of
         * available and cloud connection) is ready, so that [.getConnection]
         * and [.getUsableConnection] can safely be used.
         *
         * It MUST NOT be called from the main thread.
         */
        fun waitForInitialization() {
            instance.triggerConnectionUpdateIfNeededAndPending()
            instance.initializationLock.withLock {
                while (!instance.availableInitialized || !instance.cloudInitialized) {
                    instance.initializationCondition.await()
                }
            }
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

        // called in update thread
        @Throws(ConnectionException::class)
        private fun determineAvailableConnection(context: Context,
                                                 local: Connection?, remote: Connection?): Connection {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val info = connectivityManager.activeNetworkInfo

            if (info == null || !info.isConnected) {
                Log.e(TAG, "Network is not available")
                throw NetworkNotAvailableException()
            }

            // If we are on a mobile network go directly to remote URL from settings
            if (info.type == ConnectivityManager.TYPE_MOBILE) {
                if (remote == null) {
                    throw NoUrlInformationException(false)
                }
                return remote
            }

            // Else if we are on Wifi, Ethernet, WIMAX or VPN network
            if (LOCAL_CONNECTION_TYPES.contains(info.type)) {
                // If local URL is configured and reachable
                if (local != null && local.checkReachabilityInBackground()) {
                    Log.d(TAG, "Connecting to local URL")

                    return local
                }
                // If local URL is not reachable or not configured, try with remote URL
                if (remote != null) {
                    Log.d(TAG, "Connecting to remote URL")
                    return remote
                } else {
                    throw NoUrlInformationException(true)
                }
                // Else we treat other networks types as unsupported
            } else {
                Log.e(TAG, "Network type (" + info.typeName + ") is unsupported")
                throw NetworkNotSupportedException(info)
            }
        }
    }
}
