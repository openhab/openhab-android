/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import org.openhab.habdroid.ui.preference.PreferencesActivity
import org.openhab.habdroid.util.getConfiguredServerIds
import org.openhab.habdroid.util.getPrefs

/**
 * Opened by the Wear OS app via `openhab://wear-setup` to set up a server for the watch.
 */
class WearSetupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val startIntent = if (getPrefs().getConfiguredServerIds().isEmpty()) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, PreferencesActivity::class.java).setAction(PreferencesActivity.ACTION_WEAR_SETUP)
        }
        startActivity(startIntent)
        finish()
    }
}
