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
package org.openhab.habdroid.ui.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.openhab.habdroid.util.getConnectionFactory

/**
 * Entry point of the app on TV devices. The TV UI only supports sitemaps and is fully usable with a D-pad.
 */
class TvMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvTheme {
                TvApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Allows asking the user whether to trust an unknown certificate, otherwise the connection hangs forever
        getConnectionFactory().trustManager.bindDisplayActivity(this)
    }

    override fun onStop() {
        super.onStop()
        getConnectionFactory().trustManager.unbindDisplayActivity(this)
    }
}
