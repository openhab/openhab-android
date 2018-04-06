package org.openhab.habdroid.core.connection;

import org.junit.Before;
import org.junit.Test;

import okhttp3.OkHttpClient;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

public class DemoConnectionTest {
    private Connection mTestConnection;

    @Before
    public void setup() {
        OkHttpClient client = new OkHttpClient.Builder().build();
        mTestConnection = new DemoConnection(client);
    }

    @Test
    public void testGetConnectionType() {
        assertEquals(Connection.TYPE_REMOTE, mTestConnection.getConnectionType());
    }

    @Test
    public void testGetUsername() {
        assertNull(mTestConnection.getUsername());
    }

    @Test
    public void testGetPassword() {
        assertNull(mTestConnection.getPassword());
    }
}
