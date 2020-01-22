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
import okhttp3.OkHttpClient
import java.net.Socket

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

    override fun prepareSocket(socket: Socket): Socket {
        network?.bindSocket(socket)
        return socket
    }
}
