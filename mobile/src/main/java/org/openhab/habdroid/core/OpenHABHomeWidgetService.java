/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.core;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestHandle;

import org.atmosphere.wasync.Client;
import org.atmosphere.wasync.ClientFactory;
import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Encoder;
import org.atmosphere.wasync.Event;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.impl.AtmosphereClient;
import org.atmosphere.wasync.impl.AtmosphereRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.HomeWidgetProvider;
import org.openhab.habdroid.ui.OpenHABWidgetListFragment;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.ContinuingIntentService;
import org.openhab.habdroid.util.HomeWidgetSendCommandJob;
import org.openhab.habdroid.util.HomeWidgetUpdateJob;
import org.openhab.habdroid.util.HomeWidgetUtils;
import org.openhab.habdroid.util.MyAsyncHttpClient;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.client.methods.RequestBuilder;
import cz.msebera.android.httpclient.entity.StringEntity;
import cz.msebera.android.httpclient.message.BasicHeader;


public class OpenHABHomeWidgetService extends Service{

    private static final String TAG = OpenHABHomeWidgetService.class.getSimpleName();

    private MyAsyncHttpClient mAsyncHttpClient;
    private String mAtmosphereTrackingId;
    private RequestHandle mRequestHandle;


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");

        SharedPreferences mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        String username = mSettings.getString(Constants.PREFERENCE_USERNAME, null);
        String password = mSettings.getString(Constants.PREFERENCE_PASSWORD, null);
        String baseURL = mSettings.getString(Constants.PREFERENCE_URL, null);



        if(mAsyncHttpClient == null) {
            mAsyncHttpClient = new MyAsyncHttpClient(this);
            mAsyncHttpClient.setBasicAuth(username, password);

            subscribeForChangesLongPoll(baseURL + "rest/sitemaps/_default/default");
        }


        //subscribeForChangesWebsockets(baseURL + "rest/items/alarmMode");
    }


    public void subscribeForChangesWebsockets(String url){

        final String wsUrl = url.replace("http","ws");

        Runnable task = new Runnable() {

            public void run() {
                try {

                    Client client = ClientFactory.getDefault().newClient();


                    org.atmosphere.wasync.Request request = client.newRequestBuilder()
                            .method(Request.METHOD.GET)
                            .uri(wsUrl)
                            .transport(Request.TRANSPORT.SSE)
                            .build();

                    //.transport(Request.TRANSPORT.STREAMING)

                    final Socket socket = client.create();
                    socket.on(Event.MESSAGE, new Function<String>() {
                        @Override
                        public void on(String message) {
                            Log.d(TAG, "DATA RCVD: " + message);
                        }
                    });

                    socket.open(request);

                    /*
                    AtmosphereClient client = ClientFactory.getDefault().newClient(AtmosphereClient.class);



                    AtmosphereRequest.AtmosphereRequestBuilder request = client.newRequestBuilder()
                            .method(Request.METHOD.GET)
                            .uri(wsUrl)
                            .trackMessageLength(true)
                            /*.encoder(new Encoder<String, String>() {

                                @Override
                                public String encode(String s) {
                                    return null;
                                }
                            })
                            .decoder(new Decoder<String, String>() {

                                @Override
                                public String decode(Event e, String s) {
                                    return null;
                                }
                            })
                            .transport(Request.TRANSPORT.WEBSOCKET);

                    final org.atmosphere.wasync.Socket socket = client.create();
                    socket.on("message", new Function<String>()

                            {
                                @Override
                                public void on(String s) {
                                    Log.d(TAG, s);
                                }
                            }

                    ).on(new Function<Throwable>() {

                             @Override
                             public void on(Throwable t) {
                                 t.printStackTrace();
                             }

                         }

                    ).open(request.build()

                    );
                    */

                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        };

        new Thread(task).start();

    }



    public void subscribeForChangesLongPoll(final String pageUrl){


            Log.d(TAG, "Registering for changes of " +pageUrl + " (Long-Poll)");

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
                        return;
                    } else {

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

                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        subscribeForChangesLongPoll(pageUrl);

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
                    subscribeForChangesLongPoll(pageUrl);
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
            jObject = new JSONObject(responseString);

            processObject(jObject, widgetConfigs);


        /*
            jArray = jObject.getJSONArray("widgets");



            for(int i = 0; i < jArray.length(); i++){
                jObject = jArray.getJSONObject(i);
                try {
                    itemName = jObject.getString("name");

                    if (    widgetConfigs.containsKey(itemName) &&
                            widgetConfigs.get(itemName).containsKey("lastState") &&
                            jObject.has("state") &&
                            !widgetConfigs.get(itemName).get("lastState").equals(jObject.getString("state"))
                    ) {
                        widgetId = Integer.parseInt(widgetConfigs.get(itemName).get("id"));
                        new HomeWidgetUpdateJob(getApplicationContext(), widgetId).execute();
                    }

                }catch (JSONException e){}
            }

        */

        } catch (JSONException e) {
            Log.e("log_tag", "Error parsing data " + e.toString());
        }

    }

    private void processObject(JSONObject jObject, HashMap<String, HashMap<String, String>> widgetConfigs){
        String itemName;
        int widgetId;
        try {
            JSONArray jArray = jObject.getJSONArray("widgets");
            for(int i = 0; i < jArray.length(); i++){
                jObject = jArray.getJSONObject(i);

                try {

                    jObject = jObject.getJSONObject("item");
                    itemName = jObject.getString("name");

                    if (    widgetConfigs.containsKey(itemName) &&
                            widgetConfigs.get(itemName).containsKey("lastState") &&
                            jObject.has("state") &&
                            !widgetConfigs.get(itemName).get("lastState").equals(jObject.getString("state"))
                            ) {
                        widgetId = Integer.parseInt(widgetConfigs.get(itemName).get("id"));
                        new HomeWidgetUpdateJob(getApplicationContext(), widgetId).execute();
                    }

                }catch (JSONException e){}

                if(jObject.has("widgets")){
                    processObject(jObject, widgetConfigs);
                }

            }
        } catch (JSONException e) {
            Log.e("log_tag", "Error parsing data " + e.toString());
        }
    }

    private HashMap<String, HashMap<String, String>> getWidgetConfigs(){
        HashMap<String, HashMap<String, String>> configs = new HashMap<String, HashMap<String, String>>();

        int ids[] = AppWidgetManager.getInstance(this).getAppWidgetIds(new ComponentName(this,HomeWidgetProvider.class));

        for(int i : ids){
            if(HomeWidgetUtils.loadWidgetPrefs(getApplicationContext(), i, "name") != null) {
                HashMap<String, String> wdgtConfig = new HashMap<String, String>();

                String lastState = HomeWidgetUtils.loadWidgetPrefs(getApplicationContext(), i, "lastState");
                wdgtConfig.put("lastState", lastState == null ? "UNDEFINED" : lastState);
                wdgtConfig.put("id", i + "");
                configs.put(HomeWidgetUtils.loadWidgetPrefs(getApplicationContext(), i, "name"), wdgtConfig);
            }
        }

        return configs;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }



}
