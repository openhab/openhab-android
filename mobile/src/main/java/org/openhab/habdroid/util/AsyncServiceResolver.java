/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.util;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

public class AsyncServiceResolver extends Thread implements ServiceListener {
    private static final String TAG = AsyncServiceResolver.class.getSimpleName();

    public interface Listener {
        void onServiceResolved(ServiceInfo serviceInfo);
        void onServiceResolveFailed();
    }

    // Multicast lock for mDNS
    private MulticastLock mMulticastLock;
    // mDNS service
    private JmDNS mJmdns;
    private String mServiceType;
    private ServiceInfo mResolvedServiceInfo;
    private Listener mListener;
    private Handler mHandler;

    private static final int DEFAULT_DISCOVERY_TIMEOUT = 3000;

    public AsyncServiceResolver(Context context, Listener listener, String serviceType) {
        super();
        mServiceType = serviceType;
        mListener = listener;
        mHandler = new Handler(Looper.getMainLooper());

        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mMulticastLock = wifiManager.createMulticastLock("HABDroidMulticastLock");
        mMulticastLock.setReferenceCounted(true);
    }

    @Override
    public void run() {
        try {
            mMulticastLock.acquire();
        } catch (SecurityException e) {
            Log.i(TAG, "Could not acquire multicast lock", e);
        }

        Log.i(TAG, "Discovering service " + mServiceType);
        try {
            /* TODO: This is a dirty fix of some crazy ipv6 incompatibility
               This workaround makes JMDNS work on local ipv4 address an thus
               discover openHAB on ipv4 address. This should be fixed to fully
               support ipv6 in future. */
            mJmdns = JmDNS.create(getLocalIpv4Address());
            mJmdns.addServiceListener(mServiceType, this);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }

        try {
            // Sleep for specified timeout
            Thread.sleep(DEFAULT_DISCOVERY_TIMEOUT);
            if (mResolvedServiceInfo == null) {
                mHandler.post(() -> mListener.onServiceResolveFailed());
                shutdown();
            }
        } catch (InterruptedException ignored) {
            // ignored
        }
    }

    @Override
    public void serviceAdded(ServiceEvent event) {
        Log.d(TAG, "Service added " + event.getName());
        mJmdns.requestServiceInfo(event.getType(), event.getName(), 1);
    }

    @Override
    public void serviceRemoved(ServiceEvent event) {
    }

    @Override
    public void serviceResolved(ServiceEvent event) {
        mResolvedServiceInfo = event.getInfo();
        mHandler.post(() -> mListener.onServiceResolved(mResolvedServiceInfo));
        shutdown();
        interrupt();
    }

    private void shutdown() {
        mMulticastLock.release();
        if (mJmdns != null) {
            mJmdns.removeServiceListener(mServiceType, this);
            try {
                mJmdns.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private InetAddress getLocalIpv4Address() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                    en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
                        enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    Log.i(TAG, "IP: " + inetAddress.getHostAddress().toString());
                    Log.i(TAG, "Is IPV4 = " + (inetAddress instanceof Inet4Address));
                    if (!inetAddress.isLoopbackAddress() && (inetAddress instanceof Inet4Address)) {
                        Log.i(TAG, "Selected " + inetAddress.getHostAddress());
                        return inetAddress;
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e(TAG, ex.toString());
        }
        return null;
    }
}
