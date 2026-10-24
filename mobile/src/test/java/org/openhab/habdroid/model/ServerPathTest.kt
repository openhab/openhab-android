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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerPathTest {
    @Test
    fun testIsApiToken() {
        assertTrue(ServerPath(URL, "oh.app.abcdef", null).isApiToken())
        assertTrue(ServerPath(URL, "oh.app.abcdef", "").isApiToken())
        assertTrue(ServerPath(URL, "a".repeat(51), null).isApiToken())
        assertFalse(ServerPath(URL, "oh.app.abcdef", "password").isApiToken())
        assertFalse(ServerPath(URL, "user", null).isApiToken())
        assertFalse(ServerPath(URL, null, null).isApiToken())
    }

    @Test
    fun testHasAuthentication() {
        assertTrue(ServerPath(URL, "user", "password").hasAuthentication())
        assertTrue(ServerPath(URL, "oh.app.abcdef", null).hasAuthentication())
        assertFalse(ServerPath(URL, "user", null).hasAuthentication())
        assertFalse(ServerPath(URL, null, "password").hasAuthentication())
        assertFalse(ServerPath(URL, null, null).hasAuthentication())
    }

    companion object {
        private const val URL = "https://openhab.example.com"
    }
}
