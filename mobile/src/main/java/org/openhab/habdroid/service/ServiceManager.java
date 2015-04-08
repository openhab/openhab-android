package org.openhab.habdroid.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import org.openhab.habdroid.model.OpenHABSitemap;
import org.openhab.habdroid.util.URLAware;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by tobiasamon on 08.04.15.
 */
public class ServiceManager {

    private static final String TAG = ServiceManager.class.getSimpleName();

    private static Context mContext;

    private static List<URLAware> urlAwareTemp = new ArrayList<URLAware>();

    private static WearBackgroundService mWearBackgroundService;
    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private static ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            Log.d(TAG, "WearBackgroundService is connected");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            WearBackgroundService.LocalBinder binder = (WearBackgroundService.LocalBinder) service;
            mWearBackgroundService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    };
    private static OpenHABConnectionService mOpenHABConnectionService;
    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private static ServiceConnection mOpenHABConnectionServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            OpenHABConnectionService.LocalBinder binder = (OpenHABConnectionService.LocalBinder) service;
            mOpenHABConnectionService = binder.getService();
            Log.d(TAG, "ConnectionService connected");
            if (!urlAwareTemp.isEmpty()) {
                Log.d(TAG, "Notifying '" + urlAwareTemp.size() + "' temporary items");
                for (URLAware urlAware : urlAwareTemp) {
                    mOpenHABConnectionService.addUrlCallback(urlAware);
                    urlAware.urlChanged(mOpenHABConnectionService.getBaseUrl());
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    };

    public static void bindServices(Context context) {
        mContext = context;
        bindWearService();
        bindConnectivityService();
    }

    /**
     * Bind the wear background service to enable communication between openhab and wear even if the app is nor running
     */
    private static void bindWearService() {
        Intent intent = new Intent(mContext, WearBackgroundService.class);
        mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Bind to the connection service which tracks connection to the openhab backend
     */
    private static void bindConnectivityService() {
        Intent intent = new Intent(mContext, OpenHABConnectionService.class);
        mContext.bindService(intent, mOpenHABConnectionServiceConnection, Context.BIND_AUTO_CREATE);
    }

    public static void setSitemapForWearable(OpenHABSitemap openHABSitemap) {
        if (mWearBackgroundService != null) {
            mWearBackgroundService.setSitemapForWearable(openHABSitemap);
        }
    }

    public static String getBaseUrl() {
        if (mOpenHABConnectionService != null) {
            return mOpenHABConnectionService.getBaseUrl();
        } else {
            return "";
        }
    }

    public static void addUrlCallback(URLAware urlAware) {
        if (mOpenHABConnectionService != null) {
            mOpenHABConnectionService.addUrlCallback(urlAware);
        } else {
            Log.d(TAG, "Adding " + urlAware + " to temporary list");
            if (!urlAwareTemp.contains(urlAware)) {
                urlAwareTemp.add(urlAware);
            }
        }
    }

    public static void removeCallback(URLAware urlAware) {
        if (mOpenHABConnectionService != null) {
            mOpenHABConnectionService.removeCallback(urlAware);
        } else {
            if (urlAwareTemp.contains(urlAware)) {
                urlAwareTemp.remove(urlAware);
            }
        }
    }
}
