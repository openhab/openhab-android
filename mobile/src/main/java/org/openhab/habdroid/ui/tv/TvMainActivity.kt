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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
                var configVersion by remember { mutableIntStateOf(0) }
                val serverConfig = remember(configVersion) {
                    loadActiveServerConfig()
                }

                TvApp(
                    config = serverConfig,
                    connectionProvider = { getConnectionFactory().currentActive?.conn?.connection },
                    onSaveServerPath = { path ->
                        saveServer(path)
                        configVersion++
                    },
                    onSaveDefaultSitemap = { sitemap ->
                        serverConfig?.id?.let { configId ->
                            ServerConfiguration.saveDefaultSitemap(getPrefs(), configId, sitemap)
                            configVersion++
                        }
                    }
                )
            }
        }
    }

    private fun saveServer(path: ServerPath) {
        val prefs = getPrefs()
        val existing = loadActiveServerConfig()
        val newConfig = if (existing != null) {
            ServerConfiguration.createFrom(existing, localPath = path)
        } else {
            ServerConfiguration(
                prefs.getNextAvailableServerId(),
                getString(R.string.openhab),
                path,
                null,
                null,
                null,
                null,
                false,
                null,
                null
            )
        }

        newConfig.saveToPrefs(prefs, getSecretPrefs())
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
