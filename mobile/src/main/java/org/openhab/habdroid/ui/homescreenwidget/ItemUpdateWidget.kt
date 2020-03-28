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
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.Px
import androidx.core.content.edit
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.IconFormat
import org.openhab.habdroid.model.IconResource
import org.openhab.habdroid.model.getIconResource
import org.openhab.habdroid.model.putIconResource
import org.openhab.habdroid.ui.ItemUpdateWidgetItemPickerActivity
import org.openhab.habdroid.ui.PreferencesActivity
import org.openhab.habdroid.util.CacheManager
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ToastType
import org.openhab.habdroid.util.dpToPixel
import org.openhab.habdroid.util.getString
import org.openhab.habdroid.util.isSvg
import org.openhab.habdroid.util.showToast
import org.openhab.habdroid.util.svgToBitmap
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
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
        Log.d(TAG, "onReceive() $intent")
        if (intent == null || context == null) {
            return super.onReceive(context, intent)
        }
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            when (intent.action) {
                ACTION_CREATE_WIDGET -> {
                    val data = intent
                        .getBundleExtra(EXTRA_BUNDLE)
                        ?.getParcelable<ItemUpdateWidgetData>(EXTRA_DATA)
                        ?: return
                    saveInfoForWidget(context, data, id)
                    setupWidget(context, data, id, AppWidgetManager.getInstance(context))
                    context.showToast(R.string.home_shortcut_success_pinning, ToastType.SUCCESS)
                }
                ACTION_UPDATE_WIDGET -> {
                    BackgroundTasksManager.enqueueWidgetItemUpdateIfNeeded(context, getInfoForWidget(context, id))
                }
                ACTION_EDIT_WIDGET -> {
                    Log.d(TAG, "Edit widget $id")
                    val openEditIntent = Intent(context, ItemUpdateWidgetItemPickerActivity::class.java).apply {
                        addFlags(FLAG_ACTIVITY_NEW_TASK)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    }
                    context.startActivity(openEditIntent)
                }
            }
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
            CacheManager.getInstance(context).removeWidgetIcon(it)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.deleteSharedPreferences(getPrefsNameForWidget(it))
            } else {
                getPrefsForWidget(context, it).edit {
                    clear()
                }
            }
        }
        super.onDeleted(context, appWidgetIds)
    }

    private fun setupWidget(
        context: Context,
        data: ItemUpdateWidgetData,
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager
    ) {
        val widgetOptions = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val smallWidget = widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) <
            context.resources.getDimension(R.dimen.small_widget_threshold)

        val itemUpdateIntent = Intent(context, ItemUpdateWidget::class.java).apply {
            action = ACTION_UPDATE_WIDGET
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val itemUpdatePendingIntent = PendingIntent.getBroadcast(context, appWidgetId, itemUpdateIntent, 0)

        val editIntent = Intent(context, ItemUpdateWidget::class.java).apply {
            action = ACTION_EDIT_WIDGET
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val editPendingIntent = PendingIntent.getBroadcast(context, appWidgetId, editIntent, 0)

        val views = getRemoteViews(context, smallWidget, itemUpdatePendingIntent, editPendingIntent, data)
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
        val iconUrl = data.icon?.withCustomState(data.state)?.toUrl(context, true)

        if (iconUrl != null) {
            val cm = CacheManager.getInstance(context)

            val convertSvgIcon = { iconData: InputStream ->
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
                iconData.svgToBitmap(size)
            }

            val setIcon = { iconData: InputStream, isSvg: Boolean ->
                val iconBitmap = if (isSvg) convertSvgIcon(iconData) else BitmapFactory.decodeStream(iconData)
                if (iconBitmap != null) {
                    views.setImageViewBitmap(R.id.item_icon, iconBitmap)
                    hideLoadingIndicator(views)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }

            try {
                val cachedIconType = cm.getWidgetIconFormat(appWidgetId)
                val cachedIcon = if (cachedIconType != null) cm.getWidgetIconStream(appWidgetId) else null
                if (cachedIcon != null) {
                    Log.d(TAG, "Icon exits")
                    cachedIcon.use { setIcon(it, cachedIconType == IconFormat.Svg) }
                } else {
                    Log.d(TAG, "Download icon")
                    ConnectionFactory.waitForInitialization()
                    val connection = ConnectionFactory.usableConnectionOrNull
                    if (connection == null) {
                        Log.d(TAG, "Got no connection")
                        return@launch
                    }
                    val response = connection.httpClient.get(iconUrl).response
                    val content = response.bytes()
                    val isSvg = response.contentType().isSvg()
                    ByteArrayInputStream(content).use {
                        val type = if (isSvg) IconFormat.Svg else IconFormat.Png
                        cm.saveWidgetIcon(appWidgetId, it, type)
                        it.reset()
                        setIcon(it, isSvg)
                    }
                }
            } catch (e: HttpClient.HttpException) {
                Log.e(TAG, "Error downloading icon for url $iconUrl", e)
            } catch (e: IOException) {
                Log.e(TAG, "Error saving icon to disk", e)
            }
        }
    }

    companion object {
        private val TAG = ItemUpdateWidget::class.java.simpleName
        private const val ACTION_UPDATE_WIDGET = "org.openhab.habdroid.action.UPDATE_ITEM_FROM_WIDGET"
        private const val ACTION_EDIT_WIDGET = "org.openhab.habdroid.action.EDIT_WIDGET"
        const val ACTION_CREATE_WIDGET = "org.openhab.habdroid.action.CREATE_WIDGET"
        const val EXTRA_DATA = "data"
        const val EXTRA_BUNDLE = "bundle"

        fun getInfoForWidget(context: Context, id: Int): ItemUpdateWidgetData {
            val prefs = getPrefsForWidget(context, id)
            val item = prefs.getString(PreferencesActivity.ITEM_UPDATE_WIDGET_ITEM)
            val state = prefs.getString(PreferencesActivity.ITEM_UPDATE_WIDGET_STATE)
            val label = prefs.getString(PreferencesActivity.ITEM_UPDATE_WIDGET_LABEL)
            val widgetLabel = prefs.getString(PreferencesActivity.ITEM_UPDATE_WIDGET_WIDGET_LABEL, null)
            val mappedState = prefs.getString(PreferencesActivity.ITEM_UPDATE_WIDGET_MAPPED_STATE)
            val icon = prefs.getIconResource(PreferencesActivity.ITEM_UPDATE_WIDGET_ICON)
            return ItemUpdateWidgetData(item, state, label, widgetLabel, mappedState, icon)
        }

        fun saveInfoForWidget(
            context: Context,
            data: ItemUpdateWidgetData,
            id: Int
        ) {
            getPrefsForWidget(context, id).edit {
                putString(PreferencesActivity.ITEM_UPDATE_WIDGET_ITEM, data.item)
                putString(PreferencesActivity.ITEM_UPDATE_WIDGET_STATE, data.state)
                putString(PreferencesActivity.ITEM_UPDATE_WIDGET_LABEL, data.label)
                putString(PreferencesActivity.ITEM_UPDATE_WIDGET_WIDGET_LABEL, data.widgetLabel)
                putString(PreferencesActivity.ITEM_UPDATE_WIDGET_MAPPED_STATE, data.mappedState)
                putIconResource(PreferencesActivity.ITEM_UPDATE_WIDGET_ICON, data.icon)
            }
        }

        fun getRemoteViews(
            context: Context,
            smallWidget: Boolean,
            itemUpdatePendingIntent: PendingIntent?,
            editPendingIntent: PendingIntent?,
            data: ItemUpdateWidgetData
        ): RemoteViews {
            val views = RemoteViews(
                context.packageName,
                if (smallWidget) R.layout.widget_item_update_small else R.layout.widget_item_update
            )
            views.setOnClickPendingIntent(R.id.outer_layout, itemUpdatePendingIntent)
            views.setOnClickPendingIntent(R.id.edit, editPendingIntent)
            val widgetLabel = data.widgetLabel
                ?: context.getString(R.string.item_update_widget_text, data.label, data.mappedState)
            views.setTextViewText(R.id.text, widgetLabel)
            hideLoadingIndicator(views)
            return views
        }

        private fun hideLoadingIndicator(views: RemoteViews) {
            views.setViewVisibility(R.id.item_icon, View.VISIBLE)
            views.setViewVisibility(R.id.text, View.VISIBLE)
            views.setViewVisibility(R.id.progress_bar, View.GONE)
        }

        fun getPrefsForWidget(context: Context, id: Int): SharedPreferences {
            return context.getSharedPreferences(getPrefsNameForWidget(id), Context.MODE_PRIVATE)
        }

        private fun getPrefsNameForWidget(id: Int) = "widget-$id"

        fun updateAllWidgets(context: Context) {
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, ItemUpdateWidget::class.java))
            val intent = Intent(context, ItemUpdateWidget::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }

    @Parcelize
    data class ItemUpdateWidgetData(
        val item: String,
        val state: String,
        val label: String,
        val widgetLabel: String?,
        val mappedState: String,
        val icon: IconResource?
    ) : Parcelable
}
