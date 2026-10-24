/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
package org.openhab.habdroid.ui.tv

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import org.openhab.habdroid.util.AsyncServiceResolver

/**
 * Discovers the local openHAB server via mDNS during the first run setup.
 */
class TvSetupViewModel(application: Application) : AndroidViewModel(application) {
    sealed interface DiscoveryState {
        data object Searching : DiscoveryState

        data class Found(val url: String) : DiscoveryState

        data object NotFound : DiscoveryState
    }

    private val _state = MutableStateFlow<DiscoveryState>(DiscoveryState.Searching)
    val state: StateFlow<DiscoveryState> = _state.asStateFlow()

    private var discoveryJob: Job? = null

    init {
        discover()
    }

    fun discover() {
        discoveryJob?.cancel()
        _state.value = DiscoveryState.Searching
        discoveryJob = viewModelScope.launch {
            // Prefer an encrypted connection and fall back to an unencrypted one
            val url = discoverReachableServer(AsyncServiceResolver.OPENHAB_SERVICE_TYPE, "https", this)
                ?: discoverReachableServer(AsyncServiceResolver.OPENHAB_HTTP_SERVICE_TYPE, "http", this)
            _state.value = if (url != null) DiscoveryState.Found(url) else DiscoveryState.NotFound
        }
    }

    /**
     * @return URL of the first address of the discovered server that accepts connections, or null if none was found
     */
    private suspend fun discoverReachableServer(serviceType: String, scheme: String, scope: CoroutineScope): String? {
        val info = AsyncServiceResolver(getApplication(), serviceType, scope).resolve() ?: return null
        // Link-local IPv6 addresses need a zone ID, which can't be part of a URL
        val address = info.inetAddresses
            .filterNot { address -> address.isLinkLocalAddress }
            .sortedBy { address -> address !is Inet4Address }
            .firstOrNull { address -> isReachable(InetSocketAddress(address, info.port)) }
        if (address == null) {
            Log.d(TAG, "Discovered $serviceType at ${info.hostAddresses.contentToString()}, but it isn't reachable")
            return null
        }
        // HttpUrl adds brackets to IPv6 addresses
        return HttpUrl.Builder()
            .scheme(scheme)
            .host(address.hostAddress!!)
            .port(info.port)
            .build()
            .toString()
            .removeSuffix("/")
    }

    private suspend fun isReachable(address: InetSocketAddress): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket -> socket.connect(address, 2000) }
            true
        } catch (e: IOException) {
            Log.d(TAG, "$address isn't reachable", e)
            false
        }
    }

    companion object {
        private val TAG = TvSetupViewModel::class.java.simpleName
    }
}
