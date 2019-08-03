package org.openhab.habdroid.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import androidx.annotation.Px
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.background.BackgroundIntentReceiveActivity
import org.openhab.habdroid.background.BackgroundTasksManager
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.dpToPixel
import org.openhab.habdroid.util.getIconFormat
import org.openhab.habdroid.util.getPrefs

class ItemUpdateWidgetItemPickerActivity(override var disabledMessageId: Int = 0) : AbstractItemPickerActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retryButton.setOnClickListener {
            loadItems()
        }

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }

    override fun finish(item: Item, state: String, mappedState: String) {
        val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(this)
        RemoteViews(this.packageName, R.layout.widget_item_update).also { views ->
            val intent = Intent(this, BackgroundIntentReceiveActivity::class.java).apply {
                action = BackgroundTasksManager.ACTION_UPDATE_WIDGET
                putExtra(EXTRA_ITEM_NAME, item.name)
                putExtra(EXTRA_ITEM_STATE, state)
            }

            val pendingIntent = PendingIntent.getActivity(this, 6, intent, 0)
            views.setOnClickPendingIntent(R.id.outer_layout, pendingIntent)
            views.setTextViewText(R.id.text, getString(R.string.item_update_widget_text, item.label, mappedState))
            setIcon(this, item, state, views, appWidgetManager)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultValue)
        finish()
    }

    private fun setIcon(
        context: Context,
        item: Item,
        state: String,
        views: RemoteViews,
        appWidgetManager: AppWidgetManager
    ) = GlobalScope.launch {
        val connection = ConnectionFactory.usableConnectionOrNull ?: return@launch

        if (item.category == null) {
            views.setImageViewResource(R.id.item_icon, R.mipmap.icon)
        }
        val encodedIcon = Uri.encode(item.category)
        val iconFormat = context.getPrefs().getIconFormat()
        val iconUrl = "icon/$encodedIcon?state=$state&format=$iconFormat"
        @Px val size = context.resources.dpToPixel(128F).toInt()

        try {
            val bitmap = connection.httpClient.get(iconUrl).asBitmap(size, true).response
            views.setImageViewBitmap(R.id.item_icon, bitmap)

            IconCompat.createWithBitmap(bitmap)
        } catch (e: HttpClient.HttpException) {
            // Fall back to openHAB icon
            views.setImageViewResource(R.id.item_icon, R.mipmap.icon)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
