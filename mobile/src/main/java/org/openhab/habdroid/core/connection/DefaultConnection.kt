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

import android.net.Network
import android.util.Log
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.openhab.habdroid.model.ServerPath
import org.openhab.habdroid.util.bindToNetworkIfPossible

open class DefaultConnection : AbstractConnection {
    internal var network: Network? = null
    internal var isMetered = false

    internal constructor(
        httpClient: OkHttpClient,
        connectionType: Int,
        path: ServerPath
    ) : super(httpClient, connectionType, path)

    internal constructor(baseConnection: AbstractConnection, connectionType: Int) :
        super(baseConnection, connectionType)

    suspend fun isReachableViaNetwork(network: Network?): Boolean {
        Log.d(TAG, "Checking reachability of $baseUrl (via $network)")
        val url = baseUrl.toHttpUrl()
        val s = createConnectedSocket(InetSocketAddress(url.host, url.port), network)
        withContext(Dispatchers.IO) {
            s?.close()
        }
        return s != null
    }

    private suspend fun createConnectedSocket(socketAddress: InetSocketAddress, network: Network?): Socket? {
        var retries = 0
        while (retries < 10) {
            try {
                val s = withContext(Dispatchers.IO) {
                    Socket().apply {
                        bindToNetworkIfPossible(network)
                        connect(socketAddress, 1000)
                    }
                }
                Log.d(TAG, "Socket connected (attempt $retries)")
                return s
            } catch (_: SocketTimeoutException) {
                Log.d(TAG, "Socket timeout after $retries retries")
                retries += 5
            } catch (e: IOException) {
                Log.d(TAG, "Socket creation failed (attempt  $retries): ${e.message}")
                delay(200)
            }

            retries++
        }
        return null
    }

    override fun prepareSocket(socket: Socket): Socket {
        socket.bindToNetworkIfPossible(network)
        return socket
    }

    override fun toString() =
        "DefaultConnection[type=$connectionType, url=$baseUrl, user=$username, network=$network, metered=$isMetered]"

    override fun equals(other: Any?): Boolean {
        val rhs = other as? DefaultConnection ?: return false
        return connectionType == rhs.connectionType &&
            baseUrl == rhs.baseUrl &&
            username == rhs.username &&
            password == rhs.password &&
            network?.networkHandle == rhs.network?.networkHandle &&
            isMetered == rhs.isMetered
    }

    override fun hashCode(): Int {
        var result = connectionType
        result = 31 * result + (username?.hashCode() ?: 0)
        result = 31 * result + (password?.hashCode() ?: 0)
        result = 31 * result + baseUrl.hashCode()
        result = 31 * result + isMetered.hashCode()
        result = 31 * result + (network?.networkHandle?.hashCode() ?: 0)
        return result
    }
}
