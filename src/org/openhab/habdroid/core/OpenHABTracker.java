/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2012, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */
package org.openhab.habdroid.core;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;
import org.openhab.habdroid.R;
import org.openhab.habdroid.util.AsyncServiceResolver;
import org.openhab.habdroid.util.AsyncServiceResolverListener;
import org.openhab.habdroid.util.Util;

import javax.jmdns.ServiceInfo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;

/**
 * This class provides openHAB discovery and continuous network state tracking to
 * change openHAB connectivity URL during app use if needed
 *
 * @author Victor Belov
 *
 */

public class OpenHABTracker implements AsyncServiceResolverListener {
    private final static String TAG = "OpenHABTracker";
    // Context in which openhabtracker is working
    Context mCtx;
    // If bonjour discovery is enabled?
    boolean mDiscoveryEnabled;
    // receiver for openhabtracker notifications
    OpenHABTrackerReceiver mReceiver;
    // openHAB URL
    String mOpenHABUrl;
    // Bonjour service resolver
    AsyncServiceResolver mServiceResolver;
    // Bonjour openHAB service type
    String mOpenHABServiceType;
    // Receiver for connectivity tracking
    ConnectivityChangeReceiver mConnectivityChangeReceiver;

    public OpenHABTracker(Context ctx, String serviceType, boolean discoveryEnabled) {
        mCtx = ctx;
        mDiscoveryEnabled = discoveryEnabled;
        // If context is implementing our callback interface, set it as a receiver automatically
        if (ctx instanceof OpenHABTrackerReceiver) {
            mReceiver = (OpenHABTrackerReceiver)ctx;
        }
        // openHAB Bonjour service type
        mOpenHABServiceType = serviceType;
        // Create and register receiver for connectivity changes tracking
        mConnectivityChangeReceiver = new ConnectivityChangeReceiver();
    }

    /*
        This method engages tracker to start discovery process
     */

    public void start() {
        // Get preferences
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mCtx);
//        mCtx.registerReceiver(mConnectivityChangeReceiver,
//                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        // If demo mode is on, just go for demo server base URL ignoring other settings
        // Get current network information
        ConnectivityManager connectivityManager = (ConnectivityManager)mCtx.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        if (activeNetworkInfo != null) {
            if (settings.getBoolean("default_openhab_demomode", false)) {
                mOpenHABUrl = mCtx.getString(R.string.openhab_demo_url);
                Log.d(TAG, "Demo mode, url = " + mOpenHABUrl);
                openHABTracked(mOpenHABUrl, mCtx.getString(R.string.info_demo_mode));
                return;
            } else {
                // If we are on a mobile network go directly to remote URL from settings
                if (activeNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                    mOpenHABUrl = Util.normalizeUrl(settings.getString("default_openhab_alturl", ""));
                    // If remote URL is configured
                    if (mOpenHABUrl.length() > 0) {
                        Log.d(TAG, "Connecting to remote URL " + mOpenHABUrl);
                        openHABTracked(mOpenHABUrl, mCtx.getString(R.string.info_conn_rem_url));
                    } else {
                        openHABError(mCtx.getString(R.string.error_no_url));
                    }
                // Else if we are on Wifi or Ethernet network
                } else if (activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI
                        || activeNetworkInfo.getType() == ConnectivityManager.TYPE_ETHERNET) {
                    // See if we have a local URL configured in settings
                    mOpenHABUrl = Util.normalizeUrl(settings.getString("default_openhab_url", ""));
                    // If local URL is configured
                    if (mOpenHABUrl.length() > 0) {
                        // Check if configured local URL is reachable
                        if (checkUrlReachability(mOpenHABUrl)) {
                            Log.d(TAG, "Connecting to directly configured URL = " + mOpenHABUrl);
                            openHABTracked(mOpenHABUrl, mCtx.getString(R.string.info_conn_url));
                            return;
                            // If local URL is not reachable go with remote URL
                        } else {
                            mOpenHABUrl = Util.normalizeUrl(settings.getString("default_openhab_alturl", ""));
                            // If remote URL is configured
                            if (mOpenHABUrl.length() > 0) {
                                Log.d(TAG, "Connecting to remote URL " + mOpenHABUrl);
                                openHABTracked(mOpenHABUrl, mCtx.getString(R.string.info_conn_rem_url));
                            } else {
                                openHABError(mCtx.getString(R.string.error_no_url));
                            }
                        }
                    // If no local URL is configured
                    } else {
                        // Start service discovery
                        mServiceResolver = new AsyncServiceResolver(mCtx, this, mOpenHABServiceType);
                        bonjourDiscoveryStarted();
                        mServiceResolver.start();
                    }
                // Else we treat other networks types as unsupported
                } else {
                    Log.e(TAG, "Network type (" + activeNetworkInfo.getTypeName() + ") is unsupported");
                    openHABError("Network type (" + activeNetworkInfo.getTypeName() + ") is unsupported");
                }
            }
        } else {
            Log.e(TAG, "Network is not available");
            openHABError(mCtx.getString(R.string.error_network_not_available));
        }
    }

    public void stop() {
        try {
            mCtx.unregisterReceiver(mConnectivityChangeReceiver);
        } catch (RuntimeException e) {
            Log.d(TAG, e.getMessage());
        }
    }

    public void onServiceResolved(ServiceInfo serviceInfo) {
        bonjourDiscoveryFinished();
        Log.d(TAG, "Service resolved: "
                + serviceInfo.getHostAddresses()[0]
                + " port:" + serviceInfo.getPort());
        mOpenHABUrl = "https://" + serviceInfo.getHostAddresses()[0] + ":" +
                String.valueOf(serviceInfo.getPort()) + "/";
        openHABTracked(mOpenHABUrl, null);
    }

    public void onServiceResolveFailed() {
        bonjourDiscoveryFinished();
        Log.i(TAG, "Service resolve failed, switching to remote URL");
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mCtx);
        mOpenHABUrl = Util.normalizeUrl(settings.getString("default_openhab_alturl", ""));
        // If remote URL is configured
        if (mOpenHABUrl.length() > 0) {
            Log.d(TAG, "Connecting to remote URL " + mOpenHABUrl);
            openHABTracked(mOpenHABUrl, mCtx.getString(R.string.info_conn_rem_url));
        } else {
            openHABError(mCtx.getString(R.string.error_no_url));
        }
    }

    private boolean checkUrlReachability(String urlString) {
        Log.d(TAG, "Checking reachability of " + urlString);
        try {
            return new AsyncTask<String, Void, Boolean>() {
                @Override
                protected Boolean doInBackground(String... strings) {
                    try {
                        URL url = new URL(strings[0]);
                        Socket s = new Socket();
                        s.connect(new InetSocketAddress(url.getHost(), url.getPort()), 1000);
                        Log.d(TAG, "Socket connected");
                        s.close();
                        return true;
                    } catch (MalformedURLException e) {
                        Log.e(TAG, e.getMessage());
                        return false;
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage());
                        return false;
                    }
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, urlString).get();
        } catch (InterruptedException e) {
            Log.e(TAG, e.getMessage());
            return false;
        } catch (ExecutionException e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
    }

    private void openHABError(String error) {
        if (mReceiver != null)
            mReceiver.onError(error);
    }

    private void openHABTracked(String openHABUrl, String message) {
        if (mReceiver != null)
            mReceiver.onOpenHABTracked(mOpenHABUrl, message);
    }

    private void bonjourDiscoveryStarted() {
        if (mReceiver != null)
            mReceiver.onBonjourDiscoveryStarted();
    }

    private void bonjourDiscoveryFinished() {
        if (mReceiver != null)
            mReceiver.onBonjourDiscoveryFinished();
    }
}
