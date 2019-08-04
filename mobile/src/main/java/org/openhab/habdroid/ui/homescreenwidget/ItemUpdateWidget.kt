/*
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import org.openhab.habdroid.R
import org.openhab.habdroid.background.BackgroundIntentReceiveActivity
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.ui.AbstractItemPickerActivity

open class ItemUpdateWidget : AppWidgetProvider() {
    /*override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate()")
        appWidgetIds.forEach {
            Log.d(TAG, "id: $it")
            val data = context.getPrefs().getInfoForWidget(it)
            if (data != null) {
                setupWidget(context, data, it, appWidgetManager)
            }
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }*/

    override fun onEnabled(context: Context?) {
        Log.d(TAG, "onEnabled()")
        super.onEnabled(context)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "onReceive() $intent")
        super.onReceive(context, intent)
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        Log.d(TAG, "onDeleted()")
        // TODO delete info from prefs
        super.onDeleted(context, appWidgetIds)
    }

    companion object {
        private val TAG = ItemUpdateWidget::class.java.simpleName

        fun setupWidget(
            context: Context,
            data: ItemUpdateWidgetData,
            appWidgetId: Int,
            appWidgetManager: AppWidgetManager
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_item_update)
            val intent = Intent(context, BackgroundIntentReceiveActivity::class.java).apply {
                action = BackgroundTasksManager.ACTION_UPDATE_WIDGET
                putExtra(AbstractItemPickerActivity.EXTRA_ITEM_NAME, data.item)
                putExtra(AbstractItemPickerActivity.EXTRA_ITEM_STATE, data.state)
            }

            val pendingIntent = PendingIntent.getActivity(context, 6, intent, 0)
            views.setOnClickPendingIntent(R.id.outer_layout, pendingIntent)
            views.setTextViewText(R.id.text,
                context.getString(R.string.item_update_widget_text, data.label, data.mappedState))
            //setIcon(this, item, state, views, appWidgetManager) // TODO
            appWidgetManager.updateAppWidget(appWidgetId, views)
            Log.d(TAG, "$views;$intent")
        }
    }

    data class ItemUpdateWidgetData(val item: String, val state: String, val label: String, val mappedState: String)
}
