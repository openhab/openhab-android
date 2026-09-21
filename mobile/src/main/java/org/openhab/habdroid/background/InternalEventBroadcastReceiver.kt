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

package org.openhab.habdroid.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager
import org.openhab.habdroid.util.getBackgroundTasksManager

class InternalEventBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(BackgroundTasksManager.TAG, "Internal onReceive() with intent ${intent.action}")
        val backgroundTasksManager = context.getBackgroundTasksManager()
        backgroundTasksManager.handleInternalBroadcast(intent)
    }
}
