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

import org.openhab.habdroid.util.HttpClient

interface Connection {
    /**
     * @return A fully setup http client for requesting resources from the
     * server represented in this Connection object.
     */
    val httpClient: HttpClient

    /**
     * @return The username used for this connection.
     */
    val username: String?

    /**
     * @return The password used for this connection.
     */
    val password: String?

    /**
     * @return The type of this connection represented by one of the TYPE_* constants of the
     * Connection interface.
     */
    val connectionType: Int

    companion object {
        /**
         * Represents a connection to a locally hosted openHAB server, which is most likely the instance
         * configured in the settings. May or may not work on the network the device is currently
         * connected to.
         */
        const val TYPE_LOCAL = 0
        /**
         * Represents a connection to an openHAB instance, which may or may not be available from the
         * public internet. This is most likely the instance configured in the "remote" settings of
         * the device. The connection may or may not be available in the network the device is
         * currently connected to.
         */
        const val TYPE_REMOTE = 1
        /**
         * Represents a connection that is guaranteed to provide functionality implemented in the
         * openHAB cloud product (like notifications).
         */
        const val TYPE_CLOUD = 2
    }
}
