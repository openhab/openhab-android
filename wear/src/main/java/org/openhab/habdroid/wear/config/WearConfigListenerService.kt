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

package org.openhab.habdroid.wear.config

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import org.openhab.habdroid.core.wear.WearConfig
import org.openhab.habdroid.wear.wearApplication

/**
 * Receives the config the phone app publishes via the Data Layer, even if the app isn't running.
 */
class WearConfigListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents
            .filter { event -> event.dataItem.uri.path == WearConfig.DATA_PATH }
            .forEach { event ->
                val config = if (event.type == DataEvent.TYPE_CHANGED) {
                    DataMapItem.fromDataItem(event.dataItem).dataMap
                        .getString(WearConfig.DATA_KEY_CONFIG)
                        ?.let { json -> WearConfig.fromJson(json) }
                } else {
                    null
                }
                wearApplication.configRepository.saveConfigFromPhone(config)
            }
    }
}
