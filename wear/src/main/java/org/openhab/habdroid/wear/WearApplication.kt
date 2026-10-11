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

package org.openhab.habdroid.wear

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.complication.ItemComplicationService
import org.openhab.habdroid.wear.config.WearConfigRepository
import org.openhab.habdroid.wear.connection.WearConnectionManager
import org.openhab.habdroid.wear.tile.SitemapTileService

class WearApplication : Application() {
    val appScope: CoroutineScope = MainScope()

    val configRepository: WearConfigRepository by lazy { WearConfigRepository(this) }

    val connectionManager: WearConnectionManager by lazy {
        WearConnectionManager(this, configRepository, appScope)
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            // Tiles and complications show data of the configured server, so reload them if the config changes
            configRepository.settings.drop(1).collectLatest {
                connectionManager.state.first { state -> state !is WearConnectionManager.State.Connecting }
                SitemapTileService.requestUpdate(this@WearApplication)
                ItemComplicationService.requestUpdate(this@WearApplication)
            }
        }
    }
}

val Context.wearApplication get() = applicationContext as WearApplication
