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

import android.content.Context
import android.util.Log
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.core.CrashlyticsCore
import io.fabric.sdk.android.Fabric
import org.openhab.habdroid.BuildConfig

object RemoteLog {
    private val TAG = RemoteLog::class.java.simpleName

    fun initialize(context: Context) {
        val outdatedBuildMillis = BuildConfig.TIMESTAMP + (6L * 30 * 24 * 60 * 60 * 1000) // 6 months after build
        val isOutdated = outdatedBuildMillis < System.currentTimeMillis()
        Log.d(TAG, "Crashlytics status: isDebug ${BuildConfig.DEBUG}, isOutdated $isOutdated")
        val core = CrashlyticsCore.Builder().disabled(BuildConfig.DEBUG || isOutdated).build()
        Fabric.with(context, Crashlytics.Builder().core(core).build())
    }

    fun d(tag: String, message: String, remoteOnly: Boolean = false) {
        if (remoteOnly) {
            Crashlytics.log("[$tag] $message")
        } else {
            Crashlytics.log(Log.DEBUG, tag, message)
        }
    }

    fun e(tag: String, message: String, remoteOnly: Boolean = false) {
        if (remoteOnly) {
            Crashlytics.log("[$tag] $message")
        } else {
            Crashlytics.log(Log.ERROR, tag, message)
        }
    }
}
