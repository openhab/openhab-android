/*
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
import androidx.annotation.LayoutRes
import org.openhab.habdroid.model.Item

/**
 * Item picker that returns the selected item as result intent
 */
class BasicItemPickerActivity(
    override var hintMessageId: Int = 0,
    override var hintButtonMessageId: Int = 0,
    override var hintIconId: Int = 0
) : AbstractItemPickerActivity() {
    @LayoutRes override val additionalConfigLayoutRes: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        initialHighlightItemName = intent.getStringExtra("item")
        super.onCreate(savedInstanceState)
    }

    override fun finish(item: Item, state: String, mappedState: String, tag: Any?) {
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
}
