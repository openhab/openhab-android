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

package org.openhab.habdroid.model

import android.content.SharedPreferences
import android.os.Parcelable
import android.util.Log
import androidx.core.content.edit
import kotlinx.parcelize.Parcelize
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getActiveServerId
import org.openhab.habdroid.util.getConfiguredServerIds
import org.openhab.habdroid.util.getPrimaryServerId
import org.openhab.habdroid.util.getStringOrNull
import org.openhab.habdroid.util.putActiveServerId
import org.openhab.habdroid.util.putConfiguredServerIds
import org.openhab.habdroid.util.putPrimaryServerId
import org.openhab.habdroid.util.toNormalizedUrl

@Parcelize
data class ServerPath(val url: String, val userName: String?, val password: String?) : Parcelable {
    // If the user name is longer than 50 chars, assume it's an API token and therefore no password is required.
    fun hasAuthentication() = !userName.isNullOrEmpty() && (!password.isNullOrEmpty() || userName.length > 50)

    companion object {
        internal fun load(
            prefs: SharedPreferences,
            secretPrefs: SharedPreferences,
            serverId: Int,
            urlKeyPrefix: String,
            userNamePrefix: String,
            passwordPrefix: String
        ): ServerPath? {
            val url = prefs.getStringOrNull(PrefKeys.buildServerKey(serverId, urlKeyPrefix)).toNormalizedUrl()
                ?: return null
            return ServerPath(
                url,
                secretPrefs.getStringOrNull(PrefKeys.buildServerKey(serverId, userNamePrefix)),
                secretPrefs.getStringOrNull(PrefKeys.buildServerKey(serverId, passwordPrefix))
            )
        }
    }
}

@Parcelize
data class ServerConfiguration(
    val id: Int,
    val name: String,
    val localPath: ServerPath?,
    val remotePath: ServerPath?,
    val sslClientCert: String?,
    val defaultSitemap: DefaultSitemap?,
    val wifiSsids: Set<String>?,
    val restrictToWifiSsids: Boolean,
    val frontailUrl: String?,
    val mainUiStartPage: String?
) : Parcelable {
    fun saveToPrefs(prefs: SharedPreferences, secretPrefs: SharedPreferences) {
        Log.d(TAG, "saveToPrefs: ${this.toRedactedString()}")
        val serverIdSet = prefs.getConfiguredServerIds()

        prefs.edit {
            putString(PrefKeys.buildServerKey(id, PrefKeys.SERVER_NAME_PREFIX), name)
            putString(PrefKeys.buildServerKey(id, PrefKeys.LOCAL_URL_PREFIX), localPath?.url)
            putString(PrefKeys.buildServerKey(id, PrefKeys.REMOTE_URL_PREFIX), remotePath?.url)
            putString(PrefKeys.buildServerKey(id, PrefKeys.FRONTAIL_URL_PREFIX), frontailUrl)
            putString(PrefKeys.buildServerKey(id, PrefKeys.MAIN_UI_START_PAGE_PREFIX), mainUiStartPage)
            putString(PrefKeys.buildServerKey(id, PrefKeys.SSL_CLIENT_CERT_PREFIX), sslClientCert)
            putStringSet(PrefKeys.buildServerKey(id, PrefKeys.WIFI_SSID_PREFIX), wifiSsids)
            putBoolean(PrefKeys.buildServerKey(id, PrefKeys.RESTRICT_TO_SSID_PREFIX), restrictToWifiSsids)
            if (!serverIdSet.contains(id)) {
                serverIdSet.add(id)
                putConfiguredServerIds(serverIdSet)
                if (serverIdSet.size == 1) {
                    putActiveServerId(id)
                    putPrimaryServerId(id)
                }
            }
        }
        saveDefaultSitemap(prefs, id, defaultSitemap)
        secretPrefs.edit {
            putString(PrefKeys.buildServerKey(id, PrefKeys.LOCAL_USERNAME_PREFIX), localPath?.userName)
            putString(PrefKeys.buildServerKey(id, PrefKeys.LOCAL_PASSWORD_PREFIX), localPath?.password)
            putString(PrefKeys.buildServerKey(id, PrefKeys.REMOTE_USERNAME_PREFIX), remotePath?.userName)
            putString(PrefKeys.buildServerKey(id, PrefKeys.REMOTE_PASSWORD_PREFIX), remotePath?.password)
        }
    }

    fun removeFromPrefs(prefs: SharedPreferences, secretPrefs: SharedPreferences) {
        Log.d(TAG, "removeFromPrefs: ${this.toRedactedString()}")
        val serverIdSet = prefs.getConfiguredServerIds()
        serverIdSet.remove(id)

        prefs.edit {
            remove(PrefKeys.buildServerKey(id, PrefKeys.SERVER_NAME_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.LOCAL_URL_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.REMOTE_URL_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.FRONTAIL_URL_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.MAIN_UI_START_PAGE_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.SSL_CLIENT_CERT_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.DEFAULT_SITEMAP_NAME_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.DEFAULT_SITEMAP_LABEL_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.WIFI_SSID_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.RESTRICT_TO_SSID_PREFIX))
            putConfiguredServerIds(serverIdSet)
            if (prefs.getActiveServerId() == id) {
                putActiveServerId(if (serverIdSet.isNotEmpty()) serverIdSet.first() else 0)
            }
            if (prefs.getPrimaryServerId() == id) {
                putPrimaryServerId(if (serverIdSet.isNotEmpty()) serverIdSet.first() else 0)
            }
        }
        secretPrefs.edit {
            remove(PrefKeys.buildServerKey(id, PrefKeys.LOCAL_USERNAME_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.LOCAL_PASSWORD_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.REMOTE_USERNAME_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.REMOTE_PASSWORD_PREFIX))
        }
    }

    fun toRedactedString(): String {
        fun redactCredentials(path: ServerPath?): ServerPath? {
            path ?: return null
            return ServerPath(
                path.url,
                if (path.userName.isNullOrEmpty()) "<none>" else "<redacted>",
                if (path.password.isNullOrEmpty()) "<none>" else "<redacted>"
            )
        }

        return createFrom(
            this,
            localPath = redactCredentials(localPath),
            remotePath = redactCredentials(remotePath)
        ).toString()
    }

    companion object {
        private val TAG = ServerConfiguration::class.java.simpleName

        fun load(prefs: SharedPreferences, secretPrefs: SharedPreferences, id: Int): ServerConfiguration? {
            val localPath = ServerPath.load(
                prefs,
                secretPrefs,
                id,
                PrefKeys.LOCAL_URL_PREFIX,
                PrefKeys.LOCAL_USERNAME_PREFIX,
                PrefKeys.LOCAL_PASSWORD_PREFIX
            )
            val remotePath = ServerPath.load(
                prefs,
                secretPrefs,
                id,
                PrefKeys.REMOTE_URL_PREFIX,
                PrefKeys.REMOTE_USERNAME_PREFIX,
                PrefKeys.REMOTE_PASSWORD_PREFIX
            )
            val serverName = prefs.getStringOrNull(PrefKeys.buildServerKey(id, PrefKeys.SERVER_NAME_PREFIX))
            if ((localPath == null && remotePath == null) || serverName.isNullOrEmpty()) {
                return null
            }
            val clientCert = prefs.getStringOrNull(PrefKeys.buildServerKey(id, PrefKeys.SSL_CLIENT_CERT_PREFIX))
            val wifiSsids = try {
                prefs.getStringSet(PrefKeys.buildServerKey(id, PrefKeys.WIFI_SSID_PREFIX), emptySet())
            } catch (e: ClassCastException) {
                setOf(prefs.getStringOrNull(PrefKeys.buildServerKey(id, PrefKeys.WIFI_SSID_PREFIX)))
            }
            val restrictToWifiSsids =
                prefs.getBoolean(PrefKeys.buildServerKey(id, PrefKeys.RESTRICT_TO_SSID_PREFIX), false)
            val frontailUrl = prefs.getStringOrNull(PrefKeys.buildServerKey(id, PrefKeys.FRONTAIL_URL_PREFIX))
            val mainUiStartPage = prefs.getStringOrNull(PrefKeys.buildServerKey(id, PrefKeys.MAIN_UI_START_PAGE_PREFIX))

            val config = ServerConfiguration(
                id,
                serverName,
                localPath,
                remotePath,
                clientCert,
                getDefaultSitemap(prefs, id),
                wifiSsids,
                restrictToWifiSsids,
                frontailUrl,
                mainUiStartPage
            )
            Log.d(TAG, "load: ${config.toRedactedString()}")
            return config
        }

        fun saveDefaultSitemap(prefs: SharedPreferences, id: Int, defaultSitemap: DefaultSitemap?) {
            prefs.edit {
                putString(PrefKeys.buildServerKey(id, PrefKeys.DEFAULT_SITEMAP_NAME_PREFIX), defaultSitemap?.name)
                putString(PrefKeys.buildServerKey(id, PrefKeys.DEFAULT_SITEMAP_LABEL_PREFIX), defaultSitemap?.label)
            }
        }

        fun getDefaultSitemap(prefs: SharedPreferences, id: Int): DefaultSitemap? {
            val defaultSitemapName =
                prefs.getStringOrNull(PrefKeys.buildServerKey(id, PrefKeys.DEFAULT_SITEMAP_NAME_PREFIX))
            val defaultSitemapLabel =
                prefs.getStringOrNull(PrefKeys.buildServerKey(id, PrefKeys.DEFAULT_SITEMAP_LABEL_PREFIX))
            return if (defaultSitemapName != null && defaultSitemapLabel != null) {
                DefaultSitemap(defaultSitemapName, defaultSitemapLabel)
            } else {
                null
            }
        }

        fun createFrom(
            config: ServerConfiguration,
            id: Int = config.id,
            name: String = config.name,
            localPath: ServerPath? = config.localPath,
            remotePath: ServerPath? = config.remotePath,
            sslClientCert: String? = config.sslClientCert,
            defaultSitemap: DefaultSitemap? = config.defaultSitemap,
            wifiSsids: Set<String>? = config.wifiSsids,
            restrictToWifiSsids: Boolean = config.restrictToWifiSsids,
            frontailUrl: String? = config.frontailUrl,
            mainUiStartPage: String? = config.mainUiStartPage
        ) = ServerConfiguration(
            id,
            name,
            localPath,
            remotePath,
            sslClientCert,
            defaultSitemap,
            wifiSsids,
            restrictToWifiSsids,
            frontailUrl,
            mainUiStartPage
        )
    }
}

@Parcelize
data class DefaultSitemap(val name: String, val label: String) : Parcelable

fun String.toWifiSsids(): Set<String> = split("\n")
    .map { ssid -> ssid.trim() }
    .filter { it.isNotEmpty() }
    .toSet()
