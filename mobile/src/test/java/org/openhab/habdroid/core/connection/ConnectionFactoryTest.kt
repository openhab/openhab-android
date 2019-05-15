package org.openhab.habdroid.core.connection

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Handler
import android.os.Looper
import android.os.Message

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.openhab.habdroid.core.connection.exception.ConnectionException
import org.openhab.habdroid.core.connection.exception.NetworkNotAvailableException
import org.openhab.habdroid.core.connection.exception.NetworkNotSupportedException
import org.openhab.habdroid.core.connection.exception.NoUrlInformationException
import org.openhab.habdroid.util.Constants

import java.io.File
import java.io.IOException

import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertNull
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.`when`

class ConnectionFactoryTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var mockConnectivityService: ConnectivityManager
    private lateinit var mockPrefs: SharedPreferences

    @Before
    @Throws(IOException::class)
    fun setup() {
        mockConnectivityService = Mockito.mock(ConnectivityManager::class.java)

        val cacheFolder = tempFolder.newFolder("cache")
        val appDir = tempFolder.newFolder()

        mockContext = Mockito.mock(Application::class.java)
        Mockito.`when`(mockContext.applicationContext).thenReturn(mockContext)
        Mockito.`when`(mockContext.cacheDir).thenReturn(cacheFolder)
        Mockito.`when`(mockContext.getDir(anyString(), anyInt()))
                .then { invocation -> File(appDir, invocation.getArgument<Any>(0).toString()) }
        `when`(mockContext.getString(anyInt())).thenReturn("")
        `when`(mockContext.getSystemService(eq(Context.CONNECTIVITY_SERVICE)))
                .thenReturn(mockConnectivityService)
        `when`(mockContext.mainLooper).thenReturn(Looper.getMainLooper())

        mockPrefs = Mockito.mock(SharedPreferences::class.java)

        ConnectionFactory.initialize(mockContext, mockPrefs)

        ConnectionFactory.instance.mainHandler = makeMockedHandler()
        ConnectionFactory.instance.updateHandler = makeMockedHandler()
    }

    @Test
    @Throws(IOException::class)
    fun testGetConnectionRemoteWithUrl() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()

        `when`(mockPrefs.getString(eq(Constants.PREFERENCE_REMOTE_URL), anyString()))
                .thenReturn(server.url("/").toString())
        ConnectionFactory.instance.updateConnections()
        val conn = ConnectionFactory.getConnection(Connection.TYPE_REMOTE)

        assertNotNull("Requesting a remote connection when a remote url is set, " + " should return a connection.", conn)
        assertEquals("The connection type of a remote connection should be TYPE_REMOTE.",
                Connection.TYPE_REMOTE, conn!!.connectionType)
    }

    @Test
    fun testGetConnectionRemoteWithoutUrl() {
        `when`(mockPrefs.getString(eq(Constants.PREFERENCE_REMOTE_URL), anyString()))
                .thenReturn("")
        ConnectionFactory.instance.updateConnections()
        val conn = ConnectionFactory.getConnection(Connection.TYPE_REMOTE)

        assertNull("Requesting a remote connection when a remote url isn't set, " + "should not return a connection.", conn)
    }

    @Test
    fun testGetConnectionLocalWithUrl() {
        `when`(mockPrefs.getString(eq(Constants.PREFERENCE_LOCAL_URL), anyString()))
                .thenReturn("https://openhab.local:8080")
        ConnectionFactory.instance.updateConnections()
        val conn = ConnectionFactory.getConnection(Connection.TYPE_LOCAL)

        assertNotNull("Requesting a local connection when local url is set, " + "should return a connection.", conn)
        assertEquals("The connection type of a local connection should be LOGLEVEL_LOCAL.",
                Connection.TYPE_LOCAL, conn!!.connectionType)
    }

    @Test
    fun testGetConnectionLocalWithoutUrl() {
        `when`(mockPrefs.getString(eq(Constants.PREFERENCE_LOCAL_URL), anyString()))
                .thenReturn("")
        ConnectionFactory.instance.updateConnections()
        val conn = ConnectionFactory.getConnection(Connection.TYPE_LOCAL)

        assertNull("Requesting a remote connection when a local url isn't set, " + "should not return a connection.", conn)
    }

    @Test
    @Throws(IOException::class)
    fun testGetConnectionCloudWithUrl() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("{'gcm': { 'senderId': '12345'} }"))
        server.start()

        `when`(mockPrefs.getString(eq(Constants.PREFERENCE_REMOTE_URL), anyString()))
                .thenReturn(server.url("/").toString())

        ConnectionFactory.instance.updateConnections()
        val conn = ConnectionFactory.getConnection(Connection.TYPE_CLOUD)

        assertNotNull("Requesting a cloud connection when a remote url is set, " + "should return a connection.", conn)
        assertEquals(CloudConnection::class.java, conn!!.javaClass)
        assertEquals("The connection type of a cloud connection should be TYPE_CLOUD.",
                Connection.TYPE_CLOUD, conn.connectionType)
        assertEquals("The sender ID of the cloud connection should be '12345'",
                "12345", (conn as CloudConnection).messagingSenderId)

        server.shutdown()
    }

    @Test(expected = NetworkNotAvailableException::class)
    @Throws(ConnectionException::class)
    fun testGetAnyConnectionNoNetwork() {
        triggerNetworkUpdate(null)

        ConnectionFactory.usableConnection
    }

    @Test(expected = NetworkNotSupportedException::class)
    @Throws(ConnectionException::class)
    fun testGetAnyConnectionUnsupportedNetwork() {
        triggerNetworkUpdate(ConnectivityManager.TYPE_BLUETOOTH)

        ConnectionFactory.usableConnection
    }

    @Test
    @Throws(ConnectionException::class, IOException::class)
    fun testGetAnyConnectionWifiRemoteOnly() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()

        `when`(mockPrefs.getString(eq(Constants.PREFERENCE_REMOTE_URL), anyString()))
                .thenReturn(server.url("/").toString())
        ConnectionFactory.instance.updateConnections()
        triggerNetworkUpdate(ConnectivityManager.TYPE_WIFI)

        val conn = ConnectionFactory.usableConnection

        assertNotNull("Requesting any connection in WIFI when only a remote url is set, " + "should return a connection.", conn)
        assertEquals("The connection type of the connection should be TYPE_REMOTE.",
                Connection.TYPE_REMOTE, conn!!.connectionType)

        server.shutdown()
    }

    @Test
    @Throws(ConnectionException::class, IOException::class)
    fun testGetAnyConnectionWifiLocalRemote() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()

        `when`(mockPrefs.getString(eq(Constants.PREFERENCE_REMOTE_URL), anyString()))
                .thenReturn(server.url("/").toString())
        `when`(mockPrefs.getString(eq(Constants.PREFERENCE_LOCAL_URL), anyString()))
                .thenReturn("https://myopenhab.org:443")
        ConnectionFactory.instance.updateConnections()
        triggerNetworkUpdate(ConnectivityManager.TYPE_WIFI)

        val conn = ConnectionFactory.usableConnection

        assertNotNull("Requesting any connection in WIFI when a local url is set, " + "should return a connection.", conn)
        assertEquals("The connection type of the connection should be TYPE_LOCAL.",
                Connection.TYPE_LOCAL, conn!!.connectionType)

        server.shutdown()
    }

    @Test(expected = NoUrlInformationException::class)
    @Throws(ConnectionException::class)
    fun testGetAnyConnectionWifiNoLocalNoRemote() {
        `when`(mockPrefs.getString(anyString(), anyString())).thenReturn(null)
        triggerNetworkUpdate(ConnectivityManager.TYPE_WIFI)

        ConnectionFactory.usableConnection
    }

    private fun triggerNetworkUpdate(type: Int) {
        val mockNetworkInfo = Mockito.mock(NetworkInfo::class.java)
        `when`(mockNetworkInfo.type).thenReturn(type)
        `when`(mockNetworkInfo.isConnected).thenReturn(true)
        triggerNetworkUpdate(mockNetworkInfo)
    }

    private fun triggerNetworkUpdate(info: NetworkInfo?) {
        `when`(mockConnectivityService.activeNetworkInfo).thenReturn(info)

        ConnectionFactory.instance.onReceive(mockContext,
                Intent(ConnectivityManager.CONNECTIVITY_ACTION))
    }

    private fun makeMockedHandler(): Handler {
        val h = Mockito.mock(Handler::class.java)
        `when`(h.sendEmptyMessage(anyInt())).thenAnswer { invocation ->
            val msg = Message()
            msg.what = invocation.getArgument(0)
            ConnectionFactory.instance.handleMessage(msg)
            true
        }
        `when`(h.sendMessage(any(Message::class.java))).thenAnswer { invocation ->
            val msg = invocation.getArgument<Message>(0)
            ConnectionFactory.instance.handleMessage(msg)
            true
        }
        `when`(h.obtainMessage(anyInt()))
                .thenAnswer { invocation -> makeMockedMessage(h, invocation.getArgument(0), null) }
        `when`(h.obtainMessage(anyInt(), any()))
                .thenAnswer { invocation ->
                    makeMockedMessage(h, invocation.getArgument(0),
                            invocation.getArgument<Any>(1))
                }
        return h
    }

    private fun makeMockedMessage(h: Handler, what: Int, obj: Any?): Message {
        val msg = Mockito.mock(Message::class.java)
        msg.what = what
        msg.obj = obj
        doAnswer { invocationOnMock -> h.sendMessage(msg) }.`when`(msg).sendToTarget()
        return msg
    }
}
