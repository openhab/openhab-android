/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.core;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.message.MessageHandler;
import org.openhab.habdroid.util.AsyncServiceResolver;
import org.openhab.habdroid.util.AsyncServiceResolverListener;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.Util;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.ExecutionException;

import javax.jmdns.ServiceInfo;

/**
 * This class provides openHAB discovery and continuous network state tracking to
 * change openHAB connectivity URL during app use if needed
 *
 * @author Victor Belov
 *
 */

public class OpenHABTracker implements AsyncServiceResolverListener {
    private final static String TAG = OpenHABTracker.class.getSimpleName();
    // Context in which openhabtracker is working
    Context mCtx;
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

    public OpenHABTracker(Context ctx, String serviceType) {
        mCtx = ctx;
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
            if (settings.getBoolean(Constants.PREFERENCE_DEMOMODE, false)) {
                mOpenHABUrl = "http://demo.openhab.org:8080/";
                Log.d(TAG, "Demo mode, url = " + mOpenHABUrl);
                openHABTracked(mOpenHABUrl);
                // todo add button that takes user to preferences
                openHABMessage(mCtx.getString(R.string.info_demo_mode_short), Constants.MESSAGES.SNACKBAR, Constants.MESSAGES.LOGLEVEL.ALWAYS);
                return;
            } else {
                // If we are on a mobile network go directly to remote URL from settings
                if (activeNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                    mOpenHABUrl = Util.normalizeUrl(settings.getString(Constants.PREFERENCE_ALTURL, ""));
                    // If remote URL is configured
                    if (mOpenHABUrl.length() > 0) {
                        Log.d(TAG, "Connecting to remote URL " + mOpenHABUrl);
                        openHABTracked(mOpenHABUrl);
                        openHABMessage(mCtx.getString(R.string.info_conn_rem_url), Constants.MESSAGES.SNACKBAR, Constants.MESSAGES.LOGLEVEL.LOCAL);

                    } else {
                        openHABMessage(mCtx.getString(R.string.error_no_url), Constants.MESSAGES.DIALOG, Constants.MESSAGES.LOGLEVEL.ALWAYS);
                    }
                // Else if we are on Wifi or Ethernet network
                } else if (activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI
                        || activeNetworkInfo.getType() == ConnectivityManager.TYPE_ETHERNET) {
                    // See if we have a local URL configured in settings
                    mOpenHABUrl = Util.normalizeUrl(settings.getString(Constants.PREFERENCE_URL, ""));
                    // If local URL is configured
                    if (mOpenHABUrl.length() > 0) {
                        // Check if configured local URL is reachable
                        if (checkUrlReachability(mOpenHABUrl)) {
                            Log.d(TAG, "Connecting to local URL = " + mOpenHABUrl);
                            openHABTracked(mOpenHABUrl);
                            openHABMessage(mCtx.getString(R.string.info_conn_url), Constants.MESSAGES.SNACKBAR, Constants.MESSAGES.LOGLEVEL.REMOTE);
                            return;
                        }
                    }
                    // If local URL is not reachable or not configured, try with remote URL
                    mOpenHABUrl = Util.normalizeUrl(settings.getString(Constants.PREFERENCE_ALTURL, ""));
                    if (mOpenHABUrl.length() > 0) {
                        Log.d(TAG, "Connecting to remote URL " + mOpenHABUrl);
                        openHABTracked(mOpenHABUrl);
                        openHABMessage(mCtx.getString(R.string.info_conn_rem_url), Constants.MESSAGES.SNACKBAR, Constants.MESSAGES.LOGLEVEL.LOCAL);
                    } else {
                        // if not URL is configured, start service discovery
                        mServiceResolver = new AsyncServiceResolver(mCtx, this, mOpenHABServiceType);
                        bonjourDiscoveryStarted();
                        mServiceResolver.start();
                    }
                // Else we treat other networks types as unsupported
                } else {
                    Log.e(TAG, "Network type (" + activeNetworkInfo.getTypeName() + ") is unsupported");
                    openHABMessage(String.format(mCtx.getString(R.string.error_network_type_unsupported), activeNetworkInfo.getTypeName()), Constants.MESSAGES.DIALOG, Constants.MESSAGES.LOGLEVEL.ALWAYS);
                }
            }
        } else {
            Log.e(TAG, "Network is not available");
            //todo disable progress indicator
            openHABMessage(mCtx.getString(R.string.error_network_not_available), Constants.MESSAGES.DIALOG, Constants.MESSAGES.LOGLEVEL.ALWAYS);
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
        openHABTracked(mOpenHABUrl);
    }

    public void onServiceResolveFailed() {
        bonjourDiscoveryFinished();
        Log.i(TAG, "Service resolve failed, switching to remote URL");
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mCtx);
        mOpenHABUrl = Util.normalizeUrl(settings.getString(Constants.PREFERENCE_ALTURL, ""));
        // If remote URL is configured
        if (mOpenHABUrl.length() > 0) {
            Log.d(TAG, "Connecting to remote URL " + mOpenHABUrl);
            openHABTracked(mOpenHABUrl);
            openHABMessage(mCtx.getString(R.string.info_conn_rem_url), Constants.MESSAGES.SNACKBAR, Constants.MESSAGES.LOGLEVEL.LOCAL);
        } else {
            openHABMessage(mCtx.getString(R.string.error_no_url), Constants.MESSAGES.DIALOG, Constants.MESSAGES.LOGLEVEL.ALWAYS);
        }
    }

    public static int getCurrentNetworkConnectivityType(Context ctx) {
        ConnectivityManager connectivityManager = (ConnectivityManager)ctx.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        if (activeNetworkInfo != null) {
            return activeNetworkInfo.getType();
        }
        return -1;
    }

    private boolean checkUrlReachability(String urlString) {
        Log.d(TAG, "Checking reachability of " + urlString);
        try {
            return new AsyncTask<String, Void, Boolean>() {
                @Override
                protected Boolean doInBackground(String... strings) {
                    try {
                        URL url = new URL(strings[0]);
                        int checkPort = url.getPort();
                        if (url.getProtocol().equals("http") && checkPort == -1)
                            checkPort = 80;
                        if (url.getProtocol().equals("https") && checkPort == -1)
                            checkPort = 443;
                        Socket s = new Socket();
                        s.connect(new InetSocketAddress(url.getHost(), checkPort), 1000);
                        Log.d(TAG, "Socket connected");
                        s.close();
                        return true;
                    } catch (Exception e) {
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

    private void openHABTracked(String openHABUrl) {
        if (mReceiver != null) {
            mReceiver.onOpenHABTracked(mOpenHABUrl);
        }
    }

    private void openHABMessage(String message, int messageType, int logLevel) {
        if (mReceiver != null) {
            if (!(mReceiver instanceof Activity)) {
                return;
            }
            MessageHandler.showMessageToUser((Activity) mReceiver, message, messageType, logLevel);
        }
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
