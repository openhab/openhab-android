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
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.HomeWidgetProvider;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.HomeWidgetUtils;
import org.openhab.habdroid.util.MyWebImage;

class GetItemDetails extends AsyncTask {
    private static final String TAG = HomeWidgetUtils.class.getSimpleName();
    private static final String URI_SCHEME = "OHWDGT";

    Context context;
    AppWidgetManager appWidgetManager;
    int appWidgetId;

    public GetItemDetails(Context context, AppWidgetManager appWidgetManager,
                          int appWidgetId){
        this.context = context;
        this.appWidgetManager = appWidgetManager;
        this.appWidgetId = appWidgetId;
    }

    private Exception exception;

    protected Bitmap doInBackground(Object[] params) {
        try {

            SharedPreferences mSettings = PreferenceManager.getDefaultSharedPreferences(context);
            String username = mSettings.getString(Constants.PREFERENCE_USERNAME, null);
            String password = mSettings.getString(Constants.PREFERENCE_PASSWORD, null);
            String baseURL = mSettings.getString(Constants.PREFERENCE_URL, null);

            String icon = HomeWidgetUtils.loadWidgetPrefs(context, appWidgetId, "icon");

            Bitmap iconBitmap;
            try {
                MyWebImage iconImg = new MyWebImage(baseURL + "icon/" + icon + "?state=ON", username, password);

                iconBitmap = iconImg.getBitmap(context);
            }catch(Exception e){
                iconBitmap = BitmapFactory.decodeResource(context.getResources(),
                        R.drawable.openhab);
            }


            return iconBitmap;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    protected void onPostExecute(Object o) {
        String name = HomeWidgetUtils.loadWidgetPrefs(context, appWidgetId, "name");

        if(name != null) {
            String label = HomeWidgetUtils.loadWidgetPrefs(context, appWidgetId, "label");

            String pin = HomeWidgetUtils.loadWidgetPrefs(context, appWidgetId, "pin");

            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.home_widget);
            views.setTextViewText(R.id.widgetLabel, label);
            views.setImageViewBitmap(R.id.widgetButton, (Bitmap) o);

            Intent active = new Intent().setClass(context, HomeWidgetProvider.class);
            active.setAction(HomeWidgetProvider.ACTION_BUTTON_CLICKED);


            Uri data = Uri.withAppendedPath(
                    Uri.parse(URI_SCHEME + "://widget/id/")
                    , String.valueOf(appWidgetId));
            active.setData(data);


            active.putExtra("item_name", name);
            active.putExtra("item_command", "ON");

            PendingIntent actionPendingIntent = PendingIntent.getBroadcast(context, 0, active, 0);
            views.setOnClickPendingIntent(R.id.widgetButton, actionPendingIntent);


            appWidgetManager.updateAppWidget(new ComponentName(context, HomeWidgetProvider.class), views);
            //appWidgetManager.updateAppWidget(appWidgetId, views);

        }
    }

}