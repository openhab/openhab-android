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
import org.openhab.habdroid.wear.config.WearConfigRepository
import org.openhab.habdroid.wear.connection.WearConnectionManager

class WearApplication : Application() {
    val appScope: CoroutineScope = MainScope()

    val configRepository: WearConfigRepository by lazy { WearConfigRepository(this) }

    val connectionManager: WearConnectionManager by lazy {
        WearConnectionManager(this, configRepository, appScope)
    }
}

val Context.wearApplication get() = applicationContext as WearApplication
