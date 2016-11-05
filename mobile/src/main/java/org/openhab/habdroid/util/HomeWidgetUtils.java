package org.openhab.habdroid.util;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.impl.client.DefaultHttpClient;

public class HomeWidgetUtils {
    private static final String TAG = HomeWidgetUtils.class.getSimpleName();

    private static final String PREFS_NAME
            = "org.openhab.habdroid.HomeWidgetPrefs";

    private static final String PREF_PREFIX_KEY = "widget_";


    public static void saveWidgetPrefs(Context context, int appWidgetId, String field, String text) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.putString(PREF_PREFIX_KEY + appWidgetId + "_" + field, text);
        prefs.commit();
    }


    // Read the prefix from the SharedPreferences object for this widget.
    // If there is no preference saved, get the default from a resource
    public static String loadWidgetPrefs(Context context, int appWidgetId, String field) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        String prefix = prefs.getString(PREF_PREFIX_KEY + appWidgetId + "_" + field, null);
        if (prefix != null) {
            return prefix;
        } else {
            return null;
        }
    }

    public static void deletePref(Context context, int appWidgetId, String field) {
    }

    public static void loadAllTitlePrefs(Context context, ArrayList<Integer> appWidgetIds,
                                  ArrayList<String> texts) {
    }

    public static JSONArray getJSONArrayFromURL(Context context, String url) {
        JSONArray jArray = null;



        try {
            jArray = new JSONArray(HomeWidgetUtils.getJSONTextFromURL(context,url));
        } catch (JSONException e) {
            Log.e("log_tag", "Error parsing data " + e.toString());
        }

        return jArray;
    }

    public static JSONObject getJSONObjectFromURL(Context context, String url) {
        JSONObject jObject = null;

        Log.d(TAG, url);

        try {
            jObject = new JSONObject(HomeWidgetUtils.getJSONTextFromURL(context,url));

        } catch (JSONException e) {
            Log.e("log_tag", "Error parsing data " + e.toString());
        }

        return jObject;
    }


    private static String getJSONTextFromURL(Context context, String url){
        InputStream is = null;
        String result = "";

        SharedPreferences mSettings = PreferenceManager.getDefaultSharedPreferences(context);
        String username = mSettings.getString(Constants.PREFERENCE_USERNAME, null);
        String password = mSettings.getString(Constants.PREFERENCE_PASSWORD, null);
        String baseURL = mSettings.getString(Constants.PREFERENCE_URL, null);

        //TODO: username + password


        url = baseURL + url;

        Log.d(TAG, "Loading JSON from " + url);


        // Download JSON data from URL
        try {
            HttpClient httpclient = new DefaultHttpClient();
            //HttpPost httppost = new HttpPost(url);
            HttpGet httpget = new HttpGet(url);
            HttpResponse response = httpclient.execute(httpget);
            HttpEntity entity = response.getEntity();
            is = entity.getContent();

        } catch (Exception e) {
            Log.e("log_tag", "Error in http connection " + e.toString());
        }

        // Convert response to string
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    is, "iso-8859-1"), 8);
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
            is.close();
            result = sb.toString();
        } catch (Exception e) {
            Log.e("log_tag", "Error converting result " + e.toString());
        }

        return result;
    }

    private void updateTextLabel(int item, int widgetId, String text, Context context){
        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.home_widget);

        remoteViews.setTextViewText(item,  text);

        AppWidgetManager.getInstance(context).updateAppWidget(widgetId, remoteViews);
    }


    private void updateImageButton(int item, int widgetId, int drawableId, Context context){
        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.home_widget);

        Bitmap icon = BitmapFactory.decodeResource(context.getResources(),
                drawableId);

        remoteViews.setImageViewBitmap(item, icon);

        AppWidgetManager.getInstance(context).updateAppWidget(widgetId, remoteViews);
    }

    public static Bitmap toGrayscale(Bitmap original) {
        int height = original.getHeight();
        int width = original.getWidth();

        Bitmap grayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(grayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0); // <-- important line here
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(original, 0, 0, paint);
        return grayscale;
    }
}
