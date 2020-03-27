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
import org.openhab.habdroid.util.orDefaultIfEmpty
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
            val url = prefs.getString(PrefKeys.buildServerKey(serverId, urlKeyPrefix), null).toNormalizedUrl()
                ?: return null
            return ServerPath(
                url,
                secretPrefs.getString(PrefKeys.buildServerKey(serverId, userNamePrefix), null),
                secretPrefs.getString(PrefKeys.buildServerKey(serverId, passwordPrefix), null)
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
    val sslClientCert: String?
) : Parcelable {
    fun saveToPrefs(prefs: SharedPreferences, secretPrefs: SharedPreferences) {
        prefs.edit {
            putString(PrefKeys.buildServerKey(id, PrefKeys.SERVER_NAME_PREFIX), name)
            putString(PrefKeys.buildServerKey(id, PrefKeys.LOCAL_URL_PREFIX), localPath?.url)
            putString(PrefKeys.buildServerKey(id, PrefKeys.REMOTE_URL_PREFIX), remotePath?.url)
            putString(PrefKeys.buildServerKey(id, PrefKeys.SSL_CLIENT_CERT_PREFIX), sslClientCert)
        }
        secretPrefs.edit {
            putString(PrefKeys.buildServerKey(id, PrefKeys.LOCAL_USERNAME_PREFIX), localPath?.userName)
            putString(PrefKeys.buildServerKey(id, PrefKeys.LOCAL_PASSWORD_PREFIX), localPath?.password)
            putString(PrefKeys.buildServerKey(id, PrefKeys.REMOTE_USERNAME_PREFIX), remotePath?.userName)
            putString(PrefKeys.buildServerKey(id, PrefKeys.REMOTE_PASSWORD_PREFIX), remotePath?.password)
        }
    }
    fun removeFromPrefs(prefs: SharedPreferences, secretPrefs: SharedPreferences) {
        prefs.edit {
            remove(PrefKeys.buildServerKey(id, PrefKeys.SERVER_NAME_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.LOCAL_URL_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.REMOTE_URL_PREFIX))
            remove(PrefKeys.buildServerKey(id, PrefKeys.SSL_CLIENT_CERT_PREFIX))
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
            val serverName = prefs.getString(PrefKeys.buildServerKey(id, PrefKeys.SERVER_NAME_PREFIX), null)
            if ((localPath == null && remotePath == null) || serverName.isNullOrEmpty()) {
                return null
            }
            val clientCert = prefs.getString(PrefKeys.buildServerKey(id, PrefKeys.SSL_CLIENT_CERT_PREFIX), null)
            return ServerConfiguration(id, serverName, localPath, remotePath, clientCert)
        }
    }
}
