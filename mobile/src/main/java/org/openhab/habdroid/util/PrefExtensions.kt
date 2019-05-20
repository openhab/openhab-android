package org.openhab.habdroid.util

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.net.toUri
import org.openhab.habdroid.R
import org.openhab.habdroid.model.Sitemap

inline fun SharedPreferences.getLocalUrl(): String {
    return getString(Constants.PREFERENCE_LOCAL_URL)
}

inline fun SharedPreferences.getRemoteUrl(): String {
    return getString(Constants.PREFERENCE_REMOTE_URL)
}

inline fun SharedPreferences.getDefaultSitemap(): String {
    return getString(Constants.PREFERENCE_SITEMAP_NAME)
}

inline fun SharedPreferences.isDemoModeEnabled(): Boolean {
    return getBoolean(Constants.PREFERENCE_DEMOMODE, false)
}

inline fun SharedPreferences.isDebugModeEnabled(): Boolean {
    return getBoolean(Constants.PREFERENCE_DEBUG_MESSAGES, false)
}

inline fun SharedPreferences.getNotificationTone(): Uri? {
    return getString(Constants.PREFERENCE_TONE, null)?.toUri()
}

inline fun SharedPreferences.isScreenTimerDisabled(): Boolean {
    return getBoolean(Constants.PREFERENCE_SCREENTIMEROFF, false)
}

inline fun SharedPreferences.getChartScalingFactor(): Float {
    return getFloat(Constants.PREFERENCE_CHART_SCALING, 1.0F)
}

inline fun SharedPreferences.shouldRequestHighResChart(): Boolean {
    return getBoolean(Constants.PREFERENCE_CHART_HQ, true)
}

inline fun SharedPreferences.getString(key: String): String {
    return getString(key, "") as String
}

/**
 * Returns vibration pattern for notifications that can be passed to
 * [}][androidx.core.app.NotificationCompat.Builder.setVibrate]
 */
inline fun SharedPreferences.getNotificationVibrationPattern(context: Context): LongArray {
    return when (getString(Constants.PREFERENCE_NOTIFICATION_VIBRATION)) {
        context.getString(R.string.settings_notification_vibration_value_short) -> longArrayOf(0, 500, 500)
        context.getString(R.string.settings_notification_vibration_value_long) -> longArrayOf(0, 1000, 1000)
        context.getString(R.string.settings_notification_vibration_value_twice) -> longArrayOf(0, 1000, 1000, 1000, 1000)
        else -> longArrayOf(0)
    }
}

inline fun SharedPreferences.Editor.updateDefaultSitemap(sitemap: Sitemap?) {
    if (sitemap == null) {
        remove(Constants.PREFERENCE_SITEMAP_NAME)
        remove(Constants.PREFERENCE_SITEMAP_LABEL)
    } else {
        putString(Constants.PREFERENCE_SITEMAP_NAME, sitemap.name)
        putString(Constants.PREFERENCE_SITEMAP_LABEL, sitemap.label)
    }
}