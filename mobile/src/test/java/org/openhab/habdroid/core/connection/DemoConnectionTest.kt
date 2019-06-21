package org.openhab.habdroid.core.connection

import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Test

import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNull

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
