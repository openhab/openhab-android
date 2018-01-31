package org.openhab.habdroid.util;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import com.loopj.android.http.AsyncHttpResponseHandler;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.OpenHABHomeWidgetService;
import org.openhab.habdroid.ui.HomeWidgetProvider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.entity.StringEntity;
import cz.msebera.android.httpclient.impl.client.DefaultHttpClient;
import cz.msebera.android.httpclient.protocol.HttpContext;

public class HomeWidgetSendCommandJob<Void> extends AsyncTask {
    private static final String TAG = HomeWidgetSendCommandJob.class.getSimpleName();

    private Context context;
    private String item;
    private String command;


    public HomeWidgetSendCommandJob(Context context, String item,
                                    String command){
        this.context = context;
        this.item = item;
        this.command = command;
    }


    protected Void doInBackground(Object[] params) {

            Log.d(TAG, "Sending comamnd " + command + " to item " + item);


            SharedPreferences mSettings = PreferenceManager.getDefaultSharedPreferences(context);
            String username = mSettings.getString(Constants.PREFERENCE_USERNAME, null);
            String password = mSettings.getString(Constants.PREFERENCE_PASSWORD, null);
            String baseURL = mSettings.getString(Constants.PREFERENCE_URL, null);


        //TODO: username + password

            HttpURLConnection urlConnection = null;

            try {
                URL url = new URL(baseURL + "rest/items/" + item);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestProperty("Content-Type", "text/plain");
                urlConnection.setRequestProperty("Method", "POST");


                urlConnection.setDoOutput(true);
                urlConnection.setChunkedStreamingMode(0);

                OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
                //out.write(("state="+command).getBytes(Charset.forName("UTF-8")));
                final PrintStream printStream = new PrintStream(out);
                printStream.print(command);
                printStream.close();


                int status = urlConnection.getResponseCode();

                Log.d(TAG, "Result: " + status);

            }catch (Exception e){
                e.printStackTrace();
            }


            if(urlConnection != null) {
                urlConnection.disconnect();
            }


        return null;
    }



}