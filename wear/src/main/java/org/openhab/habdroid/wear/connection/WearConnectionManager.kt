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

package org.openhab.habdroid.wear.connection

import android.content.Context
import android.util.Log
import java.security.cert.CertPathValidatorException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.connection.DefaultConnection
import org.openhab.habdroid.core.wear.WearConfig
import org.openhab.habdroid.model.ServerPath
import org.openhab.habdroid.model.ServerProperties
import org.openhab.habdroid.wear.BuildConfig
import org.openhab.habdroid.wear.config.WearConfigRepository

/**
 * Connects to the server configured on the phone. The local connection is preferred, the remote connection is used
 * if the local one isn't reachable, e.g. when the watch is away from home.
 */
class WearConnectionManager(
    context: Context,
    private val configRepository: WearConfigRepository,
    private val scope: CoroutineScope
) {
    sealed interface State {
        data object NotConfigured : State

        data object Connecting : State

        data class Connected(
            val connection: Connection,
            val properties: ServerProperties,
            val config: WearConfig,
            val isDemo: Boolean
        ) : State

        data class Failed(val error: ConnectionError, val url: String?) : State
    }

    enum class ConnectionError {
        ConnectionFailed,
        CertificateNotTrusted,
        AuthenticationFailed,
        HttpError
    }

    private val cache = Cache(context.cacheDir.resolve("http"), 10L * 1024 * 1024)
    private val baseClient = OkHttpClient.Builder()
        .cache(cache)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level =
                    if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
                redactHeader("Authorization")
            }
        )
        .build()
        .apply {
            // SSE connections count against the per-host limit
            dispatcher.maxRequestsPerHost = dispatcher.maxRequests
        }

    private val _state = MutableStateFlow<State>(State.NotConfigured)
    val state: StateFlow<State> = _state.asStateFlow()

    private var connectJob: Job? = null

    init {
        // Connect right away, so tiles and complications don't see an unconfigured state
        reconnect()
        scope.launch {
            configRepository.settings.drop(1).collect { reconnect() }
        }
    }

    fun reconnect() {
        connectJob?.cancel()
        val settings = configRepository.settings.value
        if (settings !is WearConfigRepository.Settings.Configured) {
            _state.value = State.NotConfigured
            return
        }
        _state.value = State.Connecting
        connectJob = scope.launch {
            _state.value = connect(settings.config, settings.isDemo)
        }
    }

    /**
     * Connects if there's no connection yet, e.g. when called from a tile or complication.
     *
     * @return The connection or null if no connection could be established
     */
    suspend fun awaitConnection(): State.Connected? {
        when (state.value) {
            is State.Failed -> reconnect()
            is State.NotConfigured -> return null
            else -> {}
        }
        return withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) {
            state.first { state -> state !is State.Connecting } as? State.Connected
        }
    }

    private suspend fun connect(config: WearConfig, isDemo: Boolean): State {
        val certificates = SyncedCertificates(config.trustedCertificates)
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(certificates.trustManager), null)
        }
        val client = baseClient.newBuilder()
            .sslSocketFactory(sslContext.socketFactory, certificates.trustManager)
            .hostnameVerifier(certificates.hostnameVerifier)
            .build()
        // Fail fast if the local server isn't reachable, e.g. if the watch isn't at home
        val localClient = client.newBuilder().connectTimeout(LOCAL_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()

        val candidates = listOfNotNull(
            config.localPath?.let { path -> DefaultConnection(localClient, Connection.TYPE_LOCAL, path) },
            config.remotePath?.let { path -> DefaultConnection(client, Connection.TYPE_REMOTE, path) }
        )

        var failure: State.Failed? = null
        for (connection in candidates) {
            Log.d(TAG, "Trying $connection")
            when (val result = ServerProperties.fetch(connection)) {
                is ServerProperties.Companion.PropsSuccess -> {
                    Log.d(TAG, "Connected via $connection")
                    return State.Connected(connection, result.props, config, isDemo)
                }

                is ServerProperties.Companion.PropsFailure -> {
                    Log.w(TAG, "Unable to connect via $connection", result.error)
                    // Prefer to report errors of the local connection, as the remote one is just a fallback
                    if (failure == null || failure.error == ConnectionError.ConnectionFailed) {
                        failure = State.Failed(
                            result.toConnectionError(),
                            pathOf(config, connection.connectionType)?.url
                        )
                    }
                }
            }
        }
        return failure ?: State.Failed(ConnectionError.ConnectionFailed, null)
    }

    private fun pathOf(config: WearConfig, connectionType: Int): ServerPath? =
        if (connectionType == Connection.TYPE_LOCAL) config.localPath else config.remotePath

    private fun ServerProperties.Companion.PropsFailure.toConnectionError(): ConnectionError {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is CertPathValidatorException || cause is SSLException) {
                return ConnectionError.CertificateNotTrusted
            }
            cause = cause.cause
        }
        return when (httpStatusCode) {
            401, 403 -> ConnectionError.AuthenticationFailed
            in 400..499, in 501..599 -> ConnectionError.HttpError
            else -> ConnectionError.ConnectionFailed
        }
    }

    companion object {
        private val TAG = WearConnectionManager::class.java.simpleName
        private const val LOCAL_CONNECT_TIMEOUT_SECONDS = 4L
        private const val CONNECT_TIMEOUT_MILLIS = 30_000L
    }
}
