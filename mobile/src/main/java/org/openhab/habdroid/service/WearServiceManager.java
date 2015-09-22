package org.openhab.habdroid.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import org.openhab.habdroid.model.OpenHABSitemap;

/**
 * Created by tobiasamon on 08.04.15.
 */
public class WearServiceManager {

    private static final String TAG = WearServiceManager.class.getSimpleName();

    private static Context mContext;

    private WearBackgroundService mWearBackgroundService;

    private static final WearServiceManager instance = new WearServiceManager();

    private boolean mWearServiceBound = false;

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
            mWearServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "WearBackgroundService is disconnected");
            mWearBackgroundService = null;
            mWearServiceBound = false;
        }
    };

    public static void startService(Context context) {
        mContext = context;
        instance.internalStartService();
    }

    private void internalStartService() {
        Log.d(TAG, "Start services");
        mContext.startService(new Intent(mContext, WearBackgroundService.class));
    }

    public static void bindServices(Context context) {
        instance.bindServicesInternal(context);
    }

    private void bindServicesInternal(Context context) {
        Log.d(TAG, "Binding services for context " + context);
        mContext = context;
        bindWearService();
    }


    public static synchronized void unbindServices() {
        instance.unbindServicesInternal();
    }

    private void unbindServicesInternal() {
        Log.d(TAG, "Unbinding from services for context " + mContext);
        if (mWearBackgroundService != null && mWearServiceBound) {
            try {
                mContext.unbindService(mConnection);
                mWearBackgroundService = null;
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
        Log.i(TAG, "Wear Service is " + (mWearServiceBound ? "" : "not") + " bound");
        if (mWearBackgroundService == null) {
            Intent intent = new Intent(mContext, WearBackgroundService.class);
            mWearServiceBound = mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        } else {
            Log.d(TAG, "Do not bind wear service again");
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

    public static void setOpenHabBaseUrl(String openHABBaseUrl) {
        instance.internalSetOpenHabBaseUrl(openHABBaseUrl);
    }

    private void internalSetOpenHabBaseUrl(String openHABBaseUrl) {
        if(mWearBackgroundService != null) {
            mWearBackgroundService.setOpenHabBaseUrl(openHABBaseUrl);
        }
    }
}
