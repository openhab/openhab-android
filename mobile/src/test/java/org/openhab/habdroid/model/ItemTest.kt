package org.openhab.habdroid.model

import org.json.JSONException
import org.json.JSONObject
import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

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
        val sut = JSONObject("""
            { 'members': [
            { 'state': '52.5200066,13.4029540', 'type': 'Location', 'name': 'GroupDemoLocation',
              'label': 'Location 1', 'groupNames': [ 'LocationGroup' ] },
            { 'state': '52.5200066,13.4029540', 'type': 'Location', 'name': 'GroupDemoLocation',
              'label': 'Location 2', 'groupNames': [ 'LocationGroup' ] },
            ], 'state': 'NULL', 'type': 'Group', 'name': 'LocationGroup', 'label': 'Location Group' }
            """.trimIndent()).toItem()
        assertEquals(2, sut.members.size)
        assertEquals(Item.Type.Location, sut.members[0].type)
        assertEquals("Location 2", sut.members[1].label)
    }
}
