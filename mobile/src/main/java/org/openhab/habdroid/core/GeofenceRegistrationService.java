package org.openhab.habdroid.core;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

import org.openhab.habdroid.util.Constants;

/**
 * Created by jjhuff on 8/29/17.
 */

public class GeofenceRegistrationService extends OpenHABIntentService implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener  {
    private static final String TAG = GeofenceRegistrationService.class.getSimpleName();
    private GoogleApiClient mApiClient;

    public GeofenceRegistrationService() {
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Setup API client for LocationServices
        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mApiClient.connect();
    }

    /**
     * Google API service connection failed in a non-recoverable way.
     * @param connectionResult
     */
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "Connection to Google Play services failed with error: " + connectionResult.getErrorMessage());
    }

    /**
     * Once the Google API service connection is available, send a request to add the Geofences.
     */
    @Override
    @SuppressWarnings("MissingPermission")
    public void onConnected(Bundle connectionHint) {
        Log.d(TAG, "onConnected");

        float lat;
        float lng;
        try {
            lat = Float.parseFloat(mSettings.getString(Constants.PREFERENCE_PRESENCE_LAT, "0"));
            lng = Float.parseFloat(mSettings.getString(Constants.PREFERENCE_PRESENCE_LNG, "0"));
        } catch(NumberFormatException e) {
            Log.i(TAG, "Invalid lat/lng");
            return;
        }

        LocationServices.GeofencingApi.addGeofences(mApiClient,
                getGeofencingRequest(lat, lng), getGeofencePendingIntent()).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status status) {
                if(status.isSuccess()){
                    Log.d(TAG, "Registration success");
                }else{
                    Log.e(TAG, "Registration failure");
                }
            }
        });
    }

    @Override
    public void onConnectionSuspended (int cause) {
        Log.d(TAG, "onConnectionSuspended");
    }



    /**
     * Return the geofence request
     * @return
     */
    private GeofencingRequest getGeofencingRequest(float lat, float lng) {
        Geofence geofence;
        geofence = new Geofence.Builder()
                .setRequestId("home")
                .setCircularRegion(lat, lng, 100)
                .setNotificationResponsiveness(30 * 1000 ) // 30 sec
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER |
                        Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();

        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER | GeofencingRequest.INITIAL_TRIGGER_EXIT);
        builder.addGeofence(geofence);
        return builder.build();
    }

    private PendingIntent getGeofencePendingIntent() {
        Intent intent = new Intent(this, GeofenceBroadcastReceiver.class);
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

}
