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
import kotlinx.parcelize.Parcelize
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getStringOrNull
import org.openhab.habdroid.util.toNormalizedUrl

@Parcelize
data class ServerPath(val url: String, val userName: String?, val password: String?) : Parcelable {
    // If the user name is longer than 50 chars, assume it's an API token and therefore no password is required.
    fun hasAuthentication() = !userName.isNullOrEmpty() && (!password.isNullOrEmpty() || userName.length > 50)

    companion object {
        fun load(
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
