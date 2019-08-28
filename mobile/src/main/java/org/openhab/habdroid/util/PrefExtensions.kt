/*
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri
import org.openhab.habdroid.R
import org.openhab.habdroid.model.Sitemap

enum class ScreenLockMode {
    Disabled,
    KioskMode,
    Enabled
}

fun SharedPreferences.getLocalUrl(): String {
    return getString(Constants.PREFERENCE_LOCAL_URL)
}

fun SharedPreferences.getRemoteUrl(): String {
    return getString(Constants.PREFERENCE_REMOTE_URL)
}

fun SharedPreferences.getDefaultSitemap(): String {
    return getString(Constants.PREFERENCE_SITEMAP_NAME)
}

fun SharedPreferences.getIconFormat(): String {
    return getString(Constants.PREFERENCE_ICON_FORMAT, "PNG").orEmpty()
}

fun SharedPreferences.isDemoModeEnabled(): Boolean {
    return getBoolean(Constants.PREFERENCE_DEMO_MODE, false)
}

fun SharedPreferences.isDebugModeEnabled(): Boolean {
    return getBoolean(Constants.PREFERENCE_DEBUG_MESSAGES, false)
}

fun SharedPreferences.getNotificationTone(): Uri? {
    val tone = getString(Constants.PREFERENCE_TONE, null)
    return when {
        tone == null -> Settings.System.DEFAULT_NOTIFICATION_URI
        tone.isEmpty() -> null
        else -> tone.toUri()
    }
}

fun SharedPreferences.isScreenTimerDisabled(): Boolean {
    return getBoolean(Constants.PREFERENCE_SCREEN_TIMER_OFF, false)
}

fun SharedPreferences.getChartScalingFactor(): Float {
    return getFloat(Constants.PREFERENCE_CHART_SCALING, 1.0F)
}

fun SharedPreferences.shouldRequestHighResChart(): Boolean {
    return getBoolean(Constants.PREFERENCE_CHART_HQ, true)
}

fun SharedPreferences.isTaskerPluginEnabled(): Boolean {
    return getBoolean(Constants.PREFERENCE_TASKER_PLUGIN_ENABLED, false)
}

fun SharedPreferences.getString(key: String): String {
    return getString(key, "").orEmpty()
}

fun SharedPreferences.getScreenLockMode(context: Context): ScreenLockMode {
    val settingValue = getString(Constants.PREFERENCE_SCREEN_LOCK,
        context.getString(R.string.settings_screen_lock_off_value))
    return when (settingValue) {
        context.getString(R.string.settings_screen_lock_kiosk_value) -> ScreenLockMode.KioskMode
        context.getString(R.string.settings_screen_lock_on_value) -> ScreenLockMode.Enabled
        else -> ScreenLockMode.Disabled
    }
}

/**
 * Returns vibration pattern for notifications that can be passed to
 * [}][androidx.core.app.NotificationCompat.Builder.setVibrate]
 */
fun SharedPreferences.getNotificationVibrationPattern(context: Context): LongArray {
    return when (getString(Constants.PREFERENCE_NOTIFICATION_VIBRATION)) {
        context.getString(R.string.settings_notification_vibration_value_short) -> longArrayOf(0, 500, 500)
        context.getString(R.string.settings_notification_vibration_value_long) -> longArrayOf(0, 1000, 1000)
        context.getString(R.string.settings_notification_vibration_value_twice) -> {
            longArrayOf(0, 1000, 1000, 1000, 1000)
        }
        else -> longArrayOf(0)
    }
}

fun SharedPreferences.Editor.updateDefaultSitemap(sitemap: Sitemap?) {
    if (sitemap == null) {
        remove(Constants.PREFERENCE_SITEMAP_NAME)
        remove(Constants.PREFERENCE_SITEMAP_LABEL)
    } else {
        putString(Constants.PREFERENCE_SITEMAP_NAME, sitemap.name)
        putString(Constants.PREFERENCE_SITEMAP_LABEL, sitemap.label)
    }
}
