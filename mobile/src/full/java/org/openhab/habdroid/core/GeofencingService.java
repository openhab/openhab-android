package org.openhab.habdroid.core;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.model.OpenHABGeofence;
import org.openhab.habdroid.ui.OpenHABGeofenceFragment;
import org.openhab.habdroid.util.AsyncHttpClient;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import okhttp3.Headers;
import okhttp3.Request;

public class GeofencingService extends IntentService {
    private static final String TAG = GeofencingService.class.getSimpleName();
    private static PendingIntent sGeofencePendingIntent;
    private static List<OpenHABGeofence> sGeofenceList;

    public static List<OpenHABGeofence> getGeofences(Context context) {
        return loadOpenHABGeofencesFromMemory(context);
    }

    public static boolean isGeofenceFeatureAvailable() {
        return true;
    }

    public static class GeofenceNameNotUnique extends IllegalArgumentException {

        public GeofenceNameNotUnique() {
            super("Name of geofence is not unique!");
        }
    }

    /**
     * Adds a new Geofence.
     * Registers it on the google location API and saves list in memory.
     * @param activity
     * @param newOpenHABGeofence
     * @throws GeofenceNameNotUnique
     */
    public static void addGeofence(Activity activity, OpenHABGeofence newOpenHABGeofence) throws GeofenceNameNotUnique {
        if (loadOpenHABGeofencesFromMemory(activity).contains(newOpenHABGeofence)) {
            throw new GeofenceNameNotUnique();//Geofence name already registered
        }
        List<OpenHABGeofence> newOpenHABGeofences = new ArrayList<>(1);
        newOpenHABGeofences.add(newOpenHABGeofence);
        registerGeofences(activity,newOpenHABGeofences);
    }

    /**
     * removes the geofences form memory and unregisters them from the google location API.
     * @param activity
     * @param geofencesForRemoval
     */
    public static void removeGeofences(Activity activity, List<OpenHABGeofence> geofencesForRemoval) {
        if(geofencesForRemoval.isEmpty()) {return;}
        GeofencingClient geofencingClient = LocationServices.getGeofencingClient(activity);
        List<OpenHABGeofence> activeOpenHABGeofences = loadOpenHABGeofencesFromMemory(activity);
        activeOpenHABGeofences.removeAll(geofencesForRemoval);
        ArrayList<String> geofenceIDsForRemoval = new ArrayList<>(geofencesForRemoval.size());
        for(OpenHABGeofence geo:geofencesForRemoval) geofenceIDsForRemoval.add(geo.getName());
        geofencingClient.removeGeofences(geofenceIDsForRemoval);// google ID = OH2 name
        storeGeofencesInMemory(activity,geofencesForRemoval);
        broadcastUpdateGeofencesList(activity.getApplicationContext());
    }

    @SuppressLint("MissingPermission")
    private static void registerGeofences(Activity activity, List<OpenHABGeofence> newOpenHABGeofences) {
        if (!hasLocationPermissions(activity)) {
            requestLocationPermissionWithRationale(activity);
            return;
        }
        GeofencingClient geofencingClient = LocationServices.getGeofencingClient(activity);
        List<Geofence> googleGeofences = createGoogleGeofencesFromOpenHABGeofences(newOpenHABGeofences);
        geofencingClient.addGeofences(getGeofencingRequest(googleGeofences), getGeofencePendingIntent(activity))
                .addOnSuccessListener(activity, aVoid -> {
                    Log.d(TAG, "geofences added");
                    //Send a broadcast to the Fragment so it Updates the view and adds the new geofence.
                    broadcastUpdateGeofencesList(activity.getApplicationContext());
                    //Save the Geofences so they don't get lost when this Service gets destroyed.
                    List<OpenHABGeofence> allOpenHABGefences = loadOpenHABGeofencesFromMemory(activity);
                    allOpenHABGefences.addAll(newOpenHABGeofences);
                    storeGeofencesInMemory(activity,allOpenHABGefences);
                })
                .addOnFailureListener(activity, e -> {
                    String errorMsg = "Failed to add geofences to Google location API";
                    Log.e(TAG, errorMsg);
                    showToast(activity,errorMsg);
                    loadOpenHABGeofencesFromMemory(activity).removeAll(newOpenHABGeofences);//Maybe not necessary
                });
    }

    /**
     * Unregisters all OpenHABGeofences form the google location API.
     * DOES NOT delete them form the memory or ui,
     * so calling {@link GeofencingService#registerAllGeofences(Activity)} reverts the effect.
     * @param context
     */
    public static void unregisterAllGeofences(Context context) {
        GeofencingClient geofencingClient = LocationServices.getGeofencingClient(context);
        geofencingClient.removeGeofences(getGeofencePendingIntent(context));
    }
    /**
     * Registers all OpenHABGeofences that are stored in Momory at the google location API.
     * Calling {@link GeofencingService#unregisterAllGeofences(Context)} reverts the effect.
     * @param activity
     */
    public static void registerAllGeofences(Activity activity) {
        GeofencingClient geofencingClient = LocationServices.getGeofencingClient(activity);
        registerGeofences(activity,loadOpenHABGeofencesFromMemory(activity));
    }

    /**
     * Stores the List of OpenHABGeofences in the SharedPreferences
     * @param context
     * @param openHABGeofences the list to store
     */
    private static void storeGeofencesInMemory(Context context,List<OpenHABGeofence> openHABGeofences) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(context.getResources().getString(R.string.geofencing_preferences),
                        getJSONArrayFromGeofences(openHABGeofences).toString()).apply();
    }

    /**
     * Loads the List of OpenHABGeofences from memory if it isn't already loaded.
     * @param context
     * @return the list
     */
    private static List<OpenHABGeofence> loadOpenHABGeofencesFromMemory(Context context) {
        if (sGeofenceList != null) return sGeofenceList;
        try {
            JSONArray loadedOpenHABGeofencesJSONArray = new JSONArray(
                    PreferenceManager.getDefaultSharedPreferences(context)
                            .getString(context.getResources().getString(R.string.geofencing_preferences),"[]"));
            sGeofenceList = loadGeofencesFromJSONArray(loadedOpenHABGeofencesJSONArray);
            Log.d(TAG, "Geofences loaded from SharedPreferences:\n"+loadedOpenHABGeofencesJSONArray.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Couldn't load Geofences form SharedPreferences\n"+e.getLocalizedMessage());
            sGeofenceList = new ArrayList<>(0);
        }
        return sGeofenceList;
    }

    /**
     * return the openhabgeofence with the google id (= name of geofence) or null if not found
     * @param applicationContext
     * @param requestId
     */
    private OpenHABGeofence getGeofenceByID(Context applicationContext, String requestId) {
        for(OpenHABGeofence geo:loadOpenHABGeofencesFromMemory(applicationContext)) {
            if(geo.getName().equals(requestId))
                return geo;
        }
        return null;

    }

    private static boolean hasLocationPermissions(Context context){
        return (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
    }

    private static void requestLocationPermissionWithRationale(Activity activity) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(activity)
                    .setTitle(R.string.permission_location__geofencing_request_dialog_title)
                    .setMessage(R.string.permission_location__geofencing_request);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                dialogBuilder.setOnDismissListener(dialog -> requestLocationPermission(activity));
            } else {
                requestLocationPermission(activity);
            }
            dialogBuilder.show();
        } else {
            // No explanation needed; request the permission
            requestLocationPermission(activity);
        }
    }

    private static void requestLocationPermission(Activity activity) {
        ActivityCompat.requestPermissions(activity,new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, Constants.REQUEST_LOCATION_REQUEST_CODE);
    }

    private static void broadcastUpdateGeofencesList(Context context) {
        Intent intent = new Intent(OpenHABGeofenceFragment.ACTION_UPDATE_VIEW);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private static JSONArray getJSONArrayFromGeofences(List<OpenHABGeofence> openHABGeofences) {
        ArrayList<JSONObject> ar= new ArrayList<>(openHABGeofences.size());
        for (OpenHABGeofence j:openHABGeofences) {
            try {
                ar.add(j.toJSON());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return new JSONArray(ar);
    }

    private static List<OpenHABGeofence> loadGeofencesFromJSONArray(JSONArray ar) {
        int len = ar.length();
        List<OpenHABGeofence> ret = new ArrayList<OpenHABGeofence>(len);
        for(int i = 0;i<len;i++) {
            try {
                ret.add(OpenHABGeofence.fromJSON(ar.getJSONObject(i)));
            } catch (JSONException e) {
                Log.e(TAG,"JSON Parsing of Geofences failed!");
            }
        }
        return ret;
    }

    private static List<Geofence> createGoogleGeofencesFromOpenHABGeofences(Collection<OpenHABGeofence> openHABGeofences) {
        ArrayList<Geofence> geofences = new ArrayList<>(openHABGeofences.size());
        for (OpenHABGeofence openHABGeofence:openHABGeofences)
            geofences.add(new Geofence.Builder()
                    // Set the request ID of the geofence. This is a string to identify this geofence.
                    // We use the openHAB item name for that purpose as it also should be unique.
                    .setRequestId(openHABGeofence.getName())
                    .setCircularRegion(
                            openHABGeofence.getLatitude(),
                            openHABGeofence.getLongitude(),
                            openHABGeofence.getRadius()
                    )
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER |
                            Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build());
        return geofences;
    }

    /**
     *
     * @param geofences the list of geofences that should be added (has to not be empty!)
     * @return
     */
    private static GeofencingRequest getGeofencingRequest(List<Geofence> geofences) {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofences(geofences);
        return builder.build();
    }

    private static PendingIntent getGeofencePendingIntent(Context context) {
        // Reuse the PendingIntent if we already have it.
        if (sGeofencePendingIntent != null) {
            return sGeofencePendingIntent;
        }
        Intent intent = new Intent(context, GeofencingService.class);
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when
        // calling addGeofences() and removeGeofences().
        sGeofencePendingIntent = PendingIntent.getService(context, 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        return sGeofencePendingIntent;
    }

    public GeofencingService() {super("GeofencingService");
        Log.d(TAG,"Geo Service Constructed ");}

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        ConnectionFactory.waitForInitialization();
        Connection connection = null;

        try {
            connection = ConnectionFactory.getUsableConnection();
        } catch (ConnectionException e) {
            Log.w(TAG, "Couldn't determine openHAB URL", e);
            makeNotification(this.getApplicationContext(),"Geo Error","Couldn't determine openHAB URL",notiCounter++);
        }
        Log.d(TAG, "Geofencing Intent");

        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        // Get the transition type.
        int geofenceTransition = geofencingEvent.getGeofenceTransition();

        // Test that the reported transition was of interest.
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            makeNotification(getApplicationContext(),"Entered Fence",geofencingEvent.getTriggeringGeofences().get(0).getRequestId(),notiCounter++);
            Util.sendItemCommand(connection.getAsyncHttpClient(),
                    getGeofenceByID(getApplicationContext(),geofencingEvent.getTriggeringGeofences().get(0).getRequestId()).getOpenHABItem(),
                    "ON");

        }
        else if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            makeNotification(getApplicationContext(),"Exited Fence",geofencingEvent.getTriggeringGeofences().get(0).getRequestId(),notiCounter++);
        }
    }
    private static int notiCounter = 0;

    //TODO change this to send the new geofence status to the openHAB server as a item
    private static void makeNotification(Context c, String title, String text, int id) {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(c, c.getResources().getString(R.string.permission_location__geofencing_request))
                .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_MAX);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(c);
        notificationManager.notify(id, mBuilder.build());
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
                            //Do someting
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                        Log.e(TAG,"Could not parse JSON");
                        return;
                    }


                }
            });

        } else {
            return false;
        }
        return true;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG,"Geo Service Destroyed ");
        super.onDestroy();
    }

    private static void showToast(Context context,CharSequence text) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, text, Toast.LENGTH_SHORT).show());
    }
}
