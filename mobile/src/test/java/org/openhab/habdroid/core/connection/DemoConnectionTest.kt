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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DemoConnectionTest {
    private lateinit var testConnection: Connection

    @Before
    fun setup() {
        val client = OkHttpClient.Builder().build()
        testConnection = DemoConnection(client)
    }

    @Test
    fun testGetConnectionType() {
        assertEquals(Connection.TYPE_REMOTE, testConnection.connectionType)
    }

    @Test
    fun testGetUsername() {
        assertNull(testConnection.username)
    }

    @Test
    fun testGetPassword() {
        assertNull(testConnection.password)
    }
}
