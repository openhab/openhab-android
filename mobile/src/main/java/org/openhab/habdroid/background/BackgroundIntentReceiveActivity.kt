/*
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import org.openhab.habdroid.model.toTagData
import org.openhab.habdroid.ui.MainActivity

class BackgroundIntentReceiveActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent == null) {
            finishAndRemoveTaskIfPossible()
            return
        }

        if (intent.action == Intent.ACTION_VIEW || intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
            val tag = intent.data?.toTagData()
            val sitemap = tag?.sitemap
            BackgroundTasksManager.enqueueNfcUpdateIfNeeded(this, tag)
            if (!sitemap.isNullOrEmpty()) {
                val startMainIntent = Intent(this, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_SITEMAP_SELECTED
                    putExtra(MainActivity.EXTRA_SITEMAP_URL, sitemap)
                }
                startActivity(startMainIntent)
            }
        } else if (intent.action == BackgroundTasksManager.ACTION_UPDATE_WIDGET && intent.extras != null) {
            BackgroundTasksManager.enqueueWidgetItemUpdateIfNeeded(intent.extras!!)
        }

        finishAndRemoveTaskIfPossible()
    }

    private fun finishAndRemoveTaskIfPossible() {
        if (Build.VERSION.SDK_INT >= 21) {
            finishAndRemoveTask()
        } else {
            finish()
        }
    }
}
