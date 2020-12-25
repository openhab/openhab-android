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

import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.openhab.habdroid.model.ServerPath
import org.openhab.habdroid.util.HttpClient

class DefaultConnectionTest {
    private lateinit var client: OkHttpClient

    private lateinit var testConnection: Connection
    private lateinit var testConnectionRemote: Connection
    private lateinit var testConnectionCloud: Connection

    @Before
    fun setup() {
        client = OkHttpClient.Builder().build()
        testConnection = DefaultConnection(
            client,
            Connection.TYPE_LOCAL,
            ServerPath(TEST_BASE_URL, null, null)
        )
        testConnectionRemote = DefaultConnection(
            client,
            Connection.TYPE_REMOTE,
            ServerPath("", null, null)
        )
        testConnectionCloud = DefaultConnection(
            client,
            Connection.TYPE_CLOUD,
            ServerPath("", null, null)
        )
    }

    @Test
    fun testGetConnectionTypeSetRemote() {
        assertEquals(Connection.TYPE_REMOTE, testConnectionRemote.connectionType)
    }

    @Test
    fun testGetConnectionTypeSetLocal() {
        assertEquals(Connection.TYPE_LOCAL, testConnection.connectionType)
    }

    @Test
    fun testGetConnectionTypeSetCloud() {
        assertEquals(Connection.TYPE_CLOUD, testConnectionCloud.connectionType)
    }

    @Test
    fun testGetUsernameNotSet() {
        assertNull(testConnection.username)
    }

    @Test
    fun testGetPasswordNotSet() {
        assertNull(testConnection.password)
    }

    @Test
    fun testGetUsernameSet() {
        val connection = DefaultConnection(
            client,
            Connection.TYPE_LOCAL,
            ServerPath(TEST_BASE_URL, "Test-User", null)
        )
        assertEquals("Test-User", connection.username)
    }

    @Test
    fun testGetPasswordSet() {
        val connection = DefaultConnection(
            client,
            Connection.TYPE_LOCAL,
            ServerPath(TEST_BASE_URL, null, "Test-Password")
        )
        assertEquals("Test-Password", connection.password)
    }

    @Test
    fun testGetHttpClientCached() {
        val client1 = testConnection.httpClient
        val client2 = testConnection.httpClient

        assertNotNull(client1)
        assertEquals(client1, client2)
    }

    @Test
    fun testHasNoUsernamePassword() {
        val httpClient = testConnection.httpClient
        assertNull(httpClient.authHeader)
    }

    @Test
    fun testHasUsernamePassword() {
        val connection = DefaultConnection(
            client,
            Connection.TYPE_LOCAL,
            ServerPath(TEST_BASE_URL, "Test-User", "Test-Password")
        )
        val httpClient = connection.httpClient

        assertEquals(Credentials.basic("Test-User", "Test-Password"), httpClient.authHeader)
    }

    @Test
    fun testResolveRelativeUrl() {
        val requestUrl1 = getRequestUrlForUrl("rest/test")
        assertEquals("$TEST_BASE_URL/rest/test", requestUrl1.toString())

        val requestUrl2 = getRequestUrlForUrl("/rest/test")
        assertEquals("$TEST_BASE_URL/rest/test", requestUrl2.toString())
    }

    @Test
    fun testSyncResolveAbsoluteUrl() {
        val requestUrl = getRequestUrlForUrl("http://mylocalmachine.local/rest/test")
        assertEquals("http://mylocalmachine.local/rest/test", requestUrl.toString())
    }

    private fun getRequestUrlForUrl(url: String): HttpUrl = try {
        runBlocking {
            val result = testConnection.httpClient.get(url)
            result.close()
            assertFalse("The request should never succeed in tests", true)
            result.request.url
        }
    } catch (e: HttpClient.HttpException) {
        e.request.url
    }

    companion object {
        private const val TEST_BASE_URL = "https://demo.local:8443"
    }
}
