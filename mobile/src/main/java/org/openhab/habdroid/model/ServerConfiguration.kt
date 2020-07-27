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

package org.openhab.habdroid.model

import android.content.SharedPreferences
import android.os.Parcelable
import androidx.core.content.edit
import kotlinx.android.parcel.Parcelize
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getActiveServerId
import org.openhab.habdroid.util.getConfiguredServerIds
import org.openhab.habdroid.util.getPrimaryServerId
import org.openhab.habdroid.util.getStringOrNull
import org.openhab.habdroid.util.putConfiguredServerIds
import org.openhab.habdroid.util.toNormalizedUrl

@Parcelize
data class ServerPath(
    val url: String,
    val userName: String?,
    val password: String?
) : Parcelable {
    fun hasAuthentication() = !userName.isNullOrEmpty() && !password.isNullOrEmpty()

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
    val defaultSitemap: DefaultSitemap?
) : Parcelable {
    fun saveToPrefs(prefs: SharedPreferences, secretPrefs: SharedPreferences) {
        val serverIdSet = prefs.getConfiguredServerIds()

        prefs.edit {
            putString(PrefKeys.buildServerKey(id, PrefKeys.SERVER_NAME_PREFIX), name)
            putString(PrefKeys.buildServerKey(id, PrefKeys.LOCAL_URL_PREFIX), localPath?.url)
            putString(PrefKeys.buildServerKey(id, PrefKeys.REMOTE_URL_PREFIX), remotePath?.url)
            putString(PrefKeys.buildServerKey(id, PrefKeys.SSL_CLIENT_CERT_PREFIX), sslClientCert)
            if (!serverIdSet.contains(id)) {
                serverIdSet.add(id)
                putConfiguredServerIds(serverIdSet)
                if (serverIdSet.size == 1) {
                    putInt(PrefKeys.ACTIVE_SERVER_ID, id)
                    putInt(PrefKeys.PRIMARY_SERVER_ID, id)
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
        val serverIdSet = prefs.getConfiguredServerIds()
        serverIdSet.remove(id)

        prefs.edit {
            remove(PrefKeys.buildServerKey(id, PrefKeys.SERVER_NAME_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.LOCAL_URL_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.REMOTE_URL_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.SSL_CLIENT_CERT_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.DEFAULT_SITEMAP_NAME_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.DEFAULT_SITEMAP_LABEL_PREFIX))
            putConfiguredServerIds(serverIdSet)
            if (prefs.getActiveServerId() == id) {
                putInt(PrefKeys.ACTIVE_SERVER_ID, if (serverIdSet.isNotEmpty()) serverIdSet.first() else 0)
            }
            if (prefs.getPrimaryServerId() == id) {
                putInt(PrefKeys.PRIMARY_SERVER_ID, if (serverIdSet.isNotEmpty()) serverIdSet.first() else 0)
            }
        }
        secretPrefs.edit {
            remove(PrefKeys.buildServerKey(id, PrefKeys.LOCAL_USERNAME_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.LOCAL_PASSWORD_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.REMOTE_USERNAME_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.REMOTE_PASSWORD_PREFIX))
        }
    }

    companion object {
        fun load(prefs: SharedPreferences, secretPrefs: SharedPreferences, id: Int): ServerConfiguration? {
            val localPath = ServerPath.load(prefs, secretPrefs, id,
                PrefKeys.LOCAL_URL_PREFIX, PrefKeys.LOCAL_USERNAME_PREFIX, PrefKeys.LOCAL_PASSWORD_PREFIX)
            val remotePath = ServerPath.load(prefs, secretPrefs, id,
                PrefKeys.REMOTE_URL_PREFIX, PrefKeys.REMOTE_USERNAME_PREFIX, PrefKeys.REMOTE_PASSWORD_PREFIX)
            val serverName = prefs.getStringOrNull(PrefKeys.buildServerKey(id, PrefKeys.SERVER_NAME_PREFIX))
            if ((localPath == null && remotePath == null) || serverName.isNullOrEmpty()) {
                return null
            }
            val clientCert = prefs.getStringOrNull(PrefKeys.buildServerKey(id, PrefKeys.SSL_CLIENT_CERT_PREFIX))
            return ServerConfiguration(id, serverName, localPath, remotePath, clientCert, getDefaultSitemap(prefs, id))
        }

        fun saveDefaultSitemap(prefs: SharedPreferences, id: Int, defaultSitemap: DefaultSitemap?) {
            prefs.edit {
                putString(PrefKeys.buildServerKey(id, PrefKeys.DEFAULT_SITEMAP_NAME_PREFIX), defaultSitemap?.name)
                putString(PrefKeys.buildServerKey(id, PrefKeys.DEFAULT_SITEMAP_LABEL_PREFIX), defaultSitemap?.label)
            }
        }

        fun getDefaultSitemap(prefs: SharedPreferences, id: Int): DefaultSitemap? {
            val defaultSitemapName = prefs.getStringOrNull(PrefKeys.buildServerKey(id, PrefKeys.DEFAULT_SITEMAP_NAME_PREFIX))
            val defaultSitemapLabel = prefs.getStringOrNull(PrefKeys.buildServerKey(id, PrefKeys.DEFAULT_SITEMAP_LABEL_PREFIX))
            return if (defaultSitemapName != null && defaultSitemapLabel != null) {
                DefaultSitemap(defaultSitemapName, defaultSitemapLabel)
            } else {
                null
            }
        }
    }
}

@Parcelize
data class DefaultSitemap(val name: String, val label: String) : Parcelable
