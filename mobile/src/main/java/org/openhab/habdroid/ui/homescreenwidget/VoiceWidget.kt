/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.habdroid.ui.homescreenwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import org.openhab.habdroid.R
import org.openhab.habdroid.background.BackgroundTasksManager

/**
 * Implementation of App Widget functionality.
 */
open class VoiceWidget : AppWidgetProvider() {
    internal open val layoutRes: Int @LayoutRes get() = R.layout.widget_voice

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // There may be multiple widgets active, so update all of them
        appWidgetIds.forEach { appWidgetId ->
            // Construct the RemoteViews object
            val views = RemoteViews(context.packageName, layoutRes)

            Log.d(TAG, "Build voice recognition intent")
            val intent = BackgroundTasksManager.buildVoiceRecognitionIntent(context, true)
            val pendingIntent = PendingIntent.getActivity(context, 6, intent, 0)
            views.setOnClickPendingIntent(R.id.outer_layout, pendingIntent)

            setupOpenhabIcon(context, views)

            // Instruct the widget manager to update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    internal open fun setupOpenhabIcon(context: Context, views: RemoteViews) {
        // This widget has no openHAB icon displayed.
    }

    companion object {
        private val TAG = VoiceWidget::class.java.simpleName
    }
}
