package org.openhab.habdroid.service;

import android.Manifest;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.loopj.android.http.TextHttpResponseHandler;

import org.apache.http.Header;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.OpenHABTracker;
import org.openhab.habdroid.core.OpenHABTrackerReceiver;

public class OpenHABConnectionService extends Service implements OpenHABTrackerReceiver {

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    // openHAB Bonjour service name
    private String openHABServiceType;
    // If openHAB discovery is enabled
    private boolean mServiceDiscoveryEnabled = true;

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
