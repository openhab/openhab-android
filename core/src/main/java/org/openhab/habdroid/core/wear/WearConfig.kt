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

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.model.ServerPath
import org.openhab.habdroid.util.mapString
import org.openhab.habdroid.util.optStringOrNull

/**
 * Configuration the phone app sends to the Wear OS app via the Wearable Data Layer.
 *
 * @param sitemapName Name of the sitemap to show on the watch, null to use the first sitemap of the server
 * @param complicationGroup Name of a group item. If set, only its members are offered as complication data source.
 * @param trustedCertificates Base64 encoded DER certificates the user trusted on the phone
 */
data class WearConfig(
    val localPath: ServerPath?,
    val remotePath: ServerPath?,
    val sitemapName: String?,
    val complicationGroup: String?,
    val trustedCertificates: List<String>
) {
    val hasConnection get() = localPath != null || remotePath != null

    fun toJson(): String = JSONObject()
        .put(KEY_LOCAL, localPath?.toJson())
        .put(KEY_REMOTE, remotePath?.toJson())
        .put(KEY_SITEMAP, sitemapName)
        .put(KEY_COMPLICATION_GROUP, complicationGroup)
        .put(KEY_CERTIFICATES, JSONArray(trustedCertificates))
        .toString()

    override fun toString() = "WearConfig(local=${localPath?.url}, remote=${remotePath?.url}, " +
        "sitemap=$sitemapName, complicationGroup=$complicationGroup, certificates=${trustedCertificates.size})"

    companion object {
        /**
         * Path of the data item that holds the config
         */
        const val DATA_PATH = "/openhab/config"

        /**
         * Key of the JSON encoded config in the data map
         */
        const val DATA_KEY_CONFIG = "config"

        /**
         * Path of the message the watch sends to ask the phone to publish the config again
         */
        const val MESSAGE_PATH_REQUEST_CONFIG = "/openhab/request_config"

        /**
         * Path of the message the watch sends to open the server settings on the phone
         */
        const val MESSAGE_PATH_OPEN_SETTINGS = "/openhab/open_settings"

        /**
         * Capability the phone app announces, see res/values/wear.xml
         */
        const val CAPABILITY_PHONE_APP = "openhab_phone_app"

        /**
         * Capability the Wear OS app announces, see res/values/wear.xml
         */
        const val CAPABILITY_WEAR_APP = "openhab_wear_app"

        private const val KEY_LOCAL = "local"
        private const val KEY_REMOTE = "remote"
        private const val KEY_SITEMAP = "sitemap"
        private const val KEY_COMPLICATION_GROUP = "complicationGroup"
        private const val KEY_CERTIFICATES = "certificates"
        private const val KEY_URL = "url"
        private const val KEY_USER = "user"
        private const val KEY_PASSWORD = "password"

        fun fromJson(json: String): WearConfig? = try {
            val obj = JSONObject(json)
            WearConfig(
                localPath = obj.optJSONObject(KEY_LOCAL)?.toServerPath(),
                remotePath = obj.optJSONObject(KEY_REMOTE)?.toServerPath(),
                sitemapName = obj.optStringOrNull(KEY_SITEMAP),
                complicationGroup = obj.optStringOrNull(KEY_COMPLICATION_GROUP),
                trustedCertificates = obj.optJSONArray(KEY_CERTIFICATES)?.mapString { cert -> cert }.orEmpty()
            )
        } catch (_: JSONException) {
            null
        }

        private fun ServerPath.toJson() = JSONObject()
            .put(KEY_URL, url)
            .put(KEY_USER, userName)
            .put(KEY_PASSWORD, password)

        private fun JSONObject.toServerPath() = ServerPath(
            getString(KEY_URL),
            optStringOrNull(KEY_USER),
            optStringOrNull(KEY_PASSWORD)
        )
    }
}
