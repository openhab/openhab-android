package org.openhab.habdroid.model;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class OpenHABItemTest {

    @Mock
    JSONObject mockJSONObject;

    @Test
    public void getStateAsBoolean_stateOff_returnFalse() throws Exception {
        OpenHABItem sut = new OpenHABItem(mockJSONObject);
        sut.setState("OFF");

        assertFalse(sut.getStateAsBoolean());
    }

    @Test
         public void getStateAsBoolean_stateON_returnTrue() throws Exception {
        OpenHABItem sut = new OpenHABItem(mockJSONObject);
        sut.setState("ON");

        assertTrue(sut.getStateAsBoolean());
    }

    @Test
    public void getStateAsBoolean_stateNull_returnFalse() throws Exception {
        OpenHABItem sut = new OpenHABItem(mockJSONObject);
        sut.setState(null);

        assertFalse(sut.getStateAsBoolean());
    }

    @Test
    public void getStateAsBoolean_stateNegativeInteger_returnFalse() throws Exception {
        OpenHABItem sut = new OpenHABItem(mockJSONObject);
        sut.setState("-42");

        assertFalse(sut.getStateAsBoolean());
    }

    @Test
    public void getStateAsBoolean_statePositiveInteger_returnTrue() throws Exception {
        OpenHABItem sut = new OpenHABItem(mockJSONObject);
        sut.setState("42");

        assertTrue(sut.getStateAsBoolean());
    }

    @Test
    public void getStateAsBoolean_stateIsZero_returnFalse() throws Exception {
        OpenHABItem sut = new OpenHABItem(mockJSONObject);
        sut.setState("0");

        assertFalse(sut.getStateAsBoolean());
    }

    @Test
    public void getStateAsBoolean_stateHSBBrightnessZero_returnFalse() throws Exception {
        OpenHABItem sut = new OpenHABItem(mockJSONObject);
        sut.setState("10,10,0");

        assertFalse(sut.getStateAsBoolean());
    }

    @Test
    public void getStateAsBoolean_stateHSBBrightnessPositive_returnTrue() throws Exception {
        OpenHABItem sut = new OpenHABItem(mockJSONObject);
        sut.setState("10,10,50");

        assertTrue(sut.getStateAsBoolean());
    }
}