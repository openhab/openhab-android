/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.widget.RemoteViews;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.OpenHABVoiceService;

/**
 * Implementation of App Widget functionality.
 */
public class VoiceWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onEnabled(Context context) {
        // Enter relevant functionality for when the first widget is created
    }

    @Override
    public void onDisabled(Context context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    private void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                 int appWidgetId) {
        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.voice_widget);

        Intent callbackIntent = new Intent(context, OpenHABVoiceService.class);
        PendingIntent openhabPendingIntent = PendingIntent.getService(context, 9, callbackIntent, 0);

        Intent speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        // Display an hint to the user about what he should say.
        speechIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.info_voice_input));
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        speechIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        speechIntent.putExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT, openhabPendingIntent);

        PendingIntent speechPendingIntent = PendingIntent.getActivity(context, 6, speechIntent, 0);

        views.setOnClickPendingIntent(R.id.btn_mic, speechPendingIntent);

        Intent mainIntent = new Intent(context, OpenHABMainActivity.class);
        PendingIntent mainPendingIntent = PendingIntent.getActivity(context, 8, mainIntent, 0);
        views.setOnClickPendingIntent(R.id.btn_open_main, mainPendingIntent);

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}