/*
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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

object PrefKeys {
    /**
     * Settings keys
     */
    const val DEMO_MODE = "default_openhab_demomode"
    const val SERVER_IDS = "server_ids"
    const val ACTIVE_SERVER_ID = "active_server_id"
    const val SERVER_NAME_PREFIX = "server_name_"
    const val LOCAL_URL_PREFIX = "local_url_"
    const val LOCAL_USERNAME_PREFIX = "local_username_"
    const val LOCAL_PASSWORD_PREFIX = "local_password_"
    const val REMOTE_URL_PREFIX = "remote_url_"
    const val REMOTE_USERNAME_PREFIX = "remote_username_"
    const val REMOTE_PASSWORD_PREFIX = "remote_password_"
    const val PRIMARY_SERVER_PREF = "primary_server_pref"
    const val PRIMARY_SERVER_ID = "primary_server_id"
    const val SSL_CLIENT_CERT_PREFIX = "sslclientcert_"
    const val DEFAULT_SITEMAP_NAME_PREFIX = "default_sitemap_name_"
    const val DEFAULT_SITEMAP_LABEL_PREFIX = "default_sitemap_label_"
    const val WIFI_SSID_PREFIX = "wifi_ssid_"
    const val RESTRICT_TO_SSID_PREFIX = "restrict_to_ssid_"
    const val FRONTAIL_URL_PREFIX = "frontail_url_"
    const val CLEAR_DEFAULT_SITEMAP = "clear_default_sitemap"
    fun buildServerKey(id: Int, prefix: String) = "$prefix$id"

    const val START_PAGE = "start_page"
    const val SHOW_SITEMAPS_IN_DRAWER = "show_sitemaps"
    const val SITEMAP_COMPACT_MODE = "sitemap_compact_mode"
    const val SHOW_ICONS = "show_icons"
    const val ICON_FORMAT = "iconFormatType"
    const val CLEAR_CACHE = "default_openhab_cleacache"
    const val CHART_SCALING = "chartScalingFactor"
    const val CHART_HQ = "default_openhab_chart_hq"
    const val IMAGE_WIDGET_SCALE_TO_FIT = "imageWidgetScaleToFit"

    const val DRAWER_ENTRIES = "drawer_entries"
    const val THEME = "theme"
    const val COLOR_SCHEME = "color_scheme"
    const val SCREEN_TIMER_OFF = "default_openhab_screentimeroff"
    const val FULLSCREEN = "default_openhab_fullscreen"
    const val LAUNCHER = "launcher"

    const val SEND_DEVICE_INFO_SCHEDULE = "send_device_info_schedule"
    const val SEND_DEVICE_INFO_FOREGROUND_SERVICE = "send_device_info_foreground_service"
    const val SEND_ALARM_CLOCK = "alarmClock"
    const val SEND_PHONE_STATE = "phoneState"
    const val SEND_BATTERY_LEVEL = "battery_level"
    const val SEND_CHARGING_STATE = "charging_state"
    const val SEND_WIFI_SSID = "send_wifi_ssid"
    const val SEND_DND_MODE = "send_dnd_mode"
    const val SEND_BLUETOOTH_DEVICES = "send_bluetooth_devices"
    const val SEND_GADGETBRIDGE = "send_gadgetbridge"

    const val NOTIFICATION_STATUS_HINT = "notification_status"
    const val NOTIFICATION_TONE = "default_openhab_alertringtone"
    const val NOTIFICATION_VIBRATION = "default_openhab_notification_vibration"
    const val NOTIFICATION_TONE_VIBRATION = "default_openhab_alertringtone_vibration"

    const val DEV_ID = "sendDeviceInfoPrefix"
    const val DEV_ID_PREFIX_VOICE = "device_identifier_prefix_voice"
    const val DEV_ID_PREFIX_BG_TASKS = "device_identifier_prefix_background_tasks"
    const val SCREEN_LOCK = "screen_lock"
    const val TASKER_PLUGIN_ENABLED = "taskerPlugin"
    const val DEVICE_CONTROL_SUBTITLE = "device_control_subtitle"
    const val DEVICE_CONTROL_AUTH_REQUIRED = "device_control_auth_required"
    const val DATA_SAVER = "data_saver"
    const val CRASH_REPORTING = "crash_reporting"
    const val DEBUG_MESSAGES = "default_openhab_debug_messages"
    const val LOG = "default_openhab_log"

    const val DRAWER_ENTRY_OH3_UI = "show_oh3_ui"
    const val DRAWER_ENTRY_HABPANEL = "show_habpanel"
    const val DRAWER_ENTRY_NFC = "show_nfc"
    const val DRAWER_ENTRY_FRONTAIL = "show_frontail"

    /**
     * Application state flags
     */
    const val COMPARABLE_VERSION = "versionAsInt"
    const val FIRST_START = "firstStart"
    const val RECENTLY_RESTORED = "recentlyRestored"
    const val ALARM_CLOCK_LAST_VALUE_WAS_UNDEF = "alarmClockLastWasZero"
    const val PREV_SERVER_FLAGS = "prevServerFlags"

    /**
     * "Tooltip shown" flags
     */
    const val SWIPE_REFRESH_EXPLAINED = "swipToRefreshExplained"
    const val DATA_SAVER_EXPLAINED = "dataSaverExplained"
    const val NFC_INFO_HINT_SHOWN = "nfcInfoHintShown"

    /**
     * PreferencesActivity subpages
     */
    const val SUBSCREEN_SEND_DEVICE_INFO = "send_device_info"
    const val SUBSCREEN_TILE = "tiles"
    const val SUBSCREEN_DEVICE_CONTROL = "device_control"

    /**
     * FOSS only
     */
    const val FOSS_LAST_SEEN_MESSAGE = "foss_last_seen_message"
    const val FOSS_NOTIFICATIONS_ENABLED = "foss_notifications_enabled"
}
