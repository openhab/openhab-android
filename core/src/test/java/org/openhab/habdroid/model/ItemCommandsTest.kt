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

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemCommandsTest {
    private val temperatureItem = JSONObject(
        """{ "name": "Setpoint", "type": "Number:Temperature", "state": "20.5 °C", "link": "http://foo/Setpoint" }"""
    ).toItem()

    private val numberItem = JSONObject(
        """{ "name": "Number", "type": "Number", "state": "20.5", "link": "http://foo/Number" }"""
    ).toItem()

    @Test
    fun testCommandDoesNotUseStateFormat() {
        val state = ParsedState.NumberState(20.5F, "°C", "%.0f %unit%")
        assertEquals("20.5 °C", state.toItemCommand(temperatureItem))
    }

    @Test
    fun testCommandWithoutUnit() {
        assertEquals("20.5", ParsedState.NumberState(20.5F, null, "%.0f").toItemCommand(numberItem))
    }

    @Test
    fun testCommandWithoutStateOrItem() {
        assertEquals(null, (null as ParsedState.NumberState?).toItemCommand(numberItem))
        assertEquals(null, ParsedState.NumberState(20.5F).toItemCommand(null))
    }
}
