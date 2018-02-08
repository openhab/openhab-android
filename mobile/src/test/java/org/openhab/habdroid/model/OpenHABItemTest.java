package org.openhab.habdroid.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class OpenHABItemTest {
    @Test
    public void getStateAsBoolean_stateOff_returnFalse() throws Exception {
        OpenHABItem sut = OpenHABItem.fromJson(itemJsonForState("OFF"));
        assertFalse(sut.stateAsBoolean());
    }

    @Test
         public void getStateAsBoolean_stateON_returnTrue() throws Exception {
        OpenHABItem sut = OpenHABItem.fromJson(itemJsonForState("ON"));
        assertTrue(sut.stateAsBoolean());
    }

    @Test
    public void getStateAsBoolean_stateNull_returnFalse() throws Exception {
        OpenHABItem sut = OpenHABItem.fromJson(itemJsonForState(null));
        assertFalse(sut.stateAsBoolean());
    }

    @Test
    public void getStateAsBoolean_stateNegativeInteger_returnFalse() throws Exception {
        OpenHABItem sut = OpenHABItem.fromJson(itemJsonForState("-42"));
        assertFalse(sut.stateAsBoolean());
    }

    @Test
    public void getStateAsBoolean_statePositiveInteger_returnTrue() throws Exception {
        OpenHABItem sut = OpenHABItem.fromJson(itemJsonForState("42"));
        assertTrue(sut.stateAsBoolean());
    }

    @Test
    public void getStateAsBoolean_stateIsZero_returnFalse() throws Exception {
        OpenHABItem sut = OpenHABItem.fromJson(itemJsonForState("0"));
        assertFalse(sut.stateAsBoolean());
    }

    @Test
    public void getStateAsBoolean_stateHSBBrightnessZero_returnFalse() throws Exception {
        OpenHABItem sut = OpenHABItem.fromJson(itemJsonForState("10,10,0"));
        assertFalse(sut.stateAsBoolean());
    }

    @Test
    public void getStateAsBoolean_stateHSBBrightnessPositive_returnTrue() throws Exception {
        OpenHABItem sut = OpenHABItem.fromJson(itemJsonForState("10,10,50"));
        assertTrue(sut.stateAsBoolean());
    }

    private JSONObject itemJsonForState(String state) throws JSONException {
        String statePart = state != null ? ", state: '" + state + "'" : "";
        return new JSONObject("{ 'name': 'foo', 'type': Dummy'" + statePart + " }");
    }

    @Test
    public void isReadOnly() throws Exception {
        JSONObject object = new JSONObject();
        object.put("name", "TestItem");
        object.put("type",  "Dummy");
        assertFalse(OpenHABItem.fromJson(object).readOnly());

        object.put("stateDescription", new JSONObject().put("readOnly", true));
        assertTrue(OpenHABItem.fromJson(object).readOnly());

        object.put("stateDescription", new JSONObject().put("readOnly", false));
        assertFalse(OpenHABItem.fromJson(object).readOnly());
    }
}