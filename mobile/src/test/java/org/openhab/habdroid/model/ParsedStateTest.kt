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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsedStateTest {
    @Test
    fun testParseAsBoolean() {
        listOf("ON", "100", "1", "0.1").forEach {
            assertTrue("State $it should be parsed as true", ParsedState.parseAsBoolean(it))
        }
        listOf("OFF", "0").forEach {
            assertFalse("State $it should be parsed as false", ParsedState.parseAsBoolean(it))
        }
    }

    @Test
    fun testParseAsNumber() {
        mapOf(
            "ON" to 100f,
            "OFF" to 0f,
            "3" to 3f,
            "0" to 0f,
            "42.42" to 42.42f
        ).forEach {
            val expected = ParsedState.NumberState(it.value)
            val actual = ParsedState.parseAsNumber(it.key, null)
            assertEquals("${it.key} should be parsed as ${it.value}", expected, actual)
        }
    }

    @Test
    fun testParseAsBrightness() {
        mapOf(
            "100" to 100,
            "0" to 0,
            "0.1" to 1,
            "10,20,30" to 30
        ).forEach {
            assertEquals("${it.key} should be parsed as ${it.value}", it.value, ParsedState.parseAsBrightness(it.key))
        }
    }

    @Test
    fun testEquality() {
        val numberOne = ParsedState.NumberState(100f)
        val numberTwo = ParsedState.NumberState(100f)
        assertEquals(numberOne, numberTwo)

        val hsvOne = HsvState(100f, 100f, 100f)
        val hsvTwo = HsvState(100f, 100f, 100f)
        assertEquals(hsvOne, hsvTwo)
    }
}
