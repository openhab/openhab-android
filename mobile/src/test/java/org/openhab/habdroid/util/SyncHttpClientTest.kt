package org.openhab.habdroid.util

import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Test

import java.net.UnknownHostException

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class SyncHttpClientTest {
    private lateinit var client: OkHttpClient

    @Before
    fun setupClient() {
        client = OkHttpClient.Builder().build()
    }

    /**
     * Unit test against Issue #315 "Crash when connection could not be established".
     */
    @Test
    fun testMethodErrorResponse() {
        val httpClient = SyncHttpClient(client, "https://demo.test", null, null)

        val host = "just.a.local.url.local"
        val resp = httpClient["https://$host"].asStatus()

        assertEquals(500, resp.statusCode.toLong())
        assertTrue(resp.error is UnknownHostException)
    }
}
