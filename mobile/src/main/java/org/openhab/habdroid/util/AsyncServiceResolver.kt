/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.util

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.MulticastLock
import android.os.Handler
import android.os.Looper
import android.util.Log

import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Enumeration

import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

class AsyncServiceResolver(context: Context, private val listener: Listener, private val serviceType: String) :
        Thread(), ServiceListener {

    // Multicast lock for mDNS
    private val multicastLock: MulticastLock
    // mDNS service
    private var jmdns: JmDNS? = null
    private var resolvedServiceInfo: ServiceInfo? = null
    private val handler: Handler

    private val localIpv4Address: InetAddress?
        get() {
            try {
                val en = NetworkInterface.getNetworkInterfaces()
                while (en.hasMoreElements()) {
                    val intf = en.nextElement()
                    val enumIpAddr = intf.inetAddresses
                    while (enumIpAddr.hasMoreElements()) {
                        val inetAddress = enumIpAddr.nextElement()
                        Log.i(TAG, "IP: " + inetAddress.hostAddress.toString())
                        Log.i(TAG, "Is IPV4 = " + (inetAddress is Inet4Address))
                        if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                            Log.i(TAG, "Selected " + inetAddress.getHostAddress())
                            return inetAddress
                        }
                    }
                }
            } catch (ex: SocketException) {
                Log.e(TAG, ex.toString())
            }

            return null
        }

    interface Listener {
        fun onServiceResolved(serviceInfo: ServiceInfo)
        fun onServiceResolveFailed()
    }

    init {
        handler = Handler(Looper.getMainLooper())

        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("HABDroidMulticastLock")
        multicastLock.setReferenceCounted(true)
    }

    override fun run() {
        try {
            multicastLock.acquire()
        } catch (e: SecurityException) {
            Log.i(TAG, "Could not acquire multicast lock", e)
        }

        Log.i(TAG, "Discovering service $serviceType")
        try {
            /* TODO: This is a dirty fix of some crazy ipv6 incompatibility
               This workaround makes JMDNS work on local ipv4 address an thus
               discover openHAB on ipv4 address. This should be fixed to fully
               support ipv6 in future. */
            jmdns = JmDNS.create(localIpv4Address)
            jmdns?.addServiceListener(serviceType, this)
        } catch (e: IOException) {
            Log.e(TAG, e.message)
        }

        try {
            // Sleep for specified timeout
            Thread.sleep(DEFAULT_DISCOVERY_TIMEOUT.toLong())
            if (resolvedServiceInfo == null) {
                handler.post { listener.onServiceResolveFailed() }
                shutdown()
            }
        } catch (ignored: InterruptedException) {
            // ignored
        }

    }

    override fun serviceAdded(event: ServiceEvent) {
        Log.d(TAG, "Service added " + event.name)
        jmdns?.requestServiceInfo(event.type, event.name, 1)
    }

    override fun serviceRemoved(event: ServiceEvent) {}

    override fun serviceResolved(event: ServiceEvent) {
        val info = event.info
        resolvedServiceInfo = info
        if (info != null) {
            handler.post { listener.onServiceResolved(info) }
        }
        shutdown()
        interrupt()
    }

    private fun shutdown() {
        multicastLock.release()
        val jmdns = this.jmdns;
        if (jmdns != null) {
            jmdns.removeServiceListener(serviceType, this)
            try {
                jmdns.close()
            } catch (e: IOException) {
                // ignore
            }
            this.jmdns = null
        }
    }

    companion object {
        private val TAG = AsyncServiceResolver::class.java.simpleName

        private val DEFAULT_DISCOVERY_TIMEOUT = 3000
    }
}
