package org.openhab.habdroid.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import androidx.annotation.Px
import androidx.core.content.edit
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.ui.homescreenwidget.ItemUpdateWidget
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.dpToPixel
import org.openhab.habdroid.util.getIconFormat
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.setInfoForWidget

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
        val label = if (item.label.isNullOrEmpty()) item.name else item.label
        val data = ItemUpdateWidget.ItemUpdateWidgetData(item.name, state, label, mappedState)
        ItemUpdateWidget.setupWidget(this, data, appWidgetId, appWidgetManager)
        getPrefs().edit { setInfoForWidget(appWidgetId, data) }
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
