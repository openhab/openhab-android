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

package org.openhab.habdroid.util

import android.content.Context
import android.net.wifi.WifiManager.MulticastLock
import android.util.Log
import java.net.BindException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.openhab.habdroid.core.OpenHabApplication

class AsyncServiceResolver(
    context: Context,
    private val serviceTypes: List<String>,
    private val scope: CoroutineScope
) : ServiceListener {
    constructor(context: Context, serviceType: String, scope: CoroutineScope) :
        this(context, listOf(serviceType), scope)

    // Multicast lock for mDNS
    private val multicastLock: MulticastLock

    // mDNS service
    private var jmDns: JmDNS? = null
    private val serviceInfoChannel = Channel<ServiceInfo>(Channel.UNLIMITED)

    private val localIpv4Address: InetAddress? get() {
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr?.hasMoreElements() == true) {
                    val inetAddress = enumIpAddr.nextElement()
                    Log.i(TAG, "IP: ${inetAddress.hostAddress}")
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        Log.i(TAG, "Selected ${inetAddress.getHostAddress()}")
                        return inetAddress
                    }
                }
            }
        } catch (e: SocketException) {
            Log.e(TAG, e.toString())
        }

        return null
    }

    init {
        val wifiManager = context.getWifiManager(OpenHabApplication.DATA_ACCESS_TAG_SERVER_DISCOVERY)
        multicastLock = wifiManager.createMulticastLock("HABDroidMulticastLock")
        multicastLock.setReferenceCounted(true)
    }

    /**
     * Returns the first service that has been resolved, or null if none was found within the timeout.
     */
    suspend fun resolve(): ServiceInfo? = discover {
        withTimeoutOrNull(DEFAULT_DISCOVERY_TIMEOUT) {
            serviceInfoChannel.receive()
        }
    }

    /**
     * Returns all services that have been resolved within the given timeout.
     */
    suspend fun resolveAll(timeoutMillis: Long = DEFAULT_COLLECT_TIMEOUT): List<ServiceInfo> = discover {
        val results = mutableListOf<ServiceInfo>()
        withTimeoutOrNull(timeoutMillis) {
            for (info in serviceInfoChannel) {
                results.add(info)
            }
        }
        results
    }

    private suspend fun <T> discover(block: suspend () -> T): T {
        try {
            multicastLock.acquire()
        } catch (e: SecurityException) {
            Log.e(TAG, "Could not acquire multicast lock", e)
        } catch (e: UnsupportedOperationException) {
            Log.e(TAG, "Could not acquire multicast lock", e)
        }

        Log.i(TAG, "Discovering services $serviceTypes")

        try {
            withContext(Dispatchers.IO) {
                try {
                    jmDns = JmDNS.create(localIpv4Address)
                } catch (e: SocketException) {
                    Log.e(TAG, "Error creating JmDNS instance", e)
                    return@withContext
                } catch (e: BindException) {
                    Log.e(TAG, "Error creating JmDNS instance", e)
                    return@withContext
                }
                serviceTypes.forEach { type -> jmDns?.addServiceListener(type, this@AsyncServiceResolver) }
            }

            return block()
        } finally {
            if (multicastLock.isHeld) {
                multicastLock.release()
            }
            withContext(NonCancellable + Dispatchers.IO) {
                jmDns?.close()
                jmDns = null
            }
        }
    }

    override fun serviceAdded(event: ServiceEvent) {
        Log.d(TAG, "Service added ${event.name}")
        jmDns?.requestServiceInfo(event.type, event.name, 1)
    }

    override fun serviceRemoved(event: ServiceEvent) {}

    override fun serviceResolved(event: ServiceEvent) {
        scope.launch {
            serviceInfoChannel.trySend(event.info)
        }
    }

    companion object {
        private val TAG = AsyncServiceResolver::class.java.simpleName

        private const val DEFAULT_DISCOVERY_TIMEOUT = 3000L
        private const val DEFAULT_COLLECT_TIMEOUT = 6000L
        const val OPENHAB_SERVICE_TYPE = "_openhab-server-ssl._tcp.local."
        const val OPENHAB_SERVICE_TYPE_HTTP = "_openhab-server._tcp.local."
    }
}
