/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.widget.RemoteViews;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.OpenHABHomeWidgetService;
import org.openhab.habdroid.util.HomeWidgetUpdateJob;
import org.openhab.habdroid.util.HomeWidgetUtils;

/**
 * Implementation of App Widget functionality.
 */
public class HomeWidgetProvider extends AppWidgetProvider {
    private static final String TAG = HomeWidgetProvider.class.getSimpleName();

    public final static String ACTION_BUTTON_CLICKED = "ACTION_BUTTON_CLICKED";
    public final static String ACTION_STATUS_CHANGED = "ACTION_STATUS_CHANGED";



    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {


        Log.d(TAG, "onUpdate");

        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            if(HomeWidgetUtils.loadWidgetPrefs(context, appWidgetId, "name") != null) {
                new HomeWidgetUpdateJob(context, appWidgetManager, appWidgetId).execute();
            }
        }
    }

    @Override
    public void onEnabled(Context context) {
        Log.d(TAG, "STARTNG WIDGET SERVICE");

        context.startService(new Intent(context, OpenHABHomeWidgetService.class));
    }

    @Override
    public void onDisabled(Context context) {
        Log.d(TAG, "STOPPING WIDGET SERVICE");

        context.stopService(new Intent(context, OpenHABHomeWidgetService.class));
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        Log.d(TAG, "onDeleted");

        for (int appWidgetId : appWidgetIds) {
            SharedPreferences prefs = context.getSharedPreferences("widget_prefs", 0);
            prefs.edit().remove("widget_"+appWidgetId+"_label");
            prefs.edit().remove("widget_"+appWidgetId+"_name");
            prefs.edit().remove("widget_"+appWidgetId+"_icon");
            prefs.edit().remove("widget_"+appWidgetId+"_pin");
        }
        super.onDeleted(context, appWidgetIds);
    }


    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("WIDGET APP", intent.getAction());

        if (intent.getAction().equals(ACTION_BUTTON_CLICKED)){

            Intent serviceIntent = new Intent(context, OpenHABHomeWidgetService.class);

            serviceIntent.putExtra("item_name", intent.getStringExtra("item_name"));
            serviceIntent.putExtra("item_command", "ON");

            context.startService(serviceIntent);

        }else if (intent.getAction().equals(ACTION_STATUS_CHANGED)){
          //  updateImageButton(R.id.widgetButton, R.drawable.ic_action_down, context);
        }else {
            super.onReceive(context, intent);
        }
    }






}