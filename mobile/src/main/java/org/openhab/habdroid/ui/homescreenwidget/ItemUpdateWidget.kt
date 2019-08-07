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
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.Px
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.background.BackgroundIntentReceiveActivity
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.ui.AbstractItemPickerActivity
import org.openhab.habdroid.ui.PreferencesActivity
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.bitmapToSvg
import org.openhab.habdroid.util.dpToPixel
import org.openhab.habdroid.util.getIconFormat
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getString
import org.openhab.habdroid.util.isIconFormatPng
import java.io.IOException

open class ItemUpdateWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate()")
        appWidgetIds.forEach {
            Log.d(TAG, "id: $it")
            val data = getInfoForWidget(context, it)
            if (data != null) {
                setupWidget(context, data, it, appWidgetManager)
            }
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        Log.d(TAG, "onAppWidgetOptionsChanged()")
        val data = getInfoForWidget(context ?: return, appWidgetId)
        setupWidget(context, data ?: return, appWidgetId, appWidgetManager ?: return)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "onReceive() $intent")
        super.onReceive(context, intent)
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        Log.d(TAG, "onDeleted()")
        if (context == null || appWidgetIds == null) {
            return
        }
        appWidgetIds.forEach {
            Log.d(TAG, "Deleting data for id $it")
            context.deleteFile(getFileNameForWidget(it))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.deleteSharedPreferences(getFileNameForWidget(it))
            } else {
                saveInfoForWidget(context, ItemUpdateWidgetData("", "", "", "", ""), it)
            }
        }
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
            appWidgetManager.updateAppWidget(appWidgetId, views)
            fetchAndSetIcon(context, views, data, appWidgetId, appWidgetManager)
        }

        private fun fetchAndSetIcon(
            context: Context,
            views: RemoteViews,
            data: ItemUpdateWidgetData,
            appWidgetId: Int,
            appWidgetManager: AppWidgetManager
        ) = GlobalScope.launch {
            val connection = ConnectionFactory.usableConnectionOrNull ?: return@launch

            if (data.icon.isNotEmpty()) {
                val encodedIcon = Uri.encode(data.icon)
                val iconFormat = context.getPrefs().getIconFormat()
                val iconUrl = "icon/$encodedIcon?state=${data.state}&format=$iconFormat"

                try {
                    if (context.fileList().contains(getFileNameForWidget(appWidgetId))) {
                        Log.d(TAG, "Icon exits")
                    } else {
                        Log.d(TAG, "Download icon")
                        val bitmap = connection.httpClient.get(iconUrl).response.bytes()
                        context.openFileOutput(getFileNameForWidget(appWidgetId), MODE_PRIVATE).use {
                            it.write(bitmap)
                        }
                    }
                } catch (e: HttpClient.HttpException) {
                    Log.e(TAG, "Error downloading icon for url $iconUrl", e)
                } catch (e: IOException) {
                    Log.e(TAG, "Error saving icon to disk", e)
                }
            }

            setIcon(context, views, appWidgetId, appWidgetManager)
        }

        private fun setIcon(
            context: Context,
            views: RemoteViews,
            appWidgetId: Int,
            appWidgetManager: AppWidgetManager
        ) = GlobalScope.launch {
            val bitmap = try {
                context.openFileInput(getFileNameForWidget(appWidgetId)).use {
                if (context.getPrefs().isIconFormatPng()) {
                    val byteArray = it.readBytes()
                    BitmapFactory.decodeStream(byteArray.inputStream())
                } else {
                    val widgetOptions = appWidgetManager.getAppWidgetOptions(appWidgetId)
                    val height = widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                    val width = widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
                    // Image view height is 50% of the widget height
                    val sizeInDp = Math.min(height * 0.5F, width.toFloat())
                    @Px val size = context.resources.dpToPixel(sizeInDp).toInt()
                    Log.d(TAG, "Icon size: $size")
                    val svg = String(it.readBytes())
                    bitmapToSvg(svg, size)
                }
            }
            } catch (e: IOException) {
                Log.e(TAG, "Error getting icon from disk", e)
                null
            }

            if (bitmap == null) {
                Log.d(TAG, "Bitmap is null")
                views.setImageViewResource(R.id.item_icon, R.mipmap.icon)
            } else {
                views.setImageViewBitmap(R.id.item_icon, bitmap)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun getInfoForWidget(context: Context, id: Int): ItemUpdateWidgetData? {
            val prefs = context.getSharedPreferences(getFileNameForWidget(id), MODE_PRIVATE)
            val item = prefs.getString(PreferencesActivity.ITEM_UPDATE_WIDGET_ITEM)
            val state = prefs.getString(PreferencesActivity.ITEM_UPDATE_WIDGET_STATE)
            val label = prefs.getString(PreferencesActivity.ITEM_UPDATE_WIDGET_LABEL)
            val mappedState = prefs.getString(PreferencesActivity.ITEM_UPDATE_WIDGET_MAPPED_STATE)
            val icon = prefs.getString(PreferencesActivity.ITEM_UPDATE_WIDGET_ICON)
            if (item.isEmpty() || state.isEmpty() || label.isEmpty() || mappedState.isEmpty()) {
                return null
            }
            return ItemUpdateWidgetData(item, state, label, mappedState, icon)
        }

        fun saveInfoForWidget(
            context: Context,
            data: ItemUpdateWidgetData,
            id: Int
        ) {
            val prefs = context.getSharedPreferences(getFileNameForWidget(id), MODE_PRIVATE)
            prefs.edit()
                .putString(PreferencesActivity.ITEM_UPDATE_WIDGET_ITEM, data.item)
                .putString(PreferencesActivity.ITEM_UPDATE_WIDGET_STATE, data.state)
                .putString(PreferencesActivity.ITEM_UPDATE_WIDGET_LABEL, data.label)
                .putString(PreferencesActivity.ITEM_UPDATE_WIDGET_MAPPED_STATE, data.mappedState)
                .putString(PreferencesActivity.ITEM_UPDATE_WIDGET_ICON, data.icon)
                .apply()
        }

        fun getFileNameForWidget(id: Int): String {
            return "widget-$id"
        }
    }

    data class ItemUpdateWidgetData(
        val item: String,
        val state: String,
        val label: String,
        val mappedState: String,
        val icon: String
    )
}
