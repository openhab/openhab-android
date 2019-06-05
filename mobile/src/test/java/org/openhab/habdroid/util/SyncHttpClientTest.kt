package org.openhab.habdroid.util

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

import java.net.UnknownHostException

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
        val httpClient = HttpClient(client, "https://demo.test", null, null)

        val host = "just.a.local.url.local"
        try {
            runBlocking {
                httpClient.get("https://$host").asStatus()
            }
            assertTrue("Request should not succeed", true)
        } catch (e: HttpClient.HttpException) {
            assertEquals(500, e.statusCode)
            assertTrue(e.cause is UnknownHostException)
        }
    }
}
