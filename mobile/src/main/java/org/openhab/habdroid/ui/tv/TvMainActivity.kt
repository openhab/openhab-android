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
import org.openhab.habdroid.R
import org.openhab.habdroid.model.ServerConfiguration
import org.openhab.habdroid.model.ServerPath
import org.openhab.habdroid.util.getConnectionFactory
import org.openhab.habdroid.util.getNextAvailableServerId
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getSecretPrefs
import org.openhab.habdroid.util.loadActiveServerConfig

/**
 * Entry point of the app on TV devices. The TV UI only supports sitemaps and is fully usable with a D-pad.
 */
class TvMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvTheme {
                TvApp(
                    onAddServer = ::addServer,
                    onSaveServer = ::saveServer
                )
            }
        }
    }

    /**
     * Add the first server during the setup
     */
    private fun addServer(url: String, userName: String?, password: String?) {
        val prefs = getPrefs()
        val config = ServerConfiguration(
            prefs.getNextAvailableServerId(),
            getString(R.string.openhab),
            ServerPath(url, userName, password),
            null,
            null,
            null,
            null,
            false,
            null,
            null
        )
        config.saveToPrefs(prefs, getSecretPrefs())
    }

    /**
     * Update URL and credentials of the active server. Invalid URLs are ignored.
     */
    private fun saveServer(url: String, userName: String, password: String) {
        val config = loadActiveServerConfig() ?: return
        val trimmedUrl = url.trim()
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            return
        }
        val path = ServerPath(trimmedUrl, userName.ifEmpty { null }, password.ifEmpty { null })
        if (path != config.localPath) {
            ServerConfiguration.createFrom(config, localPath = path)
                .saveToPrefs(getPrefs(), getSecretPrefs())
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
