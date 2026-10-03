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

package org.openhab.habdroid.util

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Builds the list of URLs that should be tried when the user enters a server address.
 *
 * - If the input contains a scheme, only that exact URL is used.
 * - If the input contains a port, https and http are tried on that port.
 * - Otherwise https is tried on 443 and 8443 first, then http on 8080 and 80.
 */
object ServerUrlCandidates {
    private val SCHEME_REGEX = "^[a-zA-Z][a-zA-Z0-9+.-]*://".toRegex()
    private val AUTHORITY_REGEX = "^(\\[[^\\]]+\\]|[^:/?#\\[\\]]+)(:(\\d{1,5}))?([/?#].*)?$".toRegex()

    private val DEFAULT_PORTS = listOf(
        "https" to 443,
        "https" to 8443,
        "http" to 8080,
        "http" to 80
    )

    fun fromUserInput(input: String): List<String> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return emptyList()
        }

        if (SCHEME_REGEX.containsMatchIn(trimmed)) {
            val url = trimmed.toHttpUrlOrNull() ?: return emptyList()
            return listOf(url.withTrailingSlash())
        }

        val match = AUTHORITY_REGEX.matchEntire(trimmed) ?: return emptyList()
        val host = match.groupValues[1]
        val explicitPort = match.groupValues[3].toIntOrNull()
        val pathAndQuery = match.groupValues[4]

        val schemesAndPorts = if (explicitPort != null) {
            listOf("https" to explicitPort, "http" to explicitPort)
        } else {
            DEFAULT_PORTS
        }

        return schemesAndPorts
            .mapNotNull { (scheme, port) -> "$scheme://$host:$port$pathAndQuery".toHttpUrlOrNull() }
            .map { it.withTrailingSlash() }
            .distinct()
    }

    /**
     * Candidates for a server found via mDNS. Prefer https, if the server announced it.
     */
    fun fromDiscovery(host: String, httpsPort: Int?, httpPort: Int?): List<String> {
        val hostPart = if (host.contains(':')) "[$host]" else host
        return listOfNotNull(
            httpsPort?.let { "https://$hostPart:$it/" },
            httpPort?.let { "http://$hostPart:$it/" }
        ).mapNotNull { it.toHttpUrlOrNull()?.withTrailingSlash() }
    }

    private fun HttpUrl.withTrailingSlash(): String {
        val builder = newBuilder().query(null).fragment(null)
        if (!encodedPath.endsWith("/")) {
            builder.addPathSegment("")
        }
        return builder.build().toString()
    }
}
