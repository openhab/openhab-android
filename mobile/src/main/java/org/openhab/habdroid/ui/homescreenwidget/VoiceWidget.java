/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui.homescreenwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.RemoteViews;
import androidx.annotation.LayoutRes;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.VoiceService;

/**
 * Implementation of App Widget functionality.
 */
public class VoiceWidget extends AppWidgetProvider {
    private final static String TAG = VoiceWidget.class.getSimpleName();

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), getLayoutRes());

        Intent intent;
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.d(TAG, "Voice recognizer available, build speech intent");
            Intent callbackIntent = new Intent(context, VoiceService.class);
            PendingIntent callbackPendingIntent = PendingIntent.getService(context,
                    9, callbackIntent, 0);

            intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            // Display an hint to the user about what he should say.
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                    context.getString(R.string.info_voice_input));
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT, callbackPendingIntent);
        } else {
            Log.d(TAG, "Voice recognizer not available, build open app store intent");
            intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.google.android.googlequicksearchbox"));
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 6, intent, 0);
        views.setOnClickPendingIntent(R.id.outer_layout, pendingIntent);

        setupOpenhabIcon(context, views);

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

     void setupOpenhabIcon(Context context, RemoteViews views) {
        // This widget has no openHAB icon displayed.
     }

     @LayoutRes int getLayoutRes() {
        return R.layout.widget_voice;
     }
}