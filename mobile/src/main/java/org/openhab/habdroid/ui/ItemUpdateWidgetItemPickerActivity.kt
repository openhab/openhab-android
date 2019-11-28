package org.openhab.habdroid.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.openhab.habdroid.R
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.ui.homescreenwidget.ItemUpdateWidget

class ItemUpdateWidgetItemPickerActivity(
    override var hintMessageId: Int = 0,
    override var hintButtonMessageId: Int = 0,
    override var hintIconId: Int = 0
) : AbstractItemPickerActivity(), View.OnClickListener {
    @LayoutRes override val additionalConfigLayoutRes: Int = R.layout.widget_item_picker_config

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var autoGenSwitch: SwitchMaterial
    private lateinit var labelEditText: TextInputEditText
    private lateinit var labelEditTextWrapper: TextInputLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        autoGenSwitch = findViewById(R.id.auto_gen_label_switch)
        labelEditText = findViewById(android.R.id.edit)
        labelEditTextWrapper = findViewById(R.id.input_wrapper)

        autoGenSwitch.setOnClickListener(this)
        autoGenSwitch.isChecked = true
        labelEditText.setText(R.string.item_update_widget_default_label)
        labelEditTextWrapper.isEnabled = false
    }

    override fun finish(item: Item, state: String, mappedState: String) {
        val widgetLabel = if (autoGenSwitch.isChecked) {
            null
        } else {
            labelEditText.text.toString()
        }
        val label = if (item.label.isNullOrEmpty()) item.name else item.label

        val data = ItemUpdateWidget.ItemUpdateWidgetData(
            item.name,
            state,
            label,
            widgetLabel,
            mappedState,
            item.category.orEmpty()
        )

        ItemUpdateWidget.saveInfoForWidget(this, data, appWidgetId)

        val updateIntent = Intent(this, ItemUpdateWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        sendBroadcast(updateIntent)

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }

    override fun onClick(v: View?) {
        labelEditTextWrapper.isEnabled = !autoGenSwitch.isChecked
        labelEditText.setText(if (labelEditTextWrapper.isEnabled) {
            ""
        } else {
            getString(R.string.item_update_widget_default_label)
        })
    }
}
