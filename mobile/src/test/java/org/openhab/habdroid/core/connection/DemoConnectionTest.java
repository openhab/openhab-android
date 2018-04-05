package org.openhab.habdroid.core.connection;

import org.junit.Before;
import org.junit.Test;

import okhttp3.OkHttpClient;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

public class DemoConnectionTest {
    private Connection testConnection;

    @Before
    public void setup() {
        OkHttpClient client = new OkHttpClient.Builder().build();
        testConnection = new DemoConnection(client);
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
