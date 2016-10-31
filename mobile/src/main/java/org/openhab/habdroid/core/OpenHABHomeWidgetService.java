/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.core;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestHandle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.HomeWidgetProvider;
import org.openhab.habdroid.ui.OpenHABWidgetListFragment;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.ContinuingIntentService;
import org.openhab.habdroid.util.HomeWidgetUpdateJob;
import org.openhab.habdroid.util.HomeWidgetUtils;
import org.openhab.habdroid.util.MyAsyncHttpClient;

import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.StringEntity;
import cz.msebera.android.httpclient.message.BasicHeader;

/**
 * This service handles voice commands and sends them to OpenHAB.
 * It will use the openHAB base URL if passed in the intent's extra,
 * or else use the {@link OpenHABTracker} to discover openHAB itself.
 */
public class OpenHABHomeWidgetService extends ContinuingIntentService implements OpenHABTrackerReceiver {

    private static final String TAG = OpenHABHomeWidgetService.class.getSimpleName();
    public static final String OPENHAB_BASE_URL_EXTRA = "openHABBaseUrl";

    private String mOpenHABBaseUrl;
    private MyAsyncHttpClient mAsyncHttpClient;

    private OpenHABTracker mOpenHABTracker;
    private Queue<Intent> mBufferedIntents;
    private String mAtmosphereTrackingId;
    private RequestHandle mRequestHandle;

    public OpenHABHomeWidgetService() {
        super(TAG);
    }


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");

        mBufferedIntents = new LinkedList<Intent>();
        initHttpClient();
    }


    private void initHttpClient() {
        SharedPreferences mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        String username = mSettings.getString(Constants.PREFERENCE_USERNAME, null);
        String password = mSettings.getString(Constants.PREFERENCE_PASSWORD, null);
        mAsyncHttpClient = new MyAsyncHttpClient(this);
        mAsyncHttpClient.setBasicAuth(username, password);


        Log.d(TAG, "RECREATED SERVICE");
        subscribeForChanges("http://openhab:8080/rest/items");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent()");
        bufferIntent(intent);
        if (intent.hasExtra(OPENHAB_BASE_URL_EXTRA)) {
            Log.d(TAG, "openHABBaseUrl passed as Intent");
            onOpenHABTracked(intent.getStringExtra(OPENHAB_BASE_URL_EXTRA), null);
        } else if (mOpenHABTracker == null) {
            Log.d(TAG, "No openHABBaseUrl passed, starting OpenHABTracker");
            mOpenHABTracker = new OpenHABTracker(OpenHABHomeWidgetService.this, getString(R.string.openhab_service_type), false);
            mOpenHABTracker.start();
        }
    }

    /**
     * Buffers the {@link Intent} to be processed later when openHABBaseUrl has been determined by {@link OpenHABTracker}.
     *
     * Usually, the discovery of the openHABBaseUrl is fast enough, so there will be only one intent in the buffer
     * when the buffer is processed and this service is stopped.
     * However, it is not guaranteed that this service is only called once before openHABBaseUrl can be discovered.
     * Therefore all intents are buffered and later this buffer will be processed.
     *
     * @param intent The {@link Intent} to be buffered.
     */
    private void bufferIntent(Intent intent) {
        mBufferedIntents.add(intent);
    }

    @Override
    public void onOpenHABTracked(String baseUrl, String message) {
        Log.d(TAG, "onOpenHABTracked(): " + baseUrl + " " + message);
        mOpenHABBaseUrl = baseUrl;


        while (!mBufferedIntents.isEmpty()) {
            processWidgetIntent(mBufferedIntents.poll());
        }
        Log.d(TAG, "Stopping service for start ID " + getLastStartId());
        //stopSelf(getLastStartId());



    }

    public void subscribeForChanges(final String pageUrl){
            Log.i(TAG, " subscribe for " + pageUrl + " longPolling = ");
            // Cancel any existing http request to openHAB (typically ongoing long poll)

            List<BasicHeader> headers = new LinkedList<BasicHeader>();

            headers.add(new BasicHeader("X-Atmosphere-Framework", "1.0"));
            mAsyncHttpClient.setTimeout(300000);
            headers.add(new BasicHeader("X-Atmosphere-Transport", "long-polling"));
            if (this.mAtmosphereTrackingId == null) {
                headers.add(new BasicHeader("X-Atmosphere-tracking-id", "0"));
            } else {
                headers.add(new BasicHeader("X-Atmosphere-tracking-id", this.mAtmosphereTrackingId));
            }


            mRequestHandle = mAsyncHttpClient.get(getApplicationContext(), pageUrl, headers.toArray(new BasicHeader[] {}), null, new AsyncHttpResponseHandler() {
                @Override
                public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                    mAtmosphereTrackingId = null;

                    if (error instanceof SocketTimeoutException) {
                        Log.d(TAG, "Connection timeout, reconnecting");
                        //showPage(displayPageUrl, false);
                        return;
                    } else {
                    /*
                    * If we get a network error try connecting again, if the
                    * fragment is paused, the runnable will be removed
                    */
                        Log.e(TAG, error.getClass().toString());
                        Log.e(TAG, String.format("status code = %d", statusCode));
                        Log.e(TAG, "Connection error = " + error.getClass().toString() + ", cycle aborted");
//                            networkHandler.removeCallbacks(networkRunnable);
//                            networkRunnable =  new Runnable(){
//                                @Override
//                                public void run(){
                        //showPage(displayPageUrl, false);
//                                }
//                            };
//                            networkHandler.postDelayed(networkRunnable, 10 * 1000);
                    }
                }

                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                    for (int i=0; i<headers.length; i++) {
                        if (headers[i].getName().equalsIgnoreCase("X-Atmosphere-tracking-id")) {
                            Log.i(TAG, "Found atmosphere tracking id: " + headers[i].getValue());
                            OpenHABHomeWidgetService.this.mAtmosphereTrackingId = headers[i].getValue();
                        }
                    }

                    String responseString = new String(responseBody);
                    processChanges(responseString);
                    Log.d(TAG, responseString);
                    //subscribeForChanges(pageUrl);
                }
            });

    }



    private void processChanges(String responseString){
        JSONArray jArray = null;
        JSONObject jObject = null;
        String itemName;
        int widgetId;

        HashMap<String, HashMap<String, String>> widgetConfigs = getWidgetConfigs();

        try {
            jArray = new JSONArray(responseString);

            for(int i = 0; i < jArray.length(); i++){
                jObject = jArray.getJSONObject(i);
                try {
                    itemName = jObject.getString("name");

                    if(widgetConfigs.containsKey(itemName) && !widgetConfigs.get(itemName).get("lastState").equals(jObject.getString("state"))){
                        widgetId = Integer.parseInt(widgetConfigs.get(itemName).get("id"));
                        new HomeWidgetUpdateJob(getApplicationContext(), widgetId).execute();
                    }

                }catch (JSONException e){}
            }

        } catch (JSONException e) {
            Log.e("log_tag", "Error parsing data " + e.toString());
        }

    }


    private HashMap<String, HashMap<String, String>> getWidgetConfigs(){
        HashMap<String, HashMap<String, String>> configs = new HashMap<String, HashMap<String, String>>();

        int ids[] = AppWidgetManager.getInstance(this).getAppWidgetIds(new ComponentName(this,HomeWidgetProvider.class));

        for(int i : ids){
            HashMap<String, String> wdgtConfig = new HashMap<String, String>();

            wdgtConfig.put("lastState",HomeWidgetUtils.loadWidgetPrefs(getApplicationContext(), i, "lastState"));
            wdgtConfig.put("id",i+"");
            configs.put(HomeWidgetUtils.loadWidgetPrefs(getApplicationContext(), i, "name"),wdgtConfig);
        }

        return configs;
    }

    @Override
    public void onError(String error) {
        showToast(error);
        Log.d(TAG, "onError(): " + error);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        if (mOpenHABTracker != null) {
            mOpenHABTracker.stop();
        }
        super.onDestroy();
    }

    /**
     * Displays the given message as a toast
     *
     * @param message The message to be displayed.
     */
    private void showToast(final String message) {
        // Display toast on main looper because OpenHABVoiceService might be destroyed
        // before to toast has finished displaying
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onBonjourDiscoveryStarted() {
        Log.d(TAG, "onBonjourDiscoveryStarted()");
    }

    @Override
    public void onBonjourDiscoveryFinished() {
        Log.d(TAG, "onBonjourDiscoveryFinished()");
    }



    private void processWidgetIntent(Intent data) {


        if(data.hasExtra("item_name")) {

            String item = data.getStringExtra("item_name");
            String command = data.getStringExtra("item_command");
            if (mOpenHABBaseUrl != null) {
                sendItemCommand(item, command);
            } else {
                Log.w(TAG, "Couldn't determine OpenHAB URL");
                showToast("Couldn't determine OpenHAB URL");
            }

        }
    }



    private void sendItemCommand(final String itemName, final String command) {
        Log.d(TAG, "sendItemCommand(): itemName=" + itemName + ", command=" + command);
        try {
            performHttpPost(itemName, new StringEntity(command, "UTF-8"));
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to encode command " + command, e);
        }
    }

    private void performHttpPost(final String itemName, final StringEntity command) {
        /* Call MyAsyncHttpClient on the main UI thread in order to retrieve the callbacks correctly.
         * If calling MyAsyncHttpClient directly, the following would happen:
         * (1) MyAsyncHttpClient performs the HTTP post asynchronously
         * (2) OpenHABVoiceService stops because all intents have been handled
         * (3) MyAsyncHttpClient tries to call onSuccess() or onFailure(), which is not possible
         *     anymore because OpenHABVoiceService is already stopped/destroyed.
         */

        Log.d(TAG, "WEB REQUEST: " + mOpenHABBaseUrl + "rest/items/" + itemName);
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                mAsyncHttpClient.post(OpenHABHomeWidgetService.this, mOpenHABBaseUrl + "rest/items/" + itemName,
                        command, "text/plain;charset=UTF-8", new AsyncHttpResponseHandler() {
                            @Override
                            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                                Log.d(TAG, "Command was sent successfully");
                            }

                            @Override
                            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                                Log.e(TAG, "Got command error " + statusCode, error);
                            }
                        });
            }
        });
    }
}
