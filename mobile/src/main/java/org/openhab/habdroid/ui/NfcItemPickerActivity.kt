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

import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.core.content.edit
import org.openhab.habdroid.R
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.wasNfcInfoHintShown

class NfcItemPickerActivity(
    override var hintMessageId: Int = R.string.nfc_info_hint,
    override var hintButtonMessageId: Int = R.string.got_it,
    override var hintIconId: Int = R.drawable.ic_nfc_black_120dp
) : AbstractItemPickerActivity() {
    override val forceNonFullscreen = false
    @LayoutRes override val additionalConfigLayoutRes: Int = R.layout.nfc_item_picker_config

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        retryButton.setOnClickListener {
            if (needToShowHint) {
                getPrefs().edit {
                    putBoolean(PrefKeys.NFC_INFO_HINT_SHOWN, true)
                }
                needToShowHint = false
            }
            loadItems()
        }

        if (!getPrefs().wasNfcInfoHintShown()) {
            needToShowHint = true
            updateViewVisibility(loading = false, loadError = false, showHint = true)
        }
    }

    override fun finish(item: Item, state: String, mappedState: String) {
        startActivity(WriteTagActivity.createItemUpdateIntent(this, item.name, state, mappedState, item.label))
    }
}
