package org.openhab.habdroid.service;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
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

    private OpenHABConnectionService mConnectionService;
    private boolean mConnectionServiceBound = false;
    private ServiceConnection mConnectionServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            OpenHABConnectionService.LocalBinder binder = (OpenHABConnectionService.LocalBinder) service;
            mConnectionService = binder.getService();
            mConnectionServiceBound = true;

            mWearService = new WearService(getApplicationContext(), mConnectionService.getBaseUrl());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mConnectionServiceBound = false;
        }
    };

    public WearBackgroundService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        new BindConnectionServiceAsyncTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        return mBinder;
    }

    public void setSitemapForWearable(OpenHABSitemap openHABSitemap) {
        mWearService.setSitemapForWearable(openHABSitemap);
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

    private class BindConnectionServiceAsyncTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            Intent intent = new Intent(getApplicationContext(), OpenHABConnectionService.class);
            bindService(intent, mConnectionServiceConnection, Context.BIND_AUTO_CREATE);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            new ConnectWearServiceAsyncTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    private class ConnectWearServiceAsyncTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            while(!mConnectionServiceBound) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.v(TAG, "Interrupted");
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Log.d(TAG, "ConnectionService is bound -> connect wearservice");
            mWearService.connect();
        }
    }
}
