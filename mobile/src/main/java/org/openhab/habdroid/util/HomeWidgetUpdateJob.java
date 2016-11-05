package org.openhab.habdroid.util;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.HomeWidgetProvider;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.HomeWidgetUtils;
import org.openhab.habdroid.util.MyWebImage;

import java.util.HashMap;
import java.util.Map;

public class HomeWidgetUpdateJob extends AsyncTask {
    private static final String TAG = HomeWidgetUpdateJob.class.getSimpleName();
    private static final String URI_SCHEME = "OHWDGT";

    private Context context;
    private AppWidgetManager appWidgetManager;
    private int appWidgetId;


    public HomeWidgetUpdateJob(Context context, AppWidgetManager appWidgetManager,
                               int appWidgetId){
        this.context = context;
        this.appWidgetManager = appWidgetManager;
        this.appWidgetId = appWidgetId;
    }


    public HomeWidgetUpdateJob(Context context, int appWidgetId){
        this.context = context;
        this.appWidgetManager = AppWidgetManager.getInstance(context);;
        this.appWidgetId = appWidgetId;
    }








    protected Map<String, Object> doInBackground(Object[] params) {

            SharedPreferences mSettings = PreferenceManager.getDefaultSharedPreferences(context);
            String username = mSettings.getString(Constants.PREFERENCE_USERNAME, null);
            String password = mSettings.getString(Constants.PREFERENCE_PASSWORD, null);
            String baseURL = mSettings.getString(Constants.PREFERENCE_URL, null);

            String icon = HomeWidgetUtils.loadWidgetPrefs(context, appWidgetId, "icon");
            String name = HomeWidgetUtils.loadWidgetPrefs(context, appWidgetId, "name");

            JSONObject item = HomeWidgetUtils.getJSONObjectFromURL(context, "rest/items/" + name);

            String state = "";

            try {
                state = item.getString("state");
                HomeWidgetUtils.saveWidgetPrefs(context, appWidgetId, "lastState", state);

            }catch (JSONException e){
                e.printStackTrace();
            }


            Bitmap iconBitmap;
            try {
                MyWebImage iconImg = new MyWebImage(baseURL + "icon/" + icon + "?state=" + state, username, password);

                iconBitmap = iconImg.getBitmap(context);
            }catch(Exception e){
                iconBitmap = BitmapFactory.decodeResource(context.getResources(),
                        R.drawable.openhab);
            }

            if(state.equals("OFF")) {
                iconBitmap = HomeWidgetUtils.toGrayscale(iconBitmap);
            }

            Map result = new HashMap<String, Object>();
            result.put("image", iconBitmap);
            try{
                result.put("state", state);
                if(item.has("stateDescription")) {
                    result.put("stateDescription", item.getJSONObject("stateDescription"));
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Log.d(TAG, item.toString());
                Log.d(TAG, "no state def found");
            }

            return result;
    }


    @Override
    protected void onPostExecute(Object o) {

        HashMap itemMap = (HashMap<String, Object>) o;


        String name = HomeWidgetUtils.loadWidgetPrefs(context, appWidgetId, "name");

        if(name != null) {
            String label = HomeWidgetUtils.loadWidgetPrefs(context, appWidgetId, "label");



            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.home_widget);
            views.setTextViewText(R.id.widgetLabel, label);
            views.setImageViewBitmap(R.id.widgetButton, (Bitmap) itemMap.get("image"));
            String currentState = (String) itemMap.get("state");

            if(currentState.equals("ON")){
                views.setInt(R.id.widgetButton, "setBackgroundColor", Color.WHITE);
            }




            try {
                JSONObject stateDescription = (JSONObject) itemMap.get("stateDescription");
                if(stateDescription == null || !stateDescription.getBoolean("readOnly")) {

                    Intent active = new Intent().setClass(context, HomeWidgetProvider.class);
                    active.setAction(HomeWidgetProvider.ACTION_BUTTON_CLICKED);

                    Uri data = Uri.withAppendedPath(
                            Uri.parse(URI_SCHEME + "://widget/id/"+ (Math.random()*Integer.MAX_VALUE) +"/")
                            , String.valueOf(appWidgetId));
                    active.setData(data);

                    String pin = HomeWidgetUtils.loadWidgetPrefs(context, appWidgetId, "pin");

                    active.putExtra("item_name", name);
                    active.putExtra("item_command", currentState.equals("ON") ? "OFF" : "ON");

                    PendingIntent actionPendingIntent = PendingIntent.getBroadcast(context, 0, active, 0);
                    views.setOnClickPendingIntent(R.id.widgetButton, actionPendingIntent);


                }
            } catch (JSONException | NullPointerException e) {
                e.printStackTrace();
            }


            appWidgetManager.updateAppWidget(new ComponentName(context, HomeWidgetProvider.class), views);
            //appWidgetManager.updateAppWidget(appWidgetId, views);

        }
    }

}