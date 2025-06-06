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

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.openhab.habdroid.testUtils.Retry
import org.openhab.habdroid.testUtils.RetryRule
import org.openhab.habdroid.util.PrefKeys

class ConnectionFactoryTest {
    @ExperimentalCoroutinesApi
    companion object {
        private val mainThread = newSingleThreadContext("UI thread")

        @AfterClass
        @JvmStatic
        fun tearDownMainThread() {
            Dispatchers.resetMain()
            mainThread.close()
        }

        @BeforeClass
        @JvmStatic
        @Throws(IOException::class)
        fun setupMainThread() {
            Dispatchers.setMain(mainThread)
        }
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val retry = RetryRule()

    private lateinit var mockWifiManager: WifiManager
    private lateinit var mockContext: Application
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockNetwork: Network
    private lateinit var mockNetworkCaps: NetworkCapabilities
    private val mockConnectionHelper = MockConnectionHelper()

    @Before
    @Throws(IOException::class)
    fun setup() {
        val cacheFolder = tempFolder.newFolder()
        val appDir = tempFolder.newFolder()

        mockPrefs = mock {
            on { getStringSet(eq(PrefKeys.SERVER_IDS), anyOrNull()) } doReturn setOf("1")
            on { getInt(eq(PrefKeys.ACTIVE_SERVER_ID), any()) } doReturn 1
        }

        mockWifiManager = mock {
        }
        @Suppress("DEPRECATION")
        whenever(mockWifiManager.connectionInfo) doReturn null

        mockContext = mock<Application> {
            on { cacheDir } doReturn cacheFolder
            on { getDir(any(), any()) } doAnswer { invocation ->
                File(appDir, invocation.getArgument<Any>(0).toString())
            }
            on { getString(any()) } doReturn ""
            on { getSystemService(Context.WIFI_SERVICE) } doReturn mockWifiManager
        }
        whenever(mockContext.applicationContext) doReturn mockContext

        mockNetwork = mock {}
        mockNetworkCaps = mock {}

        ConnectionFactory.initialize(mockContext, mockPrefs, mockConnectionHelper)
    }

    @Test
    @Retry
    @Throws(IOException::class)
    fun testGetConnectionRemoteWithUrl() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()

        fillInServers(remote = server.url("/").toString())
        updateAndWaitForConnections()
        assertTrue(
            "Should return a connection if remote url is set.",
            ConnectionFactory.hasActiveRemoteConnection
        )
    }

    @Test
    @Retry
    fun testGetConnectionRemoteWithoutUrl() {
        fillInServers(remote = "")
        updateAndWaitForConnections()
        assertFalse(
            "Should not return a remote connection if remote url isn't set.",
            ConnectionFactory.hasActiveRemoteConnection
        )
    }

    @Test
    @Retry
    fun testGetConnectionLocalWithUrl() {
        fillInServers(local = "https://openhab.local:8080")
        updateAndWaitForConnections()
        assertTrue(
            "Should return a local connection if local url is set.",
            ConnectionFactory.hasActiveLocalConnection
        )
    }

    @Test
    @Retry
    fun testGetConnectionLocalWithoutUrl() {
        fillInServers(local = "")
        updateAndWaitForConnections()
        assertFalse(
            "Should not return a local connection when local url isn't set.",
            ConnectionFactory.hasActiveLocalConnection
        )
    }

    @Test
    @Retry
    @Throws(IOException::class)
    fun testGetConnectionCloudWithUrl() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("{'gcm': { 'senderId': '12345'} }"))
        server.start()

        fillInServers(remote = server.url("/").toString())
        updateAndWaitForConnections()
        val conn = ConnectionFactory.activeCloudConnection?.connection

        assertNotNull("Should return a cloud connection if remote url is set.", conn)
        assertEquals(CloudConnection::class.java, conn!!.javaClass)
        assertEquals(
            "The connection type of a cloud connection should be TYPE_CLOUD.",
            Connection.TYPE_CLOUD,
            conn.connectionType
        )
        assertEquals(
            "The sender ID of the cloud connection should be '12345'",
            "12345",
            conn.messagingSenderId
        )

        server.shutdown()
    }

    @Test
    @Retry
    fun testGetAnyConnectionNoNetwork() {
        mockConnectionHelper.update(null)
        updateAndWaitForConnections()
        assertEquals(
            ConnectionFactory.activeUsableConnection?.failureReason?.javaClass,
            NetworkNotAvailableException::class.java
        )
    }

    @Test
    @Retry
    fun testGetConnectionUnknownNetwork() {
        fillInServers("https://openhab.local:8080", "https://openhab.local:8080")
        mockConnectionHelper.update(ConnectionManagerHelper.ConnectionType.Unknown(mockNetwork, mockNetworkCaps))
        updateAndWaitForConnections()
        assertEquals(
            "Unknown transport types should be used for remote connections",
            ConnectionFactory.activeUsableConnection?.connection?.connectionType,
            Connection.TYPE_REMOTE
        )
    }

    @Test
    @Retry
    fun testGetAnyConnectionWifiRemoteOnly() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()

        fillInServers(remote = server.url("/").toString())
        mockConnectionHelper.update(ConnectionManagerHelper.ConnectionType.Wifi(mockNetwork, mockNetworkCaps))
        updateAndWaitForConnections()

        val conn = ConnectionFactory.activeUsableConnection?.connection

        assertNotNull("Should return a connection in WIFI when only remote url is set.", conn)
        assertEquals(
            "The connection type of the connection should be TYPE_REMOTE.",
            Connection.TYPE_REMOTE,
            conn?.connectionType
        )

        server.shutdown()
    }

    @Test
    @Retry
    fun testGetAnyConnectionWifiLocalRemote() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()

        fillInServers(remote = server.url("/").toString(), local = "https://myopenhab.org:443")
        mockConnectionHelper.update(ConnectionManagerHelper.ConnectionType.Wifi(mockNetwork, mockNetworkCaps))
        updateAndWaitForConnections()

        val conn = ConnectionFactory.activeUsableConnection?.connection

        assertNotNull("Should return a connection in WIFI when a local url is set.", conn)
        assertEquals(
            "The connection type of the connection should be TYPE_LOCAL.",
            Connection.TYPE_LOCAL,
            conn?.connectionType
        )

        server.shutdown()
    }

    @Test
    @Retry
    fun testGetAnyConnectionWifiNoLocalNoRemote() {
        fillInServers(null, null)
        mockConnectionHelper.update(ConnectionManagerHelper.ConnectionType.Wifi(mockNetwork, mockNetworkCaps))
        updateAndWaitForConnections()
        assertEquals(
            ConnectionFactory.activeUsableConnection?.failureReason?.javaClass,
            NoUrlInformationException::class.java
        )
    }

    private inner class MockConnectionHelper : ConnectionManagerHelper {
        override var changeCallback: ConnectionChangedCallback? = null
        private var currentTypes: List<ConnectionManagerHelper.ConnectionType> = emptyList()
        override val currentConnections: List<ConnectionManagerHelper.ConnectionType> get() = currentTypes

        fun update(type: ConnectionManagerHelper.ConnectionType?) {
            currentTypes = if (type != null) listOf(type) else emptyList()
            changeCallback?.invoke()
        }

        override fun start() {}
        override fun shutdown() {}
    }

    private fun fillInServers(local: String? = null, remote: String? = null) {
        whenever(
            mockPrefs.getString(eq(PrefKeys.buildServerKey(1, PrefKeys.LOCAL_URL_PREFIX)), anyOrNull())
        ) doReturn local
        whenever(
            mockPrefs.getString(eq(PrefKeys.buildServerKey(1, PrefKeys.REMOTE_URL_PREFIX)), anyOrNull())
        ) doReturn remote
        whenever(
            mockPrefs.getString(eq(PrefKeys.buildServerKey(1, PrefKeys.SERVER_NAME_PREFIX)), anyOrNull())
        ) doReturn "Test Server"
    }

    private fun updateAndWaitForConnections() {
        runBlocking {
            launch(Dispatchers.Main) {
                ConnectionFactory.instance.updateConnections()
            }
            ConnectionFactory.waitForInitialization()
        }
    }
}
