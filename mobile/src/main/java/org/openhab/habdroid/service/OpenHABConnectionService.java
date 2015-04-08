package org.openhab.habdroid.service;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.OpenHABTracker;
import org.openhab.habdroid.core.OpenHABTrackerReceiver;
import org.openhab.habdroid.util.URLAware;

import java.util.ArrayList;
import java.util.List;

public class OpenHABConnectionService extends Service implements OpenHABTrackerReceiver {

    private static final String TAG = OpenHABConnectionService.class.getSimpleName();

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    // openHAB Bonjour service name
    private String openHABServiceType;
    // If openHAB discovery is enabled
    private boolean mServiceDiscoveryEnabled = true;

    private List<URLAware> urlAwareObjects = new ArrayList<URLAware>();

    private OpenHABTracker mTracker;

    private String mBaseUrl;

    public OpenHABConnectionService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Fetch openHAB service type name from strings.xml
        openHABServiceType = getString(R.string.openhab_service_type);

        checkDiscoveryPermissions();

        mTracker = new OpenHABTracker(this, openHABServiceType, mServiceDiscoveryEnabled);
        mTracker.start();

        return mBinder;
    }

    public String getBaseUrl() {
        return mBaseUrl;
    }

    public void checkDiscoveryPermissions() {
        // Check if we got all needed permissions
        PackageManager pm = getPackageManager();
        if (!(pm.checkPermission(Manifest.permission.CHANGE_WIFI_MULTICAST_STATE, getPackageName()) == PackageManager.PERMISSION_GRANTED)) {
            mServiceDiscoveryEnabled = false;
        }
        if (!(pm.checkPermission(Manifest.permission.ACCESS_WIFI_STATE, getPackageName()) == PackageManager.PERMISSION_GRANTED)) {
            mServiceDiscoveryEnabled = false;
        }
    }


    @Override
    public void onOpenHABTracked(String baseUrl, String message) {
        mBaseUrl = baseUrl;
        Log.d(TAG, "onOpenHABTracked - notifying '" + urlAwareObjects.size() + "' items");
        for (URLAware urlAware : urlAwareObjects) {
            urlAware.urlChanged(baseUrl);
        }
    }

    @Override
    public void onError(String error) {

    }

    @Override
    public void onBonjourDiscoveryStarted() {

    }

    @Override
    public void onBonjourDiscoveryFinished() {

    }

    public void addUrlCallback(URLAware urlAware) {
        Log.d(TAG, "addUrlCallback");
        if (!urlAwareObjects.contains(urlAware)) {
            urlAwareObjects.add(urlAware);
        }
    }

    public void removeCallback(URLAware urlAware) {
        if (urlAwareObjects.contains(urlAware)) {
            urlAwareObjects.remove(urlAware);
        }
    }


    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public OpenHABConnectionService getService() {
            // Return this instance of LocalService so clients can call public methods
            return OpenHABConnectionService.this;
        }
    }
}
