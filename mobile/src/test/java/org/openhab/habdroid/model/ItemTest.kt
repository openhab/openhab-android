/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemTest {
    @Test
    fun getStateAsBoolean_stateOff_returnFalse() {
        val sut = itemJsonForState("OFF").toItem()
        assertNotNull(sut.state)
        assertFalse(sut.state!!.asBoolean)
    }

    @Test
    fun getStateAsBoolean_stateON_returnTrue() {
        val sut = itemJsonForState("ON").toItem()
        assertNotNull(sut.state)
        assertTrue(sut.state!!.asBoolean)
    }

    @Test
    fun getStateAsBoolean_stateNegativeInteger_returnFalse() {
        val sut = itemJsonForState("-42").toItem()
        assertNotNull(sut.state)
        assertFalse(sut.state!!.asBoolean)
    }

    @Test
    fun getStateAsBoolean_statePositiveInteger_returnTrue() {
        val sut = itemJsonForState("42").toItem()
        assertNotNull(sut.state)
        assertTrue(sut.state!!.asBoolean)
    }

    @Test
    fun getStateAsBoolean_stateIsZero_returnFalse() {
        val sut = itemJsonForState("0").toItem()
        assertNotNull(sut.state)
        assertFalse(sut.state!!.asBoolean)
    }

    @Test
    fun getStateAsBoolean_stateHsbBrightnessZero_returnFalse() {
        val sut = itemJsonForState("10,10,0").toItem()
        assertNotNull(sut.state)
        assertFalse(sut.state!!.asBoolean)
    }

    @Test
    fun getStateAsBoolean_stateHsbBrightnessPositive_returnTrue() {
        val sut = itemJsonForState("10,10,50").toItem()
        assertNotNull(sut.state)
        assertTrue(sut.state!!.asBoolean)
    }

    @Throws(JSONException::class)
    private fun itemJsonForState(state: String?): JSONObject {
        val statePart = if (state != null) ", state: '$state'" else ""
        return JSONObject("{ 'name': 'foo', 'type': Dummy'$statePart }")
    }

    @Test
    fun isReadOnly() {
        val json = with(JSONObject()) {
            put("name", "TestItem")
            put("type", "Dummy")
        }
        assertFalse(json.toItem().readOnly)

        json.put("stateDescription", JSONObject().put("readOnly", true))
        assertTrue(json.toItem().readOnly)

        json.put("stateDescription", JSONObject().put("readOnly", false))
        assertFalse(json.toItem().readOnly)
    }

    @Test
    fun getMembers() {
        val sut = itemAsJsonObjectWithMembers.toItem()
        assertEquals(2, sut.members.size)
        assertEquals(Item.Type.Location, sut.members[0].type)
        assertEquals(Item.Type.Location, sut.members[1].type)
        assertEquals("Location 1", sut.members[0].label)
        assertEquals("Location 2", sut.members[1].label)
    }

    @Test
    fun getTags() {
        val sut = itemAsJsonObject.toItem()
        assertEquals(2, sut.tags.size)
        assertEquals(Item.Tag.Lighting, sut.tags[0])
        assertEquals(Item.Tag.Switchable, sut.tags[1])
    }

    @Test
    fun testEquals() {
        val sut1a = itemAsJsonObjectWithMembers.toItem()
        val sut1b = itemAsJsonObjectWithMembers.toItem()
        val sut2a = itemAsJsonObject.toItem()
        val sut2b = itemAsJsonObject.toItem()
        assertEquals(sut1a, sut1a)
        assertEquals(sut1a, sut1b)
        assertEquals(sut2a, sut2a)
        assertEquals(sut2a, sut2b)
        assertNotEquals(sut1a, null)
        assertNotEquals(sut1a, sut2a)
    }

    @Test
    fun testHashCode() {
        val sut1a = itemAsJsonObjectWithMembers.toItem()
        val sut1b = itemAsJsonObjectWithMembers.toItem()
        val sut2a = itemAsJsonObject.toItem()
        val sut2b = itemAsJsonObject.toItem()
        assertEquals(sut1a.hashCode(), sut1a.hashCode())
        assertEquals(sut1a.hashCode(), sut1b.hashCode())
        assertEquals(sut2a.hashCode(), sut2b.hashCode())
        assertNotEquals(sut1a.hashCode(), null)
        assertNotEquals(sut1a.hashCode(), sut2a.hashCode())
    }

    companion object {
        private val itemAsJsonObjectWithMembers = JSONObject(
            """
            { 'members': [
            { 'state': '52.5200066,13.4029540', 'type': 'Location', 'name': 'GroupDemoLocation',
              'label': 'Location 1', 'groupNames': [ 'LocationGroup' ] },
            { 'state': '52.5200066,13.4029540', 'type': 'Location', 'name': 'GroupDemoLocation',
              'label': 'Location 2', 'groupNames': [ 'LocationGroup' ] },
            ], 'state': 'NULL', 'type': 'Group', 'name': 'LocationGroup', 'label': 'Location Group',
                'tags': [ "Lighting", "Switchable" ] }
            """.trimIndent()
        )
        private val itemAsJsonObject = JSONObject(
            """
              { 'state': 'NULL',
                'type': 'Group',
                'name': 'LocationGroup',
                'label': 'Location Group',
                'tags': [ "Lighting", "Switchable" ] }
            """.trimIndent()
        )
    }
}
