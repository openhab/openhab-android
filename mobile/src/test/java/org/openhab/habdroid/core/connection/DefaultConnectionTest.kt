package org.openhab.habdroid.core.connection

import junit.framework.Assert.*
import okhttp3.Credentials
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Test

class DefaultConnectionTest {
    private lateinit var client: OkHttpClient

    private lateinit var testConnection: Connection
    private lateinit var testConnectionRemote: Connection
    private lateinit var testConnectionCloud: Connection

    @Before
    fun setup() {
        client = OkHttpClient.Builder().build()
        testConnection = DefaultConnection(client, Connection.TYPE_LOCAL,
                TEST_BASE_URL, null, null)
        testConnectionRemote = DefaultConnection(client, Connection.TYPE_REMOTE,
                "", null, null)
        testConnectionCloud = DefaultConnection(client,
                Connection.TYPE_CLOUD, "", null, null)
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
        val connection = DefaultConnection(client, Connection.TYPE_LOCAL,
                TEST_BASE_URL, "Test-User", null)
        assertEquals("Test-User", connection.username)
    }

    @Test
    fun testGetPasswordSet() {
        val connection = DefaultConnection(client, Connection.TYPE_LOCAL,
                TEST_BASE_URL, null, "Test-Password")
        assertEquals("Test-Password", connection.password)
    }

    @Test
    fun testGetSyncHttpClientCached() {
        val client1 = testConnection.syncHttpClient
        val client2 = testConnection.syncHttpClient

        assertNotNull(client1)
        assertEquals(client1, client2)
    }

    @Test
    fun testGetAsyncHttpClientCached() {
        val client1 = testConnection.asyncHttpClient
        val client2 = testConnection.asyncHttpClient

        assertNotNull(client1)
        assertEquals(client1, client2)
    }

    @Test
    fun testAsyncHasNoUsernamePassword() {
        val httpClient = testConnection.asyncHttpClient
        assertNull(httpClient.authHeader)
    }

    @Test
    fun testSyncHasNoUsernamePassword() {
        val httpClient = testConnection.syncHttpClient
        assertNull(httpClient.authHeader)
    }

    @Test
    fun testAsyncHasUsernamePassword() {
        val connection = DefaultConnection(client, Connection.TYPE_LOCAL,
                TEST_BASE_URL, "Test-User", "Test-Password")
        val httpClient = connection.asyncHttpClient

        assertEquals(Credentials.basic("Test-User", "Test-Password"),
                httpClient.authHeader)
    }

    @Test
    fun testSyncHasUsernamePassword() {
        val connection = DefaultConnection(client, Connection.TYPE_LOCAL,
                TEST_BASE_URL, "Test-User", "Test-Password")
        val httpClient = connection.syncHttpClient

        assertEquals(Credentials.basic("Test-User", "Test-Password"),
                httpClient.authHeader)
    }

    @Test
    fun testSyncResolveRelativeUrl() {
        val result1 = testConnection.syncHttpClient.get("rest/test")
        assertFalse("The request should never succeed in tests", result1.isSuccessful)
        assertEquals("$TEST_BASE_URL/rest/test", result1.request.url().toString())
        result1.close()

        val result2 = testConnection.syncHttpClient.get("/rest/test")
        assertEquals("$TEST_BASE_URL/rest/test", result2.request.url().toString())
        result2.close()
    }

    @Test
    fun testSyncResolveAbsoluteUrl() {
        val result = testConnection.syncHttpClient.get("http://mylocalmachine.local/rest/test")
        assertFalse("The request should never succeed in tests", result.isSuccessful)
        assertEquals("http://mylocalmachine.local/rest/test", result.request.url().toString())
        result.close()
    }

    companion object {
        private val TEST_BASE_URL = "https://demo.local:8443"
    }
}
