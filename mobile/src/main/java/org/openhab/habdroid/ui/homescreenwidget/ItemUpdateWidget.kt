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
import androidx.core.content.edit
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.ui.PreferencesActivity
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.dpToPixel
import org.openhab.habdroid.util.getIconFormat
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getString
import org.openhab.habdroid.util.isIconFormatPng
import org.openhab.habdroid.util.svgToBitmap
import java.io.IOException
import kotlin.math.min

open class ItemUpdateWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate()")
        appWidgetIds.forEach {
            Log.d(TAG, "id: $it")
            setupWidget(context, getInfoForWidget(context, it), it, appWidgetManager)
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
        setupWidget(context, data, appWidgetId, appWidgetManager ?: return)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "onReceive() ${intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -42)}") // TODO remove comment
        if (intent == null || context == null) {
            return super.onReceive(context, intent)
        }
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (intent.action == BackgroundTasksManager.ACTION_UPDATE_WIDGET
            && id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            BackgroundTasksManager.enqueueWidgetItemUpdateIfNeeded(getInfoForWidget(context, id))
        }
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
                context.getSharedPreferences(getFileNameForWidget(it), MODE_PRIVATE).edit {
                    clear()
                }
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
            val widgetOptions = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val smallWidget = widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) <
                context.resources.getDimension(R.dimen.small_widget_threshold)
            val views = RemoteViews(context.packageName,
                if (smallWidget) R.layout.widget_item_update_small else R.layout.widget_item_update)
            val intent = Intent(context, ItemUpdateWidget::class.java).apply {
                action = BackgroundTasksManager.ACTION_UPDATE_WIDGET
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }

            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
            views.setOnClickPendingIntent(R.id.outer_layout, pendingIntent)
            views.setTextViewText(R.id.text,
                context.getString(R.string.item_update_widget_text, data.label, data.mappedState))
            appWidgetManager.updateAppWidget(appWidgetId, views)
            fetchAndSetIcon(context, views, data, smallWidget, appWidgetId, appWidgetManager)
        }

        private fun fetchAndSetIcon(
            context: Context,
            views: RemoteViews,
            data: ItemUpdateWidgetData,
            smallWidget: Boolean,
            appWidgetId: Int,
            appWidgetManager: AppWidgetManager
        ) = GlobalScope.launch {
            if (data.icon.isNotEmpty()) {
                val encodedIcon = Uri.encode(data.icon)
                val iconFormat = context.getPrefs().getIconFormat()
                val iconUrl = "icon/$encodedIcon?state=${data.state}&format=$iconFormat"

                try {
                    if (context.fileList().contains(getFileNameForWidget(appWidgetId))) {
                        Log.d(TAG, "Icon exits")
                    } else {
                        Log.d(TAG, "Download icon")
                        ConnectionFactory.waitForInitialization()
                        val connection = ConnectionFactory.usableConnectionOrNull
                        if (connection == null) {
                            Log.d(TAG, "Got no connection")
                            return@launch
                        }
                        val content = connection.httpClient.get(iconUrl).response.bytes()
                        context.openFileOutput(getFileNameForWidget(appWidgetId), MODE_PRIVATE).use {
                            it.write(content)
                        }
                    }
                } catch (e: HttpClient.HttpException) {
                    Log.e(TAG, "Error downloading icon for url $iconUrl", e)
                } catch (e: IOException) {
                    Log.e(TAG, "Error saving icon to disk", e)
                }
            }

            setIcon(context, views, smallWidget, appWidgetId, appWidgetManager)
        }

        private fun setIcon(
            context: Context,
            views: RemoteViews,
            smallWidget: Boolean,
            appWidgetId: Int,
            appWidgetManager: AppWidgetManager
        ) = GlobalScope.launch {
            val bitmap = try {
                context.openFileInput(getFileNameForWidget(appWidgetId)).use { fileInputStream ->
                    val inputStream = fileInputStream.readBytes().inputStream()
                    if (context.getPrefs().isIconFormatPng()) {
                        BitmapFactory.decodeStream(inputStream)
                    } else {
                        val widgetOptions = appWidgetManager.getAppWidgetOptions(appWidgetId)
                        var height = widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat()
                        val width = widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).toFloat()
                        if (!smallWidget) {
                            // Image view height is 50% of the widget height
                            height *= 0.5F
                        }
                        val sizeInDp = min(height, width)
                        @Px val size = context.resources.dpToPixel(sizeInDp).toInt()
                        Log.d(TAG, "Icon size: $size")
                        inputStream.svgToBitmap(size)
                    }.also { inputStream.close() }
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

        fun getInfoForWidget(context: Context, id: Int): ItemUpdateWidgetData {
            val prefs = context.getSharedPreferences(getFileNameForWidget(id), MODE_PRIVATE)
            val item = prefs.getString(PreferencesActivity.ITEM_UPDATE_WIDGET_ITEM)
            val state = prefs.getString(PreferencesActivity.ITEM_UPDATE_WIDGET_STATE)
            val label = prefs.getString(PreferencesActivity.ITEM_UPDATE_WIDGET_LABEL)
            val mappedState = prefs.getString(PreferencesActivity.ITEM_UPDATE_WIDGET_MAPPED_STATE)
            val icon = prefs.getString(PreferencesActivity.ITEM_UPDATE_WIDGET_ICON)
            return ItemUpdateWidgetData(item, state, label, mappedState, icon)
        }

        fun saveInfoForWidget(
            context: Context,
            data: ItemUpdateWidgetData,
            id: Int
        ) {
            context.getSharedPreferences(getFileNameForWidget(id), MODE_PRIVATE).edit {
                putString(PreferencesActivity.ITEM_UPDATE_WIDGET_ITEM, data.item)
                putString(PreferencesActivity.ITEM_UPDATE_WIDGET_STATE, data.state)
                putString(PreferencesActivity.ITEM_UPDATE_WIDGET_LABEL, data.label)
                putString(PreferencesActivity.ITEM_UPDATE_WIDGET_MAPPED_STATE, data.mappedState)
                putString(PreferencesActivity.ITEM_UPDATE_WIDGET_ICON, data.icon)
            }
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
