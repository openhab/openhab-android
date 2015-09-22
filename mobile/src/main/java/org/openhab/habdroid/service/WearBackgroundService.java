package org.openhab.habdroid.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import org.openhab.habdroid.model.OpenHABSitemap;
import org.openhab.habdroid.wear.WearService;

public class WearBackgroundService extends Service {

    private static final String TAG = WearBackgroundService.class.getSimpleName();

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    private WearService mWearService;

    public WearBackgroundService() {
    }

/*    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        return START_STICKY;
    }
*/
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        mWearService = new WearService(getApplicationContext());
        mWearService.connect();
        return mBinder;
    }

    public void setSitemapForWearable(OpenHABSitemap openHABSitemap) {
        mWearService.setSitemapForWearable(openHABSitemap);
    }

    public void setOpenHabBaseUrl(String openHabBaseUrl) {
        mWearService.setOpenHabBaseUrl(openHabBaseUrl);
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public WearBackgroundService getService() {
            // Return this instance of LocalService so clients can call public methods
            return WearBackgroundService.this;
        }
    }
}
