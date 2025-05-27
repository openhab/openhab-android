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

package org.openhab.habdroid.util

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.connection.DemoConnection
import org.openhab.habdroid.model.DefaultSitemap
import org.openhab.habdroid.model.IconFormat
import org.openhab.habdroid.model.ServerConfiguration
import org.openhab.habdroid.model.ServerProperties
import org.openhab.habdroid.model.Sitemap
import org.openhab.habdroid.ui.preference.widgets.toItemUpdatePrefValue
import org.openhab.habdroid.ui.widget.WidgetImageView

enum class ScreenLockMode {
    Disabled,
    KioskMode,
    Enabled
}

fun SharedPreferences.getActiveServerId(): Int = getInt(PrefKeys.ACTIVE_SERVER_ID, 0)

fun SharedPreferences.getPrimaryServerId(): Int = getInt(PrefKeys.PRIMARY_SERVER_ID, 0)

fun SharedPreferences.getNextAvailableServerId(): Int = getStringSet(PrefKeys.SERVER_IDS, null)
    ?.lastOrNull()
    .orDefaultIfEmpty("0")
    .let { idString -> idString.toInt() + 1 }

fun SharedPreferences.getConfiguredServerIds(): MutableSet<Int> = getStringSet(PrefKeys.SERVER_IDS, null)
    ?.map { id -> id.toInt() }
    ?.toMutableSet()
    ?: mutableSetOf()

fun SharedPreferences.getLocalUrl(id: Int = getActiveServerId()): String =
    getStringOrNull(PrefKeys.buildServerKey(id, PrefKeys.LOCAL_URL_PREFIX)).orEmpty()

fun SharedPreferences.getRemoteUrl(id: Int = getActiveServerId()): String =
    getStringOrNull(PrefKeys.buildServerKey(id, PrefKeys.REMOTE_URL_PREFIX)).orEmpty()

fun SharedPreferences.getDefaultSitemap(connection: Connection?, id: Int = getActiveServerId()): DefaultSitemap? {
    if (connection is DemoConnection) {
        return DefaultSitemap("demo", "demo")
    }
    return ServerConfiguration.getDefaultSitemap(this, id)
}

fun SharedPreferences.getIconFormat(): IconFormat {
    val serverProps = getInt(PrefKeys.PREV_SERVER_FLAGS, 0)
    if (serverProps and ServerProperties.SERVER_FLAG_SUPPORTS_ANY_FORMAT_ICON != 0) {
        return IconFormat.Svg
    }
    val formatString = getStringOrFallbackIfEmpty(PrefKeys.ICON_FORMAT, "PNG")
    return if (formatString == "SVG") IconFormat.Svg else IconFormat.Png
}

fun SharedPreferences.isDemoModeEnabled(): Boolean = getBoolean(PrefKeys.DEMO_MODE, false)

fun SharedPreferences.isDebugModeEnabled(): Boolean = getBoolean(PrefKeys.DEBUG_MESSAGES, false)

fun SharedPreferences.getNotificationTone(): Uri? {
    val tone = getStringOrNull(PrefKeys.NOTIFICATION_TONE)
    return when {
        tone == null -> Settings.System.DEFAULT_NOTIFICATION_URI
        tone.isEmpty() -> null
        else -> tone.toUri()
    }
}

fun SharedPreferences.isScreenTimerDisabled(): Boolean = getBoolean(PrefKeys.SCREEN_TIMER_OFF, false)

fun SharedPreferences.getChartScalingFactor(): Float = getFloat(PrefKeys.CHART_SCALING, 1.5F)

fun SharedPreferences.shouldRequestHighResChart(): Boolean = getBoolean(PrefKeys.CHART_HQ, true)

fun SharedPreferences.useCompactSitemapLayout(): Boolean = getBoolean(PrefKeys.SITEMAP_COMPACT_MODE, false)

fun SharedPreferences.getImageWidgetScalingType(): WidgetImageView.ImageScalingType =
    if (getBoolean(PrefKeys.IMAGE_WIDGET_SCALE_TO_FIT, true)) {
        WidgetImageView.ImageScalingType.ScaleToFitWithViewAdjustment
    } else {
        WidgetImageView.ImageScalingType.ScaleToFitWithViewAdjustmentDownscaleOnly
    }

fun SharedPreferences.isTaskerPluginEnabled(): Boolean = getBoolean(PrefKeys.TASKER_PLUGIN_ENABLED, false)

fun SharedPreferences.wasNfcInfoHintShown(): Boolean = getBoolean(PrefKeys.NFC_INFO_HINT_SHOWN, false)

fun SharedPreferences.getDayNightMode(context: Context): Int = when (getStringOrNull(PrefKeys.THEME)) {
    context.getString(R.string.theme_value_light) -> AppCompatDelegate.MODE_NIGHT_NO
    context.getString(R.string.theme_value_dark), context.getString(R.string.theme_value_black) ->
        AppCompatDelegate.MODE_NIGHT_YES
    else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    } else {
        AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
    }
}

fun SharedPreferences.areSitemapsShownInDrawer(): Boolean = getBoolean(PrefKeys.SHOW_SITEMAPS_IN_DRAWER, false)

fun SharedPreferences.getBackgroundTaskScheduleInMillis(): Long {
    val value = getStringOrFallbackIfEmpty(PrefKeys.SEND_DEVICE_INFO_SCHEDULE, "360")
    // Value is stored in minutes, but we need millis to compare it
    return value.toInt() * 60 * 1000L
}

fun SharedPreferences.getPrefixForVoice(): String? {
    val enabled = getBoolean(PrefKeys.DEV_ID_PREFIX_VOICE, false)
    return if (enabled) getStringOrEmpty(PrefKeys.DEV_ID) else null
}

fun SharedPreferences.getPrefixForBgTasks(): String {
    val enabled = getBoolean(PrefKeys.DEV_ID_PREFIX_BG_TASKS, true)
    return if (enabled) getStringOrEmpty(PrefKeys.DEV_ID) else ""
}

fun SharedPreferences.getStringOrNull(key: String): String? = getString(key, null)

fun SharedPreferences.getStringOrEmpty(key: String): String = getString(key, "").orEmpty()

fun SharedPreferences.getStringOrFallbackIfEmpty(key: String, fallback: String): String {
    val value = getStringOrNull(key)
    return if (value.isNullOrEmpty()) fallback else value
}

fun SharedPreferences.getScreenLockMode(context: Context): ScreenLockMode =
    when (getStringOrNull(PrefKeys.SCREEN_LOCK)) {
        context.getString(R.string.settings_screen_lock_kiosk_value) -> ScreenLockMode.KioskMode
        context.getString(R.string.settings_screen_lock_on_value) -> ScreenLockMode.Enabled
        else -> ScreenLockMode.Disabled
    }

fun SharedPreferences.isItemUpdatePrefEnabled(key: String) = getString(key, null).toItemUpdatePrefValue().first

fun SharedPreferences.isEventListenerEnabled() = getBoolean(PrefKeys.SEND_DEVICE_INFO_FOREGROUND_SERVICE, false) &&
    isDataSyncForegroundServiceAllowed()

enum class DeviceControlSubtitleMode {
    LOCATION,
    EQUIPMENT,
    LOCATION_AND_EQUIPMENT,
    ITEM_NAME
}

fun SharedPreferences.getDeviceControlSubtitle(context: Context): DeviceControlSubtitleMode =
    when (getStringOrNull(PrefKeys.DEVICE_CONTROL_SUBTITLE)) {
        context.getString(R.string.device_control_subtitle_equipment_value) -> DeviceControlSubtitleMode.EQUIPMENT
        context.getString(R.string.device_control_subtitle_location_equipment_value) ->
            DeviceControlSubtitleMode.LOCATION_AND_EQUIPMENT
        context.getString(R.string.device_control_subtitle_item_name_value) -> DeviceControlSubtitleMode.ITEM_NAME
        else -> DeviceControlSubtitleMode.LOCATION
    }

/**
 * Returns vibration pattern for notifications that can be passed to
 * [}][androidx.core.app.NotificationCompat.Builder.setVibrate]
 */
fun SharedPreferences.getNotificationVibrationPattern(context: Context): LongArray =
    when (getStringOrNull(PrefKeys.NOTIFICATION_VIBRATION)) {
        context.getString(R.string.settings_notification_vibration_value_short) -> longArrayOf(0, 500, 500)
        context.getString(R.string.settings_notification_vibration_value_long) -> longArrayOf(0, 1000, 1000)
        context.getString(R.string.settings_notification_vibration_value_twice) -> {
            longArrayOf(0, 1000, 1000, 1000, 1000)
        }
        else -> longArrayOf(0)
    }

fun SharedPreferences.Editor.putConfiguredServerIds(ids: Set<Int>) {
    putStringSet(PrefKeys.SERVER_IDS, ids.map { id -> id.toString() }.toSet())
}

fun SharedPreferences.Editor.putActiveServerId(id: Int) {
    putInt(PrefKeys.ACTIVE_SERVER_ID, id)
}

fun SharedPreferences.Editor.putPrimaryServerId(id: Int) {
    putInt(PrefKeys.PRIMARY_SERVER_ID, id)
}

fun SharedPreferences.updateDefaultSitemap(connection: Connection?, sitemap: Sitemap?, id: Int = getActiveServerId()) {
    if (connection is DemoConnection) {
        return
    }
    val defaultSitemap = sitemap?.let { DefaultSitemap(sitemap.name, sitemap.label) }
    ServerConfiguration.saveDefaultSitemap(this, id, defaultSitemap)
}

fun PreferenceFragmentCompat.getPreference(key: String): Preference =
    findPreference(key) ?: throw IllegalArgumentException("No such preference: $key")
