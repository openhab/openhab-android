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

package org.openhab.habdroid.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.openhab.habdroid.R
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.util.SuggestedCommandsFactory

/**
 * Item picker that returns the selected item as result intent
 */
class BasicItemPickerActivity(
    override var hintMessageId: Int = 0,
    override var hintButtonMessageId: Int = 0,
    override var hintIconId: Int = 0
) : AbstractItemPickerActivity() {
    private var showNoCommand = false
    private var selectItemOnly = false
    override val forItemCommandOnly get() = !selectItemOnly

    override fun onCreate(savedInstanceState: Bundle?) {
        initialHighlightItemName = intent.getStringExtra(EXTRA_ITEM)
        showNoCommand = intent.getBooleanExtra(EXTRA_SHOW_NO_COMMAND, showNoCommand)
        hideReadOnly = intent.getBooleanExtra(EXTRA_HIDE_READ_ONLY, hideReadOnly)
        selectItemOnly = intent.getBooleanExtra(EXTRA_SELECT_ITEM_ONLY, selectItemOnly)
        super.onCreate(savedInstanceState)
    }

    override fun onItemClicked(item: Item) {
        if (selectItemOnly) {
            finish(item, null)
        } else {
            super.onItemClicked(item)
        }
    }

    override fun finish(item: Item, state: String?, mappedState: String?, tag: Any?) {
        val label = if (item.label.isNullOrEmpty()) item.name else item.label
        val resultIntent = Intent().apply {
            putExtra("item", item.name)
            putExtra("label", label)
            putExtra("state", state)
            putExtra("mappedState", mappedState)
            putExtra("icon", item.category)
            putExtra("tags", item.tags.toTypedArray())
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun addAdditionalCommands(
        suggestedCommands: SuggestedCommandsFactory.SuggestedCommands,
        entries: MutableList<CommandEntry>
    ) {
        if (showNoCommand) {
            entries.add(CommandEntry(null, getString(R.string.widget_no_command)))
        }
    }

    companion object {
        private const val EXTRA_ITEM = "item"
        private const val EXTRA_SHOW_NO_COMMAND = "show_no_command"
        private const val EXTRA_HIDE_READ_ONLY = "hide_read_only"
        private const val EXTRA_SELECT_ITEM_ONLY = "select_item_only"

        /**
         * @param initialItem Name of the Item that should be highlighted initially
         * @param showNoCommand Whether to offer "no command" as additional command
         * @param hideReadOnly Whether to hide read-only Items
         * @param selectItemOnly Return the Item directly when clicked instead of asking for a command
         */
        fun createIntent(
            context: Context,
            initialItem: String?,
            showNoCommand: Boolean = false,
            hideReadOnly: Boolean = true,
            selectItemOnly: Boolean = false
        ) = Intent(context, BasicItemPickerActivity::class.java).apply {
            putExtra(EXTRA_ITEM, initialItem)
            putExtra(EXTRA_SHOW_NO_COMMAND, showNoCommand)
            putExtra(EXTRA_HIDE_READ_ONLY, hideReadOnly)
            putExtra(EXTRA_SELECT_ITEM_ONLY, selectItemOnly)
        }
    }
}
