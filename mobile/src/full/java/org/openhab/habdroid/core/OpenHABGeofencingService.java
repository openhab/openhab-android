package org.openhab.habdroid.core;

import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.CloudConnection;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.util.AsyncHttpClient;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import okhttp3.Headers;
import okhttp3.Request;

public class OpenHABGeofencingService extends IntentService implements ConnectionFactory.UpdateListener {
    private static final String TAG = OpenHABGeofencingService.class.getSimpleName();
    private Handler mHandler = new Handler(Looper.getMainLooper());

    private Connection mConnection;

    public OpenHABGeofencingService() {super("OpenHABGeofencingService");}

    //private GeofencingClient mGeofencingClient;

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {

        ConnectionFactory.waitForInitialization();
        Connection connection = null;

        try {
            connection = ConnectionFactory.getUsableConnection();
        } catch (ConnectionException e) {
            Log.w(TAG, "Couldn't determine openHAB URL", e);
        }

        showToast("Geofencing Intent");

        Log.d(TAG, "Geofencing Intent");

        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.geofencing_request_location_permission_dialog_title);
            String description = getString(R.string.geofencing_request_location_permission);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(getResources().getString(R.string.geofencing_request_location_permission), name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        String NotiString = "";
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent.hasError()) {
            NotiString = "ERROR";
        }
        // Get the transition type.
        int geofenceTransition = geofencingEvent.getGeofenceTransition();

        // Test that the reported transition was of interest.
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER)
            NotiString = "GEOFENCE_TRANSITION_ENTER";
        else if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT)
            NotiString = "GEOFENCE_TRANSITION_EXIT";

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext(), getResources().getString(R.string.geofencing_request_location_permission))
                .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                .setContentTitle("GEOFENCE")
                .setContentText(NotiString)
                .setPriority(NotificationCompat.PRIORITY_MAX);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());

        // notificationId is a unique int for each notification that you must define
        int notificationId = 47;
        notificationManager.notify(notificationId, mBuilder.build());

        if (connection != null) {
            //Util.sendItemCommand(mConnection.getAsyncHttpClient(),"rest/items/lamp_room_desk","ON");
            //sendVoiceCommand(connection.getSyncHttpClient(), voiceCommand);
        } else {
            //showToast(getString(R.string.error_couldnt_determine_openhab_url));
        }
    }

    public static boolean loadGeofencesFromServer(final List<Geofence> geofenceList) {

        Connection connection = null;

        try {
            connection = ConnectionFactory.getUsableConnection();
        } catch (ConnectionException e) {
            Log.w(TAG, "Couldn't determine openHAB URL", e);
        }

        if (connection != null) {
            AsyncHttpClient client = connection.getAsyncHttpClient();

            final HashMap<String, String> headers = new HashMap<>();
            headers.put("Accept-Language", Locale.getDefault().getLanguage());

            client.get("rest/items?type=Location&recursive", new AsyncHttpClient.StringResponseHandler() {
                @Override
                public void onFailure(Request request, int statusCode, Throwable error) {
                    Log.d(TAG, "Getting Items failed with "+statusCode);
                }
                @Override
                public void onSuccess(String body, Headers headers) {
                    //JSONObject jsonObject = ;
                    JSONArray jsonArray;
                    try {
                        Log.d(TAG,body);
                        jsonArray = new JSONArray(body);
                        //jsonObject.getJSONArray();

                        Log.d(TAG,jsonArray.toString(4));

                        for(int i = 0;i<jsonArray.length();i++){
                            JSONObject jsonObject = jsonArray.getJSONObject(i);
                            String requestID = jsonObject.optString("name");
                            String [] loc = jsonObject.optString("state","0.0,0.0").split(",");
                            double latitude  = Double.parseDouble(loc[0]);
                            double longitude = Double.parseDouble(loc[1]);
                            float radius = 100;// in meters
                            geofenceList.add(new Geofence.Builder()
                                    // Set the request ID of the geofence. This is a string to identify this geofence.
                                    .setRequestId(requestID)
                                    .setCircularRegion(
                                            latitude,
                                            longitude,
                                            radius
                                    )
                                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER |
                                            Geofence.GEOFENCE_TRANSITION_EXIT)
                                    .build());
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                        Log.e(TAG,"Could not parse JSON");
                        return;
                    }


                }
            });
           // post("rest/voice/interpreters",command, "text/plain", headers).asStatus();

        } else {
            return false;
            //showToast(getString(R.string.error_couldnt_determine_openhab_url));
        }
        return true;
    }


    //@Override
    public void onCreate(Bundle savedInstanceState) {

        //ConnectionFactory.addListener(this);

    }

    private void showToast(CharSequence text) {
        mHandler.post(() -> Toast.makeText(this, text, Toast.LENGTH_LONG).show());
    }

    @Override
    public void onAvailableConnectionChanged() {

        Log.d(TAG,"onAvailableConnectionChanged");
        Connection newConnection;
        ConnectionException failureReason;

        try {
            newConnection = ConnectionFactory.getUsableConnection();
           // failureReason = null;
        } catch (ConnectionException e) {
            newConnection = null;
            //failureReason = e;
        }


        if (newConnection != null && newConnection == mConnection) {
            return;
        }


        mConnection = newConnection;
        //setupGeofences();
    }

    @Override
    public void onCloudConnectionChanged(CloudConnection connection) {

    }
}
