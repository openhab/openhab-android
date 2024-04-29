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

import org.junit.Assert.assertEquals
import org.junit.Test

class IconResourceTest {
    @Test
    fun testOhIcons() {
        mapOf(
            "light" to "icon/light?format=PNG&anyFormat=true&iconset=classic",
            "oh:light" to "icon/light?format=PNG&anyFormat=true&iconset=classic",
            "oh:classic:light" to "icon/light?format=PNG&anyFormat=true&iconset=classic",
            "oh:custom:light" to "icon/light?format=PNG&anyFormat=true&iconset=custom"
        ).forEach {
            testIconToUrl(it.key, it.value)
        }
    }

    @Test
    fun testMaterialIcons() {
        mapOf(
            "material:light" to "https://api.iconify.design/ic/baseline-light.svg?height=64",
            "material:outline:light" to "https://api.iconify.design/ic/outline-light.svg?height=64"
        ).forEach {
            testIconToUrl(it.key, it.value)
        }
    }

    @Test
    fun testF7Icons() {
        mapOf(
            "f7:airplane" to "https://api.iconify.design/f7/airplane.svg?height=64",
            "f7:IGNORED:airplane" to "https://api.iconify.design/f7/airplane.svg?height=64"
        ).forEach {
            testIconToUrl(it.key, it.value)
        }
    }

    @Test
    fun testIconifyIcons() {
        mapOf(
            "if:codicon:lightbulb" to "https://api.iconify.design/codicon/lightbulb.svg?height=64",
            "iconify:codicon:lightbulb" to "https://api.iconify.design/codicon/lightbulb.svg?height=64"
        ).forEach {
            testIconToUrl(it.key, it.value)
        }
    }

    @Test
    fun testUnknownIconSources() {
        listOf(
            "unknown:ignored",
            "unknown:ignored:ignored"
        ).forEach {
            val urlForUnknownIcons = "icon/none?format=PNG&anyFormat=true&iconset=classic"
            testIconToUrl(it, urlForUnknownIcons)
        }
    }

    @Test
    fun testIsNoneIcon() {
        mapOf(
            "none" to true,
            "oh:none" to true,
            "oh:classic:none" to true,
            "oh:foo:none" to true,
            "f7:none" to false,
            "lights" to false
        ).forEach {
            assertEquals("${it.key} failed", it.key.isNoneIcon(), it.value)
        }
    }

    private fun testIconToUrl(icon: String, url: String) {
        assertEquals(
            "$icon icon failed!",
            url,
            IconResource(icon, true, "").toUrl(false, IconFormat.Png, 64)
        )
    }
}
