package org.openhab.habdroid.core.connection

import android.util.Log

import okhttp3.OkHttpClient
import org.openhab.habdroid.util.HttpClient

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL

abstract class AbstractConnection : Connection {
    final override val connectionType: Int
    final override val username: String?
    final override val password: String?
    private val baseUrl: String

    final override val httpClient: HttpClient

    internal constructor(
        httpClient: OkHttpClient,
        connectionType: Int,
        baseUrl: String,
        username: String?,
        password: String?
    ) {
        this.username = username
        this.password = password
        this.baseUrl = baseUrl
        this.connectionType = connectionType
        this.httpClient = HttpClient(httpClient, baseUrl, username, password)
    }

    internal constructor(base: AbstractConnection, connectionType: Int) {
        username = base.username
        password = base.password
        baseUrl = base.baseUrl
        this.connectionType = connectionType
        httpClient = base.httpClient
    }

    override fun checkReachabilityInBackground(): Boolean {
        Log.d(TAG, "Checking reachability of $baseUrl")
        try {
            val url = URL(baseUrl)
            val checkPort = when {
                url.protocol == "http" && url.port == -1 -> 80
                url.protocol == "https" && url.port == -1 -> 443
                else -> url.port
            }
            val s = createConnectedSocket(InetSocketAddress(url.host, checkPort)) ?: return false
            s.close()
            return true
        } catch (e: Exception) {
            Log.d(TAG, e.message)
            return false
        }
    }

    private fun createConnectedSocket(socketAddress: InetSocketAddress): Socket? {
        val s = Socket()
        var retries = 0
        while (retries < 10) {
            try {
                s.connect(socketAddress, 1000)
                Log.d(TAG, "Socket connected (attempt  $retries)")
                return s
            } catch (e: SocketTimeoutException) {
                Log.d(TAG, "Socket timeout after $retries retries")
                retries += 5
            } catch (e: IOException) {
                Log.d(TAG, "Socket creation failed (attempt  $retries)")
                try {
                    Thread.sleep(200)
                } catch (ignored: InterruptedException) {
                    // ignored
                }
            }

            retries++
        }
        return null
    }

    companion object {
        private val TAG = AbstractConnection::class.java.simpleName
    }
}
