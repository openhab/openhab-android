/*
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.Px
import androidx.core.content.edit
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.min
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.openhab.habdroid.R
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.IconFormat
import org.openhab.habdroid.model.IconResource
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.getIconResource
import org.openhab.habdroid.model.putIconResource
import org.openhab.habdroid.ui.duplicate
import org.openhab.habdroid.ui.preference.PreferencesActivity
import org.openhab.habdroid.util.CacheManager
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.IconBackground
import org.openhab.habdroid.util.ImageConversionPolicy
import org.openhab.habdroid.util.ItemClient
import org.openhab.habdroid.util.PendingIntent_Immutable
import org.openhab.habdroid.util.dpToPixel
import org.openhab.habdroid.util.getIconFallbackColor
import org.openhab.habdroid.util.getStringOrEmpty
import org.openhab.habdroid.util.getStringOrNull
import org.openhab.habdroid.util.isSvg
import org.openhab.habdroid.util.parcelable
import org.openhab.habdroid.util.showToast
import org.openhab.habdroid.util.svgToBitmap

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
                    val data = intent.getBundleExtra(EXTRA_BUNDLE)
                        ?.parcelable<ItemUpdateWidgetData>(EXTRA_DATA)
                        ?: return
                    saveInfoForWidget(context, data, id)
                    BackgroundTasksManager.schedulePeriodicTrigger(context, false)
                    setupWidget(context, data, id, AppWidgetManager.getInstance(context))
                    context.showToast(R.string.home_shortcut_success_pinning)
                }
                ACTION_UPDATE_WIDGET -> {
                    val data = getInfoForWidget(context, id)
                    if (data.command != null) {
                        BackgroundTasksManager.enqueueWidgetItemUpdateIfNeeded(context, data)
                    }
                }
                ACTION_EDIT_WIDGET -> {
                    Log.d(TAG, "Edit widget $id")
                    val openEditIntent = Intent(context, PreferencesActivity::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
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

        val itemUpdatePendingIntent =
            PendingIntent.getBroadcast(context, appWidgetId, itemUpdateIntent, PendingIntent_Immutable)

        val editIntent = Intent(context, ItemUpdateWidget::class.java).apply {
            action = ACTION_EDIT_WIDGET
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val editPendingIntent = PendingIntent.getBroadcast(context, appWidgetId, editIntent, PendingIntent_Immutable)

        GlobalScope.launch {
            val itemState = if (data.showState) {
                ConnectionFactory.waitForInitialization()
                try {
                    ConnectionFactory.primaryUsableConnection?.connection?.let { connection ->
                        val item = ItemClient.loadItem(connection, data.item)
                        when {
                            item?.isOfTypeOrGroupType(Item.Type.Number) == true -> item.state?.asNumber?.toString()
                            else -> item?.state?.asString
                        }
                    }
                } catch (e: HttpClient.HttpException) {
                    Log.e(TAG, "Failed to load state of item ${data.item}")
                    null
                }
            } else {
                null // State isn't shown, so no need to get it
            }

            val views = getRemoteViews(
                context,
                smallWidget,
                itemUpdatePendingIntent,
                editPendingIntent,
                data,
                itemState ?: context.getString(R.string.error_getting_state)
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
            fetchAndSetIcon(context, views, data, smallWidget, appWidgetId, appWidgetManager)
        }
    }

    private suspend fun fetchAndSetIcon(
        context: Context,
        views: RemoteViews,
        data: ItemUpdateWidgetData,
        smallWidget: Boolean,
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager
    ) {
        val iconUrl = data.icon?.withCustomState(data.command.orEmpty())?.toUrl(context, true)

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
                val widgetBackground = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    IconBackground.OS_THEME
                } else {
                    IconBackground.LIGHT
                }
                val fallbackColor = context.getIconFallbackColor(widgetBackground)
                iconData.svgToBitmap(size, fallbackColor, ImageConversionPolicy.PreferTargetSize)
            }

            val setIcon = { iconData: InputStream, isSvg: Boolean ->
                var iconBitmap = if (isSvg) convertSvgIcon(iconData) else BitmapFactory.decodeStream(iconData)
                if (iconBitmap != null) {
                    var viewsWithIcon = views.duplicate()
                    var retryCount = 0
                    do {
                        Log.d(TAG, "Bitmap size: ${iconBitmap.byteCount} bytes")
                        viewsWithIcon.setImageViewBitmap(R.id.item_icon, iconBitmap)
                        try {
                            Log.d(TAG, "Try to set icon")
                            appWidgetManager.updateAppWidget(appWidgetId, viewsWithIcon)
                            break
                        } catch (iae: IllegalArgumentException) {
                            retryCount++
                            Log.w(TAG, "Failed to set icon, attempt #$retryCount", iae)
                            val newWidth = iconBitmap.width / 2
                            val newHeight = iconBitmap.height / 2
                            iconBitmap = Bitmap.createScaledBitmap(iconBitmap, newWidth, newHeight, true)
                            // The view object keeps the previous bitmap when setting a new one, so we need to reset
                            // it to its version without bitmap, as otherwise its size would never decrease.
                            viewsWithIcon = views.duplicate()
                        }
                    } while (retryCount < 5 && iconBitmap.width > 50 && iconBitmap.height > 50)

                    hideLoadingIndicator(viewsWithIcon)
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
                    val connection = ConnectionFactory.primaryUsableConnection?.connection
                    if (connection == null) {
                        Log.d(TAG, "Got no connection")
                        return
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
            val item = prefs.getStringOrEmpty(PreferencesActivity.ITEM_UPDATE_WIDGET_ITEM)
            val command = prefs.getStringOrNull(PreferencesActivity.ITEM_UPDATE_WIDGET_COMMAND)
            val label = prefs.getStringOrEmpty(PreferencesActivity.ITEM_UPDATE_WIDGET_LABEL)
            val widgetLabel = prefs.getStringOrNull(PreferencesActivity.ITEM_UPDATE_WIDGET_WIDGET_LABEL)
            val mappedState = prefs.getStringOrEmpty(PreferencesActivity.ITEM_UPDATE_WIDGET_MAPPED_STATE)
            val icon = prefs.getIconResource(PreferencesActivity.ITEM_UPDATE_WIDGET_ICON)
            val showState = prefs.getBoolean(PreferencesActivity.ITEM_UPDATE_WIDGET_SHOW_STATE, false)
            return ItemUpdateWidgetData(item, command, label, widgetLabel, mappedState, icon, showState)
        }

        fun saveInfoForWidget(context: Context, data: ItemUpdateWidgetData, id: Int) {
            getPrefsForWidget(context, id).edit {
                putString(PreferencesActivity.ITEM_UPDATE_WIDGET_ITEM, data.item)
                putString(PreferencesActivity.ITEM_UPDATE_WIDGET_COMMAND, data.command)
                putString(PreferencesActivity.ITEM_UPDATE_WIDGET_LABEL, data.label)
                putString(PreferencesActivity.ITEM_UPDATE_WIDGET_WIDGET_LABEL, data.widgetLabel)
                putString(PreferencesActivity.ITEM_UPDATE_WIDGET_MAPPED_STATE, data.mappedState)
                putIconResource(PreferencesActivity.ITEM_UPDATE_WIDGET_ICON, data.icon)
                putBoolean(PreferencesActivity.ITEM_UPDATE_WIDGET_SHOW_STATE, data.showState)
            }
        }

        fun getRemoteViews(
            context: Context,
            smallWidget: Boolean,
            itemUpdatePendingIntent: PendingIntent?,
            editPendingIntent: PendingIntent?,
            data: ItemUpdateWidgetData,
            itemState: String
        ): RemoteViews {
            val layout = if (smallWidget) {
                R.layout.widget_item_update_small
            } else {
                R.layout.widget_item_update
            }
            val label = if (data.showState) {
                "${data.widgetLabel.orEmpty()} $itemState"
            } else {
                data.widgetLabel.orEmpty()
            }
            val views = RemoteViews(context.packageName, layout)
            views.setOnClickPendingIntent(android.R.id.background, itemUpdatePendingIntent)

            val editButtonVisibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                View.GONE
            } else {
                views.setOnClickPendingIntent(R.id.edit, editPendingIntent)
                View.VISIBLE
            }
            views.setViewVisibility(R.id.edit, editButtonVisibility)

            views.setTextViewText(R.id.text, label)
            val alpha = if (label.isNotEmpty() && smallWidget) 76 else 255
            views.setInt(R.id.item_icon, "setImageAlpha", alpha)
            hideLoadingIndicator(views)
            return views
        }

        private fun hideLoadingIndicator(views: RemoteViews) {
            views.setViewVisibility(R.id.item_icon, View.VISIBLE)
            views.setViewVisibility(R.id.text, View.VISIBLE)
            views.setViewVisibility(R.id.progress_bar, View.GONE)
        }

        fun getPrefsForWidget(context: Context, id: Int): SharedPreferences =
            context.getSharedPreferences(getPrefsNameForWidget(id), Context.MODE_PRIVATE)

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
        val command: String?,
        val label: String,
        val widgetLabel: String?,
        val mappedState: String,
        val icon: IconResource?,
        val showState: Boolean
    ) : Parcelable {
        fun isValid(): Boolean = item.isNotEmpty() &&
            label.isNotEmpty()

        /**
         * When comparing fields treat null as an empty string.
         */
        fun nearlyEquals(other: Any?): Boolean = when (other) {
            null -> false
            !is ItemUpdateWidgetData -> false
            this -> true
            else -> {
                this.item == other.item &&
                    this.command.orEmpty() == other.command.orEmpty() &&
                    this.label == other.label &&
                    this.widgetLabel.orEmpty() == other.widgetLabel.orEmpty() &&
                    this.mappedState == other.mappedState &&
                    this.icon == other.icon &&
                    this.showState == other.showState
            }
        }
    }
}
