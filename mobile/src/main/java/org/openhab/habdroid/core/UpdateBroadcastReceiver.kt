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

package org.openhab.habdroid.core

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import org.openhab.habdroid.BuildConfig
import org.openhab.habdroid.R
import org.openhab.habdroid.model.putIconResource
import org.openhab.habdroid.model.toOH2IconResource
import org.openhab.habdroid.ui.PreferencesActivity
import org.openhab.habdroid.ui.homescreenwidget.ItemUpdateWidget
import org.openhab.habdroid.util.Constants
import org.openhab.habdroid.util.Constants.PREFERENCE_COMPARABLE_VERSION
import org.openhab.habdroid.util.getDayNightMode
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getSecretPrefs
import org.openhab.habdroid.util.getString

class UpdateBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val prefs = context.getPrefs()
        prefs.edit {
            if (prefs.getInt(PREFERENCE_COMPARABLE_VERSION, 0) <= UPDATE_LOCAL_CREDENTIALS) {
                Log.d(TAG, "Checking for putting local username/password to remote username/password.")
                if (prefs.getString(Constants.PREFERENCE_REMOTE_USERNAME, null) == null) {
                    putString(Constants.PREFERENCE_REMOTE_USERNAME,
                        prefs.getString(Constants.PREFERENCE_LOCAL_USERNAME, null))
                }
                if (prefs.getString(Constants.PREFERENCE_REMOTE_PASSWORD, null) == null) {
                    putString(Constants.PREFERENCE_REMOTE_PASSWORD,
                        prefs.getString(Constants.PREFERENCE_LOCAL_PASSWORD, null))
                }
            }
            if (prefs.getInt(PREFERENCE_COMPARABLE_VERSION, 0) <= SECURE_CREDENTIALS) {
                Log.d(TAG, "Put username/password to encrypted prefs.")
                context.getSecretPrefs().edit {
                    putString(Constants.PREFERENCE_LOCAL_USERNAME,
                        prefs.getString(Constants.PREFERENCE_LOCAL_USERNAME, null))
                    putString(Constants.PREFERENCE_LOCAL_PASSWORD,
                        prefs.getString(Constants.PREFERENCE_LOCAL_PASSWORD, null))
                    putString(Constants.PREFERENCE_REMOTE_USERNAME,
                        prefs.getString(Constants.PREFERENCE_REMOTE_USERNAME, null))
                    putString(Constants.PREFERENCE_REMOTE_PASSWORD,
                        prefs.getString(Constants.PREFERENCE_REMOTE_PASSWORD, null))
                }
                // Clear from unencrypted prefs
                remove(Constants.PREFERENCE_LOCAL_USERNAME)
                remove(Constants.PREFERENCE_LOCAL_PASSWORD)
                remove(Constants.PREFERENCE_REMOTE_USERNAME)
                remove(Constants.PREFERENCE_REMOTE_PASSWORD)
            }
            if (prefs.getInt(PREFERENCE_COMPARABLE_VERSION, 0) <= DARK_MODE) {
                Log.d(TAG, "Migrate to day/night themes")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    putString(Constants.PREFERENCE_THEME, context.getString(R.string.theme_value_system))
                } else {
                    val newTheme = when (prefs.getString("default_openhab_theme")) {
                        "black", "basicuidark", "dark" -> context.getString(R.string.theme_value_dark)
                        else -> context.getString(R.string.theme_value_system)
                    }

                    putString(Constants.PREFERENCE_THEME, newTheme)
                }

                AppCompatDelegate.setDefaultNightMode(prefs.getDayNightMode(context))

                val accentColor = when (prefs.getString("default_openhab_theme")) {
                    "basicui", "basicuidark" -> ContextCompat.getColor(context, R.color.indigo_500)
                    "black", "dark" -> ContextCompat.getColor(context, R.color.blue_grey_700)
                    else -> ContextCompat.getColor(context, R.color.openhab_orange)
                }

                putInt(Constants.PREFERENCE_ACCENT_COLOR, accentColor)
            }
            if (prefs.getInt(PREFERENCE_COMPARABLE_VERSION, 0) <= WIDGET_ICON) {
                val widgetComponent = ComponentName(context, ItemUpdateWidget::class.java)
                AppWidgetManager.getInstance(context).getAppWidgetIds(widgetComponent).forEach { id ->
                    val widgetPrefs = ItemUpdateWidget.getPrefsForWidget(context, id)
                    val icon = widgetPrefs.getString(PreferencesActivity.ITEM_UPDATE_WIDGET_ICON)
                    widgetPrefs.edit {
                        putIconResource(PreferencesActivity.ITEM_UPDATE_WIDGET_ICON, icon.toOH2IconResource())
                    }
                }
            }

            updateComparableVersion(this)
        }
    }

    companion object {
        private val TAG = UpdateBroadcastReceiver::class.java.simpleName

        private const val UPDATE_LOCAL_CREDENTIALS = 26
        private const val SECURE_CREDENTIALS = 190
        private const val DARK_MODE = 200
        private const val WIDGET_ICON = 237

        fun updateComparableVersion(editor: SharedPreferences.Editor) {
            editor.putInt(PREFERENCE_COMPARABLE_VERSION, BuildConfig.VERSION_CODE).apply()
        }
    }
}
