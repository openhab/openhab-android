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

package org.openhab.habdroid.wear.complication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.launch
import org.json.JSONException
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.wear.tile.SitemapTileService
import org.openhab.habdroid.wear.util.buildSourceId
import org.openhab.habdroid.wear.util.loadItem
import org.openhab.habdroid.wear.wearApplication

/**
 * Toggles a switch item when its complication is tapped
 */
class ComplicationTapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val itemName = intent.getStringExtra(EXTRA_ITEM_NAME) ?: return
        val pendingResult = goAsync()
        val app = context.wearApplication
        app.appScope.launch {
            try {
                val connection = app.connectionManager.awaitConnection()?.connection ?: return@launch
                // Load the current state, as the complication might show an outdated state
                val item = connection.loadItem(itemName)
                val command = if (item.state?.asBoolean == true) "OFF" else "ON"
                Log.d(TAG, "Send $command to $itemName")
                connection.httpClient.sendItemCommand(item, command, buildSourceId(null, null))?.join()

                // Other complications or the tile might show the same item
                ItemComplicationService.requestUpdate(context)
                SitemapTileService.requestUpdate(context)
            } catch (e: HttpClient.HttpException) {
                Log.w(TAG, "Toggling $itemName failed", e)
            } catch (e: JSONException) {
                Log.w(TAG, "Parsing $itemName failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val TAG = ComplicationTapReceiver::class.java.simpleName
        const val EXTRA_ITEM_NAME = "item_name"
    }
}
