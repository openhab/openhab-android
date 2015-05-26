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

    private WearBackgroundService mWearBackgroundService;

    private static final ServiceManager instance = new ServiceManager();

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection mConnection = new ServiceConnection() {

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
            Log.d(TAG, "WearService disconnected");
            mWearBackgroundService = null;
        }
    };

    private OpenHABConnectionService mOpenHABConnectionService;

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection mOpenHABConnectionServiceConnection = new ServiceConnection() {

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
            Log.d(TAG, "OpenHABConnectionService disconnected");
            mOpenHABConnectionService = null;
        }
    };

    public static void startServicesSticky(Context context) {
        mContext = context;
        instance.startServicesStickyInternal();
    }

    private void startServicesStickyInternal() {
        Log.d(TAG, "Start services");
        mContext.startService(new Intent(mContext, WearBackgroundService.class));
        mContext.startService(new Intent(mContext, OpenHABConnectionService.class));
    }

    public static void bindServices(Context context) {
        instance.bindServicesInternal(context);
    }

    private void bindServicesInternal(Context context) {
        Log.d(TAG, "Binding services for context " + context);
        mContext = context;
        bindWearService();
        bindConnectivityService();
    }


    public static synchronized void unbindServices() {
        instance.unbindServicesInternal();
    }

    private void unbindServicesInternal() {
        Log.d(TAG, "Unbinding from services for context " + mContext);
        if (mWearBackgroundService != null) {
            try {
                mContext.unbindService(mConnection);
                mWearBackgroundService = null;
            } catch (IllegalArgumentException iae) {
                Log.i(TAG, "Might not be bound", iae);
            }
        }
        if (mOpenHABConnectionService != null) {
            try {
                mOpenHABConnectionService.stopTracker();
                mContext.unbindService(mOpenHABConnectionServiceConnection);
                mOpenHABConnectionService = null;
            } catch (IllegalArgumentException iae) {
                Log.i(TAG, "Might not be bound", iae);
            }
        }
    }

    /**
     * Bind the wear background service to enable communication between openhab and wear even if the app is nor running
     */
    private void bindWearService() {
        Log.i(TAG, "Binding wear service " + (mWearBackgroundService == null ? "is null" : "already existing"));
        if (mWearBackgroundService == null) {
            Intent intent = new Intent(mContext, WearBackgroundService.class);
            mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        } else {
            Log.d(TAG, "Do not bind wear service again");
        }
    }

    /**
     * Bind to the connection service which tracks connection to the openhab backend
     */
    private void bindConnectivityService() {
        Log.i(TAG, "Binding openhab service " + (mOpenHABConnectionService == null ? "is null" : "already existing"));
        if (mOpenHABConnectionService == null) {
            Intent intent = new Intent(mContext, OpenHABConnectionService.class);
            mContext.bindService(intent, mOpenHABConnectionServiceConnection, Context.BIND_AUTO_CREATE);
        } else {
            Log.d(TAG, "Do not bind openhab service again");
        }
    }

    public static void setSitemapForWearable(OpenHABSitemap openHABSitemap) {
        instance.setSitemapForWearableInternal(openHABSitemap);
    }

    private void setSitemapForWearableInternal(OpenHABSitemap openHABSitemap) {
        if (mWearBackgroundService != null) {
            mWearBackgroundService.setSitemapForWearable(openHABSitemap);
        }
    }

    public static String getBaseUrl() {
        return instance.getBaseUrlInternal();
    }

    private String getBaseUrlInternal() {
        if (mOpenHABConnectionService != null) {
            return mOpenHABConnectionService.getBaseUrl();
        } else {
            return "";
        }
    }

    public static void addUrlCallback(URLAware urlAware) {
        instance.addUrlCallbackInternal(urlAware);
    }

    private void addUrlCallbackInternal(URLAware urlAware) {
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
        instance.removeCallbackInternal(urlAware);
    }

    private void removeCallbackInternal(URLAware urlAware) {
        if (mOpenHABConnectionService != null) {
            mOpenHABConnectionService.removeCallback(urlAware);
        } else {
            if (urlAwareTemp.contains(urlAware)) {
                urlAwareTemp.remove(urlAware);
            }
        }
    }
}
