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

package org.openhab.habdroid.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.core.os.bundleOf
import com.google.android.material.button.MaterialButton
import org.openhab.habdroid.R
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.TaskerIntent
import org.openhab.habdroid.util.TaskerPlugin
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.isTaskerPluginEnabled
import org.openhab.habdroid.util.showToast

class TaskerItemPickerActivity(
    override var hintMessageId: Int = R.string.settings_tasker_plugin_summary,
    override var hintButtonMessageId: Int = R.string.turn_on,
    override var hintIconId: Int = R.drawable.ic_connection_error
) : AbstractItemPickerActivity(), View.OnClickListener {
    private var relevantVars: Array<String>? = null
    private lateinit var commandButton: MaterialButton
    private lateinit var updateButton: MaterialButton
    override val forItemCommandOnly: Boolean = false
    @LayoutRes override val additionalConfigLayoutRes: Int = R.layout.tasker_item_picker_config

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        retryButton.setOnClickListener {
            if (needToShowHint) {
                getPrefs().edit {
                    putBoolean(PrefKeys.TASKER_PLUGIN_ENABLED, true)
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

        commandButton = findViewById(R.id.button_item_command)
        updateButton = findViewById(R.id.button_item_update)
        commandButton.setOnClickListener(this)
        updateButton.setOnClickListener(this)

        if (intent.getBundleExtra(TaskerIntent.EXTRA_BUNDLE)?.getBoolean(EXTRA_ITEM_AS_COMMAND, true) == false) {
            updateButton.isChecked = true
        } else {
            commandButton.isChecked = true
        }

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
        var asCommand = commandButton.isChecked

        if (asCommand && item.type == Item.Type.Contact) {
            asCommand = false
            showToast(R.string.item_picker_contact_no_command)
        }

        val resultBundle = bundleOf(
            EXTRA_ITEM_NAME to item.name,
            EXTRA_ITEM_LABEL to item.label,
            EXTRA_ITEM_STATE to state,
            EXTRA_ITEM_MAPPED_STATE to mappedState,
            EXTRA_ITEM_AS_COMMAND to asCommand
        )
        val resultIntent = Intent().apply {
            @StringRes val blurbRes =
                if (asCommand) R.string.item_picker_blurb_command else R.string.item_picker_blurb_update
            val blurb = getString(blurbRes, item.label, item.name, state)
            putExtra(TaskerIntent.EXTRA_STRING_BLURB, blurb)
            putExtra(TaskerIntent.EXTRA_BUNDLE, resultBundle)
        }

        if (TaskerPlugin.Setting.hostSupportsOnFireVariableReplacement(this)) {
            TaskerPlugin.Setting.setVariableReplaceKeys(resultBundle,
                arrayOf(EXTRA_ITEM_STATE, EXTRA_ITEM_MAPPED_STATE)
            )
        }

        if (TaskerPlugin.Setting.hostSupportsSynchronousExecution(intent.extras)) {
            TaskerPlugin.Setting.requestTimeoutMS(resultIntent, TaskerPlugin.Setting.REQUESTED_TIMEOUT_MS_MAX)
        }

        if (TaskerPlugin.hostSupportsRelevantVariables(intent.extras)) {
            TaskerPlugin.addRelevantVariableList(resultIntent,
                arrayOf("$VAR_HTTP_CODE\nHTTP code\nHTTP code returned by the server"))
        }

        setResult(RESULT_OK, resultIntent)
        finish()
    }

    companion object {
        const val EXTRA_ITEM_NAME = "itemName"
        const val EXTRA_ITEM_LABEL = "itemLabel"
        const val EXTRA_ITEM_STATE = "itemState"
        const val EXTRA_ITEM_MAPPED_STATE = "itemMappedState"

        const val RESULT_CODE_PLUGIN_DISABLED = TaskerPlugin.Setting.RESULT_CODE_FAILED_PLUGIN_FIRST
        const val RESULT_CODE_NO_CONNECTION = TaskerPlugin.Setting.RESULT_CODE_FAILED_PLUGIN_FIRST + 1
        fun getResultCodeForHttpFailure(httpCode: Int): Int {
            return 1000 + httpCode
        }

        const val VAR_HTTP_CODE = "%httpcode"
        const val EXTRA_ITEM_AS_COMMAND = "asCommand"
    }

    override fun onClick(view: View) {
        // Make sure one can't uncheck buttons by clicking a checked one
        (view as MaterialButton).isChecked = true
    }
}
