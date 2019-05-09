package org.openhab.habdroid.core.connection

import android.util.Log

import okhttp3.OkHttpClient
import org.openhab.habdroid.util.AsyncHttpClient
import org.openhab.habdroid.util.SyncHttpClient

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL

abstract class AbstractConnection : Connection {

    override val connectionType: Int
    override val username: String?
    override val password: String?
    private val baseUrl: String

    override val asyncHttpClient: AsyncHttpClient
    override val syncHttpClient: SyncHttpClient

    internal constructor(httpClient: OkHttpClient, connectionType: Int,
                         baseUrl: String, username: String?, password: String?) {
        this.username = username
        this.password = password
        this.baseUrl = baseUrl
        this.connectionType = connectionType

        asyncHttpClient = AsyncHttpClient(httpClient, baseUrl, username, password)
        syncHttpClient = SyncHttpClient(httpClient, baseUrl, username, password)
    }

    internal constructor(base: AbstractConnection, connectionType: Int) {
        username = base.username
        password = base.password
        baseUrl = base.baseUrl
        this.connectionType = connectionType

        asyncHttpClient = base.asyncHttpClient
        syncHttpClient = base.syncHttpClient
    }

    override fun checkReachabilityInBackground(): Boolean {
        Log.d(TAG, "Checking reachability of $baseUrl")
        try {
            val url = URL(baseUrl)
            var checkPort = url.port
            if (url.protocol == "http" && checkPort == -1) {
                checkPort = 80
            } else if (url.protocol == "https" && checkPort == -1) {
                checkPort = 443
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
