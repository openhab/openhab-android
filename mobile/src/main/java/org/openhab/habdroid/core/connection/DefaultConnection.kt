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

import android.net.Network
import android.util.Log
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.openhab.habdroid.util.bindToNetworkIfPossible
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

open class DefaultConnection : AbstractConnection {
    internal var network: Network? = null

    internal constructor(
        httpClient: OkHttpClient,
        connectionType: Int,
        baseUrl: String,
        username: String?,
        password: String?
    ) : super(httpClient, connectionType, baseUrl, username, password)

    internal constructor(baseConnection: AbstractConnection, connectionType: Int) :
        super(baseConnection, connectionType)

    suspend fun isReachableViaNetwork(network: Network?): Boolean {
        Log.d(TAG, "Checking reachability of $baseUrl (via $network)")
        val url = baseUrl.toHttpUrl()
        val s = createConnectedSocket(InetSocketAddress(url.host, url.port), network)
        s?.close()
        return s != null
    }

    private suspend fun createConnectedSocket(socketAddress: InetSocketAddress, network: Network?): Socket? {
        val s = Socket()
        s.bindToNetworkIfPossible(network)

        var retries = 0
        while (retries < 10) {
            try {
                s.connect(socketAddress, 1000)
                Log.d(TAG, "Socket connected (attempt  $retries)")
                return s
            } catch (e: SocketTimeoutException) {
                Log.d(TAG, "Socket timeout after $retries retries")
                retries += 5
            } catch (e: IOException) {
                Log.d(TAG, "Socket creation failed (attempt  $retries)")
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
}
