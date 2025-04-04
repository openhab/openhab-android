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

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.openhab.habdroid.core.CloudMessagingHelper
import org.openhab.habdroid.ui.homescreenwidget.ItemUpdateWidget

class PeriodicItemUpdateWorker(val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork()")
        doPeriodicWork(context)
        return Result.success()
    }

    companion object {
        private val TAG = PeriodicItemUpdateWorker::class.java.simpleName

        suspend fun doPeriodicWork(context: Context) {
            Log.d(TAG, "doPeriodicWork()")
            BackgroundTasksManager.scheduleUpdatesForAllKeys(context)
            ItemUpdateWidget.updateAllWidgets(context)
            if (CloudMessagingHelper.needsPollingForNotifications(context)) {
                CloudMessagingHelper.pollForNotifications(context)
            }
        }
    }
}
