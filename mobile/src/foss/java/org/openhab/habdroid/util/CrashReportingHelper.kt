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
import org.acra.ACRA
import org.acra.config.CoreConfigurationBuilder
import org.acra.config.MailSenderConfigurationBuilder
import org.acra.config.NotificationConfigurationBuilder
import org.openhab.habdroid.BuildConfig
import org.openhab.habdroid.R

object CrashReportingHelper {
    private val TAG = CrashReportingHelper::class.java.simpleName

    fun initialize(app: Application) {
        val outdatedBuildMillis = BuildConfig.TIMESTAMP + (6L * 30 * 24 * 60 * 60 * 1000) // 6 months after build
        val isOutdated = outdatedBuildMillis < System.currentTimeMillis()
        Log.d(TAG, "ACRA status: isOutdated $isOutdated")
        if (isOutdated) {
            return
        }

        val builder = CoreConfigurationBuilder(app)
            .withBuildConfigClass(BuildConfig::class.java)
            .apply {
                getPluginConfigurationBuilder(NotificationConfigurationBuilder::class.java)
                    .withEnabled(true)
                    .withResIcon(R.drawable.ic_openhab_appicon_white_24dp)
                    .withResTitle(R.string.crash_report_notification_title)
                    .withResText(R.string.crash_report_notification_text)
                    .withResSendButtonText(R.string.crash_report_notification_send_mail)
                    .withResSendButtonIcon(0)
                    .withResDiscardButtonIcon(0)
                    .withResChannelName(R.string.notification_channel_crash_reports)
                    .withResChannelDescription(R.string.notification_channel_crash_reports_description)
                    .withSendOnClick(false)

                getPluginConfigurationBuilder(MailSenderConfigurationBuilder::class.java)
                    .withEnabled(true)
                    .withMailTo("apps@openhabfoundation.org")
        }

        ACRA.init(app, builder)
    }

    fun isCrashReporterProcess(): Boolean {
        return ACRA.isACRASenderServiceProcess()
    }

    fun canBeDisabledByUser() = false

    fun d(tag: String, message: String, remoteOnly: Boolean = false, exception: Exception? = null) {
        if (!remoteOnly) {
            Log.d(tag, message, exception)
        }
    }

    fun e(tag: String, message: String, remoteOnly: Boolean = false, exception: Exception? = null) {
        if (!remoteOnly) {
            Log.e(tag, message, exception)
        }
    }

    fun nonFatal(@Suppress("UNUSED_PARAMETER") e: Throwable) {
        // no-op
    }
}
