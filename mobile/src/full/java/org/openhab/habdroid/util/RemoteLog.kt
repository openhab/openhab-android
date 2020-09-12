/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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

package org.openhab.habdroid.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.openhab.habdroid.BuildConfig

object RemoteLog {
    private val TAG = RemoteLog::class.java.simpleName

    fun initialize() {
        val outdatedBuildMillis = BuildConfig.TIMESTAMP + (6L * 30 * 24 * 60 * 60 * 1000) // 6 months after build
        val isOutdated = outdatedBuildMillis < System.currentTimeMillis()
        Log.d(TAG, "Crashlytics status: isDebug ${BuildConfig.DEBUG}, isOutdated $isOutdated")
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG && !isOutdated)
    }

    fun d(tag: String, message: String, remoteOnly: Boolean = false) {
        FirebaseCrashlytics.getInstance().log("D/$tag: $message")
        if (!remoteOnly) {
            Log.d(tag, message)
        }
    }

    fun e(tag: String, message: String, remoteOnly: Boolean = false) {
        FirebaseCrashlytics.getInstance().log("E/$tag: $message")
        if (!remoteOnly) {
            Log.e(tag, message)
        }
    }

    fun nonFatal(e: Throwable) {
        FirebaseCrashlytics.getInstance().recordException(e)
    }
}
