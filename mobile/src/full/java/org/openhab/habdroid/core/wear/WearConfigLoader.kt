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

package org.openhab.habdroid.core.wear

import android.content.Context
import android.util.Base64
import android.util.Log
import de.duenndns.ssl.MemorizingTrustManager
import java.security.cert.CertificateEncodingException
import org.openhab.habdroid.model.ServerConfiguration
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getPrimaryServerId
import org.openhab.habdroid.util.getSecretPrefs

private const val TAG = "WearConfigLoader"

/**
 * Keys of preferences that are part of the config sent to the watch
 */
private val WEAR_CONFIG_PREF_PREFIXES = listOf(
    PrefKeys.LOCAL_URL_PREFIX,
    PrefKeys.LOCAL_USERNAME_PREFIX,
    PrefKeys.LOCAL_PASSWORD_PREFIX,
    PrefKeys.REMOTE_URL_PREFIX,
    PrefKeys.REMOTE_USERNAME_PREFIX,
    PrefKeys.REMOTE_PASSWORD_PREFIX,
    PrefKeys.DEFAULT_SITEMAP_NAME_PREFIX,
    PrefKeys.WEAR_SITEMAP_NAME_PREFIX,
    PrefKeys.WEAR_COMPLICATION_GROUP_PREFIX
)

fun isWearConfigPref(key: String?): Boolean = key != null &&
    (key == PrefKeys.PRIMARY_SERVER_ID || WEAR_CONFIG_PREF_PREFIXES.any { prefix -> key.startsWith(prefix) })

/**
 * @return The config of the primary server for the watch, or null if no server is configured
 */
fun Context.loadWearConfig(): WearConfig? {
    val prefs = getPrefs()
    val config = ServerConfiguration.load(prefs, getSecretPrefs(), prefs.getPrimaryServerId()) ?: return null
    return WearConfig(
        localPath = config.localPath,
        remotePath = config.remotePath,
        sitemapName = config.wearSitemap?.name ?: config.defaultSitemap?.name,
        complicationGroup = config.wearComplicationGroup,
        trustedCertificates = loadTrustedCertificates()
    )
}

private fun Context.loadTrustedCertificates(): List<String> {
    val trustManager = MemorizingTrustManager(this)
    return trustManager.certificates.toList().mapNotNull { alias ->
        try {
            trustManager.getCertificate(alias)?.encoded?.let { encoded ->
                Base64.encodeToString(encoded, Base64.NO_WRAP)
            }
        } catch (e: CertificateEncodingException) {
            Log.w(TAG, "Unable to encode certificate $alias", e)
            null
        }
    }
}
