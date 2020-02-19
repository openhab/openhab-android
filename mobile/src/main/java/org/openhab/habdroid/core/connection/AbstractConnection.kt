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

import okhttp3.OkHttpClient
import org.openhab.habdroid.util.HttpClient

import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

abstract class AbstractConnection : Connection {
    final override val connectionType: Int
    final override val username: String?
    final override val password: String?
    final override val httpClient: HttpClient

    protected val baseUrl: String
    private val socketFactory = object : SocketFactory() {
        override fun createSocket(): Socket {
            return prepareSocket(Socket())
        }
        override fun createSocket(host: String?, port: Int): Socket {
            return prepareSocket(Socket(host, port))
        }
        override fun createSocket(host: String?, port: Int, clientAddress: InetAddress?, clientPort: Int): Socket {
            return prepareSocket(Socket(host, port, clientAddress, clientPort))
        }
        override fun createSocket(host: InetAddress?, port: Int): Socket {
            return prepareSocket(Socket(host, port))
        }
        override fun createSocket(host: InetAddress?, port: Int, clientAddress: InetAddress?, clientPort: Int): Socket {
            return prepareSocket(Socket(host, port, clientAddress, clientPort))
        }
    }

    internal constructor(
        httpClient: OkHttpClient,
        connectionType: Int,
        baseUrl: String,
        username: String?,
        password: String?
    ) {
        val httpClientWithSocketFactory = httpClient.newBuilder()
            .socketFactory(socketFactory)
            .build()

        this.username = username
        this.password = password
        this.baseUrl = baseUrl
        this.connectionType = connectionType
        this.httpClient = HttpClient(httpClientWithSocketFactory, baseUrl, username, password)
    }

    internal constructor(base: AbstractConnection, connectionType: Int) {
        username = base.username
        password = base.password
        baseUrl = base.baseUrl
        this.connectionType = connectionType
        httpClient = base.httpClient
    }

    open fun prepareSocket(socket: Socket): Socket {
        return socket
    }

    companion object {
        internal val TAG = AbstractConnection::class.java.simpleName
    }
}
