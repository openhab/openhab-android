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

package org.openhab.habdroid.ui.intro

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.util.HttpClient

/**
 * Checks whether an openHAB server can be reached at a given URL and whether it requires authentication.
 */
class ServerProber(private val connectionFactory: ConnectionFactory) {
    sealed class Result {
        abstract val url: String

        data class Reachable(override val url: String, val version: String?) : Result()

        data class AuthRequired(override val url: String, val version: String?) : Result()

        data class NotOpenHab(override val url: String) : Result()

        data class Failed(override val url: String, val statusCode: Int, val error: Throwable?) : Result()
    }

    suspend fun probe(url: String, username: String?, password: String?): Result {
        val client = connectionFactory.createProbeHttpClient(url, username, password)

        val version = try {
            val response = client.get("rest/", timeoutMillis = PROBE_READ_TIMEOUT_MS).asText().response
            parseOpenHabVersion(response) ?: return Result.NotOpenHab(url)
        } catch (e: HttpClient.HttpException) {
            Log.d(TAG, "Probing $url failed with status ${e.statusCode}", e)
            return if (e.statusCode.isAuthError()) {
                Result.AuthRequired(url, null)
            } else {
                Result.Failed(url, e.statusCode, e.cause ?: e)
            }
        }

        // The REST root is accessible without authentication, so check an endpoint that requires a user
        return try {
            client.get("rest/sitemaps", timeoutMillis = PROBE_READ_TIMEOUT_MS).close()
            Result.Reachable(url, version.ifEmpty { null })
        } catch (e: HttpClient.HttpException) {
            if (e.statusCode.isAuthError()) {
                Result.AuthRequired(url, version.ifEmpty { null })
            } else {
                Result.Reachable(url, version.ifEmpty { null })
            }
        }
    }

    private fun Int.isAuthError() = this == 401 || this == 403

    companion object {
        private val TAG = ServerProber::class.java.simpleName
        private const val PROBE_READ_TIMEOUT_MS = 5000L

        /**
         * @return the openHAB version, an empty string if the version is unknown
         * or null if the response doesn't come from openHAB
         */
        fun parseOpenHabVersion(response: String): String? = try {
            val json = JSONObject(response)
            if (json.has("version") || json.has("links")) {
                json.optJSONObject("runtimeInfo")?.optString("version").orEmpty()
            } else {
                null
            }
        } catch (e: JSONException) {
            null
        }
    }
}
