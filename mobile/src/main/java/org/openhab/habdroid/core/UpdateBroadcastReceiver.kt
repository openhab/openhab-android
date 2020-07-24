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
import org.openhab.habdroid.background.EventListenerService
import org.openhab.habdroid.background.tiles.AbstractTileService
import org.openhab.habdroid.model.DefaultSitemap
import org.openhab.habdroid.model.ServerConfiguration
import org.openhab.habdroid.model.ServerPath
import org.openhab.habdroid.model.putIconResource
import org.openhab.habdroid.model.toOH2IconResource
import org.openhab.habdroid.ui.PreferencesActivity
import org.openhab.habdroid.ui.homescreenwidget.ItemUpdateWidget
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getDayNightMode
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getSecretPrefs
import org.openhab.habdroid.util.getStringOrNull
import org.openhab.habdroid.util.putConfiguredServerIds

class UpdateBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val prefs = context.getPrefs()
        prefs.edit {
            if (prefs.getInt(PrefKeys.COMPARABLE_VERSION, 0) <= UPDATE_LOCAL_CREDENTIALS) {
                Log.d(TAG, "Checking for putting local username/password to remote username/password.")
                if (prefs.getStringOrNull("default_openhab_remote_username") == null) {
                    putString("default_openhab_remote_username", prefs.getStringOrNull("default_openhab_username"))
                }
                if (prefs.getStringOrNull("default_openhab_remote_password") == null) {
                    putString("default_openhab_remote_password", prefs.getStringOrNull("default_openhab_password"))
                }
            }
            if (prefs.getInt(PrefKeys.COMPARABLE_VERSION, 0) <= SECURE_CREDENTIALS) {
                Log.d(TAG, "Put username/password to encrypted prefs.")
                context.getSecretPrefs().edit {
                    putString("default_openhab_username", prefs.getStringOrNull("default_openhab_username"))
                    putString("default_openhab_password", prefs.getStringOrNull("default_openhab_password"))
                    putString("default_openhab_remote_username",
                        prefs.getStringOrNull("default_openhab_remote_username"))
                    putString("default_openhab_remote_password",
                        prefs.getStringOrNull("default_openhab_remote_password"))
                }
                // Clear from unencrypted prefs
                remove("default_openhab_username")
                remove("default_openhab_password")
                remove("default_openhab_remote_username")
                remove("default_openhab_remote_password")
            }
            if (prefs.getInt(PrefKeys.COMPARABLE_VERSION, 0) <= DARK_MODE) {
                Log.d(TAG, "Migrate to day/night themes")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    putString(PrefKeys.THEME, context.getString(R.string.theme_value_system))
                } else {
                    val newTheme = when (prefs.getStringOrNull("default_openhab_theme")) {
                        "black", "basicuidark", "dark" -> context.getString(R.string.theme_value_dark)
                        else -> context.getString(R.string.theme_value_system)
                    }

                    putString(PrefKeys.THEME, newTheme)
                }

                AppCompatDelegate.setDefaultNightMode(prefs.getDayNightMode(context))

                val accentColor = when (prefs.getStringOrNull("default_openhab_theme")) {
                    "basicui", "basicuidark" -> ContextCompat.getColor(context, R.color.indigo_500)
                    "black", "dark" -> ContextCompat.getColor(context, R.color.blue_grey_700)
                    else -> ContextCompat.getColor(context, R.color.openhab_orange)
                }

                putInt(PrefKeys.ACCENT_COLOR, accentColor)
            }
            if (prefs.getInt(PrefKeys.COMPARABLE_VERSION, 0) <= WIDGET_ICON) {
                Log.d(TAG, "Migrate widget icon prefs")
                val widgetComponent = ComponentName(context, ItemUpdateWidget::class.java)
                AppWidgetManager.getInstance(context).getAppWidgetIds(widgetComponent).forEach { id ->
                    val widgetPrefs = ItemUpdateWidget.getPrefsForWidget(context, id)
                    val icon = widgetPrefs.getStringOrNull(PreferencesActivity.ITEM_UPDATE_WIDGET_ICON)
                    widgetPrefs.edit {
                        putIconResource(PreferencesActivity.ITEM_UPDATE_WIDGET_ICON, icon.toOH2IconResource())
                    }
                }

                Log.d(TAG, "Update widgets")
                ItemUpdateWidget.updateAllWidgets(context)
            }
            if (prefs.getInt(PrefKeys.COMPARABLE_VERSION, 0) <= MULTI_SERVER_SUPPORT) {
                // if local or remote server URL are set, convert them to a server named 'openHAB'
                val localUrl = prefs.getStringOrNull("default_openhab_url")
                val remoteUrl = prefs.getStringOrNull("default_openhab_alturl")
                if (localUrl != null || remoteUrl != null) {
                    val secretPrefs = context.getSecretPrefs()
                    val localPath = localUrl?.let { url -> ServerPath(url,
                        secretPrefs.getStringOrNull("default_openhab_username"),
                        secretPrefs.getStringOrNull("default_openhab_password")
                    ) }
                    val remotePath = remoteUrl?.let { url -> ServerPath(url,
                        secretPrefs.getStringOrNull("default_openhab_remote_username"),
                        secretPrefs.getStringOrNull("default_openhab_remote_password")
                    ) }
                    val defaultSitemapName = prefs.getStringOrNull("default_openhab_sitemap")
                    val defaultSitemapLabel = prefs.getStringOrNull("default_openhab_sitemap_label")
                    val defaultSitemap = if (defaultSitemapName.isNullOrEmpty() || defaultSitemapLabel == null) {
                        null
                    } else {
                        DefaultSitemap(defaultSitemapName, defaultSitemapLabel)
                    }
                    val config = ServerConfiguration(1, "openHAB", localPath, remotePath,
                        prefs.getStringOrNull("default_openhab_sslclientcert"), defaultSitemap)
                    config.saveToPrefs(prefs, secretPrefs)
                    prefs.edit {
                        putConfiguredServerIds(setOf(config.id))
                        putInt(PrefKeys.ACTIVE_SERVER_ID, config.id)
                        putInt(PrefKeys.PRIMARY_SERVER_ID, config.id)
                        remove("default_openhab_url")
                        remove("default_openhab_alturl")
                        remove("default_openhab_sslclientcert")
                    }
                    secretPrefs.edit {
                        remove("default_openhab_username")
                        remove("default_openhab_password")
                        remove("default_openhab_remote_username")
                        remove("default_openhab_remote_password")
                    }
                }
            }

            updateComparableVersion(this)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            for (tileId in 1..AbstractTileService.TILE_COUNT) {
                AbstractTileService.updateTile(context, tileId)
            }
        }
        EventListenerService.startOrStopService(context)
    }

    companion object {
        private val TAG = UpdateBroadcastReceiver::class.java.simpleName

        private const val UPDATE_LOCAL_CREDENTIALS = 26
        private const val SECURE_CREDENTIALS = 190
        private const val DARK_MODE = 200
        private const val WIDGET_ICON = 250
        private const val MULTI_SERVER_SUPPORT = 274

        fun updateComparableVersion(editor: SharedPreferences.Editor) {
            editor.putInt(PrefKeys.COMPARABLE_VERSION, BuildConfig.VERSION_CODE).apply()
        }
    }
}
