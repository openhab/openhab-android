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

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import org.openhab.habdroid.model.toTagData
import org.openhab.habdroid.ui.MainActivity
import org.openhab.habdroid.ui.preference.PreferencesActivity
import org.openhab.habdroid.util.getConfiguredServerIds
import org.openhab.habdroid.util.getPrefs

class NfcReceiveActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent == null) {
            finishAndRemoveTask()
            return
        }

        if (intent.action == Intent.ACTION_VIEW && intent.data?.host == WEAR_SETUP_HOST) {
            // Opened by the Wear OS app to set up a server
            val startIntent = if (getPrefs().getConfiguredServerIds().isEmpty()) {
                Intent(this, MainActivity::class.java)
            } else {
                Intent(this, PreferencesActivity::class.java).setAction(PreferencesActivity.ACTION_WEAR_SETUP)
            }
            startActivity(startIntent)
        } else if (intent.action == Intent.ACTION_VIEW || intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
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
        }

        finishAndRemoveTask()
    }

    companion object {
        private const val WEAR_SETUP_HOST = "wear-setup"
    }
}
