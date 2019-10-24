package org.openhab.habdroid.ui

import android.content.Intent
import android.os.Bundle
import androidx.core.content.edit
import androidx.core.os.bundleOf
import org.openhab.habdroid.R
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.util.Constants
import org.openhab.habdroid.util.TaskerIntent
import org.openhab.habdroid.util.TaskerPlugin
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.isTaskerPluginEnabled

class TaskerItemPickerActivity(
    override var hintMessageId: Int = R.string.settings_tasker_plugin_summary,
    override var hintButtonMessageId: Int = R.string.turn_on,
    override var hintIconId: Int = R.drawable.ic_connection_error
) : AbstractItemPickerActivity() {
    private var relevantVars: Array<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        retryButton.setOnClickListener {
            if (needToShowHint) {
                getPrefs().edit {
                    putBoolean(Constants.PREFERENCE_TASKER_PLUGIN_ENABLED, true)
                }
                needToShowHint = false
            }
            loadItems()
        }

        if (!getPrefs().isTaskerPluginEnabled()) {
            needToShowHint = true
            updateViewVisibility(loading = false, loadError = false, showHint = true)
        }

        val editItem = intent.getBundleExtra(TaskerIntent.EXTRA_BUNDLE)
        initialHighlightItemName = editItem?.getString(EXTRA_ITEM_NAME)

        if (TaskerPlugin.hostSupportsRelevantVariables(intent.extras)) {
            relevantVars = TaskerPlugin.getRelevantVariableList(intent.extras)
        }
    }

    override fun addAdditionalCommands(labels: MutableList<String>, commands: MutableList<String>) {
        relevantVars?.forEach {
            labels.add(getString(R.string.item_picker_tasker_variable, it))
            commands.add(it)
        }
    }

    override fun finish(item: Item, state: String, mappedState: String) {
        val resultBundle = bundleOf(
            EXTRA_ITEM_NAME to item.name,
            EXTRA_ITEM_LABEL to item.label,
            EXTRA_ITEM_STATE to state,
            EXTRA_ITEM_MAPPED_STATE to mappedState
        )
        val blurb = getString(R.string.item_picker_blurb, item.label, item.name, state)

        val intent = Intent().apply {
            putExtra(TaskerIntent.EXTRA_STRING_BLURB, blurb)
            putExtra(TaskerIntent.EXTRA_BUNDLE, resultBundle)
        }

        if (TaskerPlugin.Setting.hostSupportsOnFireVariableReplacement(this)) {
            TaskerPlugin.Setting.setVariableReplaceKeys(resultBundle,
                arrayOf(EXTRA_ITEM_STATE, EXTRA_ITEM_MAPPED_STATE) )
        }

        setResult(RESULT_OK, intent)
        finish()
    }

    companion object {
        const val EXTRA_ITEM_NAME = "itemName"
        const val EXTRA_ITEM_LABEL = "itemLabel"
        const val EXTRA_ITEM_STATE = "itemState"
        const val EXTRA_ITEM_MAPPED_STATE = "itemMappedState"
    }
}
