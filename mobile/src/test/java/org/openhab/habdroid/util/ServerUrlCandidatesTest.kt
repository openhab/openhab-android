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

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerUrlCandidatesTest {
    @Test
    fun testEmptyInput() {
        assertEquals(emptyList<String>(), ServerUrlCandidates.fromUserInput(""))
        assertEquals(emptyList<String>(), ServerUrlCandidates.fromUserInput("   "))
    }

    @Test
    fun testHostWithoutSchemeAndPort() {
        assertEquals(
            listOf(
                "https://openhab.local/",
                "https://openhab.local:8443/",
                "http://openhab.local:8080/",
                "http://openhab.local/"
            ),
            ServerUrlCandidates.fromUserInput(" openhab.local ")
        )
    }

    @Test
    fun testIpWithPort() {
        assertEquals(
            listOf("https://192.168.1.10:8080/", "http://192.168.1.10:8080/"),
            ServerUrlCandidates.fromUserInput("192.168.1.10:8080")
        )
    }

    @Test
    fun testIpv6() {
        assertEquals(
            listOf("https://[fe80::1]:8443/", "http://[fe80::1]:8443/"),
            ServerUrlCandidates.fromUserInput("[fe80::1]:8443")
        )
    }

    @Test
    fun testWithPath() {
        assertEquals(
            listOf("https://example.com:9000/openhab/", "http://example.com:9000/openhab/"),
            ServerUrlCandidates.fromUserInput("example.com:9000/openhab")
        )
    }

    @Test
    fun testWithScheme() {
        assertEquals(listOf("http://openhab:8080/"), ServerUrlCandidates.fromUserInput("http://openhab:8080"))
        assertEquals(listOf("https://myopenhab.org/"), ServerUrlCandidates.fromUserInput("HTTPS://myopenhab.org/"))
    }

    @Test
    fun testInvalidInput() {
        assertEquals(emptyList<String>(), ServerUrlCandidates.fromUserInput("ftp://openhab"))
        assertEquals(emptyList<String>(), ServerUrlCandidates.fromUserInput("open hab"))
    }

    @Test
    fun testDiscovery() {
        assertEquals(
            listOf("https://192.168.1.10:8443/", "http://192.168.1.10:8080/"),
            ServerUrlCandidates.fromDiscovery("192.168.1.10", 8443, 8080)
        )
        assertEquals(listOf("http://[fe80::1]:8080/"), ServerUrlCandidates.fromDiscovery("fe80::1", null, 8080))
    }
}
