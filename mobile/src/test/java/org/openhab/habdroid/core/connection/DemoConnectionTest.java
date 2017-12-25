package org.openhab.habdroid.core.connection;

import android.content.Context;

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
        testConnection = new DemoConnection(mockContext);
    }

    @Test
    public void testGetOpenHABUrl() {
        assertEquals("http://demo.openhab.org:8080/", testConnection.getOpenHABUrl());
    }

    @Test
    public void testGetConnectionType() {
        assertEquals(Connections.REMOTE, testConnection.getConnectionType());
    }

    @Test
    public void testGetUsername() {
        assertNull(testConnection.getUsername());
    }

    @Test
    public void testGetPassword() {
        assertNull(testConnection.getPassword());
    }

    @Test(expected = RuntimeException.class)
    public void testSetOpenHABUrl() {
        testConnection.setOpenHABUrl("Test");
    }

    @Test(expected = RuntimeException.class)
    public void testSetUsername() {
        testConnection.setUsername("Test");
    }

    @Test(expected = RuntimeException.class)
    public void testSetPassword() {
        testConnection.setPassword("Test");
    }

    @Test(expected = RuntimeException.class)
    public void testSetConnectionType() {
        testConnection.setConnectionType(Connections.REMOTE);
    }
}
