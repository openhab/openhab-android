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

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.openhab.habdroid.core.connection.exception.ConnectionException
import org.openhab.habdroid.core.connection.exception.NetworkNotAvailableException
import org.openhab.habdroid.core.connection.exception.NetworkNotSupportedException
import org.openhab.habdroid.core.connection.exception.NoUrlInformationException
import org.openhab.habdroid.util.Constants
import java.io.File
import java.io.IOException

class ConnectionFactoryTest {
    companion object {
        private val mainThread = newSingleThreadContext("UI thread")

        @BeforeClass
        @JvmStatic
        @Throws(IOException::class)
        fun setupMainThread() {
            Dispatchers.setMain(mainThread)
        }

        @AfterClass
        @JvmStatic
        fun tearDownMainThread() {
            Dispatchers.resetMain()
            mainThread.close()
        }
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private val mockConnectionHelper = MockConnectionHelper()

    @Before
    @Throws(IOException::class)
    fun setup() {
        val cacheFolder = tempFolder.newFolder("cache")
        val appDir = tempFolder.newFolder()

        mockPrefs = mock()
        mockContext = mock<Application> {
            on { cacheDir } doReturn cacheFolder
            on { getDir(any(), any()) } doAnswer { invocation ->
                File(appDir, invocation.getArgument<Any>(0).toString())
            }
            on { getString(any()) } doReturn ""
        }
        whenever(mockContext.applicationContext) doReturn mockContext

        ConnectionFactory.initialize(mockContext, mockPrefs, mockConnectionHelper)
    }

    @Test
    @Throws(IOException::class)
    fun testGetConnectionRemoteWithUrl() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()

        whenever(mockPrefs.getString(eq(Constants.PREFERENCE_REMOTE_URL), any())) doReturn server.url("/").toString()
        updateAndWaitForConnections()

        val conn = ConnectionFactory.remoteConnection

        assertNotNull("Should return a remote connection if remote url is set.", conn)
        assertEquals("The connection type of a remote connection should be TYPE_REMOTE.",
            Connection.TYPE_REMOTE, conn!!.connectionType)
    }

    @Test
    fun testGetConnectionRemoteWithoutUrl() {
        whenever(mockPrefs.getString(eq(Constants.PREFERENCE_REMOTE_URL), any())) doReturn ""
        updateAndWaitForConnections()
        val conn = ConnectionFactory.remoteConnection

        assertNull("Should not return a remote connection if remote url isn't set.", conn)
    }

    @Test
    fun testGetConnectionLocalWithUrl() {
        whenever(mockPrefs.getString(eq(Constants.PREFERENCE_LOCAL_URL), any())) doReturn "https://openhab.local:8080"
        updateAndWaitForConnections()
        val conn = ConnectionFactory.localConnection

        assertNotNull("Should return a local connection if local url is set.", conn)
        assertEquals("The connection type of a local connection should be TYPE_LOCAL.",
            Connection.TYPE_LOCAL, conn!!.connectionType)
    }

    @Test
    fun testGetConnectionLocalWithoutUrl() {
        whenever(mockPrefs.getString(eq(Constants.PREFERENCE_LOCAL_URL), any())) doReturn ""
        updateAndWaitForConnections()
        val conn = ConnectionFactory.localConnection

        assertNull("Should not return a local connection when local url isn't set.", conn)
    }

    @Test
    @Throws(IOException::class)
    fun testGetConnectionCloudWithUrl() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("{'gcm': { 'senderId': '12345'} }"))
        server.start()

        whenever(mockPrefs.getString(eq(Constants.PREFERENCE_REMOTE_URL), any())) doReturn server.url("/").toString()
        updateAndWaitForConnections()
        val conn = ConnectionFactory.cloudConnectionOrNull

        assertNotNull("Should return a cloud connection if remote url is set.", conn)
        assertEquals(CloudConnection::class.java, conn!!.javaClass)
        assertEquals("The connection type of a cloud connection should be TYPE_CLOUD.",
            Connection.TYPE_CLOUD, conn.connectionType)
        assertEquals("The sender ID of the cloud connection should be '12345'",
            "12345", conn.messagingSenderId)

        server.shutdown()
    }

    @Test(expected = NetworkNotAvailableException::class)
    @Throws(ConnectionException::class)
    fun testGetAnyConnectionNoNetwork() {
        mockConnectionHelper.update(ConnectionManagerHelper.ConnectionType.None)
        updateAndWaitForConnections()
        ConnectionFactory.usableConnection
    }

    @Test(expected = NetworkNotSupportedException::class)
    @Throws(ConnectionException::class)
    fun testGetAnyConnectionUnsupportedNetwork() {
        mockConnectionHelper.update(ConnectionManagerHelper.ConnectionType.Unknown)
        updateAndWaitForConnections()
        ConnectionFactory.usableConnection
    }

    @Test
    @Throws(ConnectionException::class, IOException::class)
    fun testGetAnyConnectionWifiRemoteOnly() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()

        whenever(mockPrefs.getString(eq(Constants.PREFERENCE_REMOTE_URL), any())) doReturn server.url("/").toString()
        mockConnectionHelper.update(ConnectionManagerHelper.ConnectionType.Wifi)
        updateAndWaitForConnections()

        val conn = ConnectionFactory.usableConnection

        assertNotNull("Should return a connection in WIFI when only remote url is set.", conn)
        assertEquals("The connection type of the connection should be TYPE_REMOTE.",
            Connection.TYPE_REMOTE, conn.connectionType)

        server.shutdown()
    }

    @Test
    @Throws(ConnectionException::class, IOException::class)
    fun testGetAnyConnectionWifiLocalRemote() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()

        whenever(mockPrefs.getString(eq(Constants.PREFERENCE_REMOTE_URL), any())) doReturn server.url("/").toString()
        whenever(mockPrefs.getString(eq(Constants.PREFERENCE_LOCAL_URL), any())) doReturn "https://myopenhab.org:443"
        mockConnectionHelper.update(ConnectionManagerHelper.ConnectionType.Wifi)
        updateAndWaitForConnections()

        val conn = ConnectionFactory.usableConnection

        assertNotNull("Should return a connection in WIFI when a local url is set.", conn)
        assertEquals("The connection type of the connection should be TYPE_LOCAL.",
            Connection.TYPE_LOCAL, conn.connectionType)

        server.shutdown()
    }

    @Test(expected = NoUrlInformationException::class)
    @Throws(ConnectionException::class)
    fun testGetAnyConnectionWifiNoLocalNoRemote() {
        whenever(mockPrefs.getString(any(), any())) doReturn null
        mockConnectionHelper.update(ConnectionManagerHelper.ConnectionType.Wifi)
        updateAndWaitForConnections()
        ConnectionFactory.usableConnection
    }

    private inner class MockConnectionHelper : ConnectionManagerHelper {
        override var changeCallback: ConnectionChangedCallback? = null
        private var currentType = ConnectionManagerHelper.ConnectionType.Unknown
        override val currentConnection: ConnectionManagerHelper.ConnectionResult
            get() = ConnectionManagerHelper.ConnectionResult(currentType, null)

        fun update(type: ConnectionManagerHelper.ConnectionType) {
            currentType = type
            changeCallback?.invoke()
        }
        override fun shutdown() {}
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
