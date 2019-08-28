package org.openhab.habdroid.ui

import android.content.Intent
import android.os.Bundle
import androidx.core.content.edit
import androidx.core.os.bundleOf
import org.openhab.habdroid.R
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.util.Constants
import org.openhab.habdroid.util.TaskerIntent
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.isTaskerPluginEnabled

class TaskerItemPickerActivity(override var disabledMessageId: Int = R.string.settings_tasker_plugin_summary) :
    AbstractItemPickerActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        retryButton.setOnClickListener {
            if (isDisabled) {
                getPrefs().edit {
                    putBoolean(Constants.PREFERENCE_TASKER_PLUGIN_ENABLED, true)
                }
                isDisabled = false
            }
            loadItems()
        }

        if (!getPrefs().isTaskerPluginEnabled()) {
            isDisabled = true
            updateViewVisibility(loading = false, loadError = false, isDisabled = true)
        }

        val editItem = intent.getBundleExtra(TaskerIntent.EXTRA_BUNDLE)
        initialHighlightItemName = editItem?.getString(EXTRA_ITEM_NAME)
    }

    override fun finish(item: Item, state: String, mappedState: String) {
        val intent = Intent().apply {
            val blurb = getString(R.string.item_picker_blurb, item.label, item.name, state)
            putExtra(TaskerIntent.EXTRA_STRING_BLURB, blurb)

            putExtra(TaskerIntent.EXTRA_BUNDLE, bundleOf(
                EXTRA_ITEM_NAME to item.name,
                EXTRA_ITEM_STATE to state
            ))
        }

        setResult(RESULT_OK, intent)
        finish()
    }

    companion object {
        const val EXTRA_ITEM_NAME = "itemName"
        const val EXTRA_ITEM_STATE = "itemState"
    }
}
