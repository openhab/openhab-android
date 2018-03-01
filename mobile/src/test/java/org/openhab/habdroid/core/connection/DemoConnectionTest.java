package org.openhab.habdroid.core.connection;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

public class DemoConnectionTest {
    private Connection testConnection;

    @Before
    public void setup() {
        Context mockContext = Mockito.mock(Context.class);
        SharedPreferences mockSettings = Mockito.mock(SharedPreferences.class);
        testConnection = new DemoConnection(mockContext, mockSettings);
    }

    @Test
    public void testGetOpenHABUrl() {
        assertEquals("https://demo.openhab.org:8443/", testConnection.getOpenHABUrl());
    }

    @Test
    public void testGetConnectionType() {
        assertEquals(Connection.TYPE_REMOTE, testConnection.getConnectionType());
    }

    @Test
    public void testGetUsername() {
        assertNull(testConnection.getUsername());
    }

    @Test
    public void testGetPassword() {
        assertNull(testConnection.getPassword());
    }
}
