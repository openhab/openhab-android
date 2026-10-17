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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openhab.habdroid.model.ServerPath

class WearConfigTest {
    @Test
    fun testJsonRoundTrip() {
        val config = WearConfig(
            localPath = ServerPath("http://openhab.local:8080/", "user", "secret"),
            remotePath = ServerPath("https://myopenhab.org/", "mail@example.com", null),
            sitemapName = "watch",
            complicationGroup = "gWatch",
            trustedCertificates = listOf("Y2VydDE=", "Y2VydDI=")
        )
        assertEquals(config, WearConfig.fromJson(config.toJson()))
    }

    @Test
    fun testJsonRoundTripWithoutOptionalValues() {
        val config = WearConfig(
            localPath = null,
            remotePath = ServerPath("https://myopenhab.org/", null, null),
            sitemapName = null,
            complicationGroup = null,
            trustedCertificates = emptyList()
        )
        assertEquals(config, WearConfig.fromJson(config.toJson()))
    }

    @Test
    fun testInvalidJson() {
        assertNull(WearConfig.fromJson("{"))
    }

    @Test
    fun testToStringDoesNotContainCredentials() {
        val config = WearConfig(
            localPath = ServerPath("http://openhab.local:8080/", "user", "secret"),
            remotePath = null,
            sitemapName = null,
            complicationGroup = null,
            trustedCertificates = emptyList()
        )
        assert(!config.toString().contains("secret"))
    }
}
