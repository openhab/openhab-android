/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui.homescreenwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.LayoutRes

import org.openhab.habdroid.R
import org.openhab.habdroid.core.VoiceService

/**
 * Implementation of App Widget functionality.
 */
open class VoiceWidget : AppWidgetProvider() {
    internal open val layoutRes: Int
        @LayoutRes get() = R.layout.widget_voice

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager,
                                appWidgetId: Int) {
        // Construct the RemoteViews object
        val views = RemoteViews(context.packageName, layoutRes)

        Log.d(TAG, "Voice recognizer available, build speech intent")
        val callbackIntent = Intent(context, VoiceService::class.java)
        val callbackPendingIntent = PendingIntent.getService(context,
                9, callbackIntent, 0)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        // Display an hint to the user about what he should say.
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                context.getString(R.string.info_voice_input))
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.putExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT, callbackPendingIntent)

        val pendingIntent = PendingIntent.getActivity(context, 6, intent, 0)
        views.setOnClickPendingIntent(R.id.outer_layout, pendingIntent)

        setupOpenhabIcon(context, views)

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    internal open fun setupOpenhabIcon(context: Context, views: RemoteViews) {
        // This widget has no openHAB icon displayed.
    }

    companion object {
        private val TAG = VoiceWidget::class.java.simpleName
    }
}