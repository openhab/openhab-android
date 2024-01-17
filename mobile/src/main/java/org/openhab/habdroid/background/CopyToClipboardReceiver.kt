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

package org.openhab.habdroid.background

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import org.openhab.habdroid.R
import org.openhab.habdroid.util.showToast

class CopyToClipboardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val toCopy = intent.getStringExtra(EXTRA_TO_COPY) ?: return
        Log.d(TAG, "Copy to clipboard: $toCopy")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(context.getString(R.string.app_name), toCopy)
        clipboard.setPrimaryClip(clip)
        context.showToast(context.getString(R.string.copied_item_name, toCopy))
    }

    companion object {
        private val TAG = CopyToClipboardReceiver::class.java.simpleName
        const val EXTRA_TO_COPY = "extra_to_copy"
    }
}
