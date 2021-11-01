/*
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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

import android.app.Application
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.openhab.habdroid.BuildConfig

object CrashReportingHelper {
    private val TAG = CrashReportingHelper::class.java.simpleName

    fun initialize(app: Application) {
        val outdatedBuildMillis = BuildConfig.TIMESTAMP + (6L * 30 * 24 * 60 * 60 * 1000) // 6 months after build
        val isOutdated = outdatedBuildMillis < System.currentTimeMillis()
        val isUserEnabled = app.getPrefs().getBoolean(PrefKeys.CRASH_REPORTING, true)
        Log.d(
            TAG,
            "Crashlytics status: isDebug ${BuildConfig.DEBUG}, isOutdated $isOutdated, isUserEnabled $isUserEnabled"
        )
        FirebaseCrashlytics.getInstance()
            .setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG && !isOutdated && isUserEnabled)
    }

    // Only required for ACRA
    fun isCrashReporterProcess(): Boolean {
        return false
    }

    fun d(tag: String, message: String, remoteOnly: Boolean = false, exception: Exception? = null) {
        FirebaseCrashlytics.getInstance().log("D/$tag: $message; ${exception?.stackTraceToString()}")
        if (!remoteOnly) {
            Log.d(tag, message, exception)
        }
    }

    fun e(tag: String, message: String, remoteOnly: Boolean = false, exception: Exception? = null) {
        FirebaseCrashlytics.getInstance().log("E/$tag: $message; ${exception?.stackTraceToString()}")
        if (!remoteOnly) {
            Log.e(tag, message, exception)
        }
    }

    fun nonFatal(e: Throwable) {
        FirebaseCrashlytics.getInstance().recordException(e)
    }
}
