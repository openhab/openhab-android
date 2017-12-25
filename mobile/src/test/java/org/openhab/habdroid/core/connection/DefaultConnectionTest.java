package org.openhab.habdroid.core.connection;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.openhab.habdroid.util.MyAsyncHttpClient;
import org.openhab.habdroid.util.MySyncHttpClient;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;

public class DefaultConnectionTest {
    private Connection testConnection;

    private SharedPreferences mockSettings;

    @Before
    public void setup() {
        Context mockContext = Mockito.mock(Context.class);
        mockSettings = Mockito.mock(SharedPreferences.class);
        testConnection = new DefaultConnection(mockContext, mockSettings);
    }

    @Test
    public void testGetOpenHABUrlNotSet() {
        assertNull(testConnection.getOpenHABUrl());
    }

    @Test
    public void testGetOpenHABUrlSet() {
        testConnection.setOpenHABUrl("Test-URL");
        assertEquals("Test-URL", testConnection.getOpenHABUrl());
    }

    @Test
    public void testGetConnectionTypeNotSet() {
        assertEquals(Connections.ANY, testConnection.getConnectionType());
    }

    @Test
    public void testGetConnectionTypeSetRemote() {
        testConnection.setConnectionType(Connections.REMOTE);
        assertEquals(Connections.REMOTE, testConnection.getConnectionType());
    }

    @Test
    public void testGetConnectionTypeSetLocal() {
        testConnection.setConnectionType(Connections.LOCAL);
        assertEquals(Connections.LOCAL, testConnection.getConnectionType());
    }

    @Test
    public void testGetConnectionTypeSetCloud() {
        testConnection.setConnectionType(Connections.CLOUD);
        assertEquals(Connections.CLOUD, testConnection.getConnectionType());
    }

    @Test
    public void testGetUsernameNotSet() {
        assertNull(testConnection.getUsername());
    }

    @Test
    public void testGetPasswordNotSet() {
        assertNull(testConnection.getPassword());
    }

    @Test
    public void testGetUsernameSet() {
        testConnection.setUsername("Test-User");
        assertEquals("Test-User", testConnection.getUsername());
    }

    @Test
    public void testGetPasswordSet() {
        testConnection.setPassword("Test-Password");
        assertEquals("Test-Password", testConnection.getPassword());
    }

    @Test
    public void testGetSyncHttpClientCached() {
        Mockito.when(mockSettings.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        MySyncHttpClient client1 = testConnection.getSyncHttpClient();
        MySyncHttpClient client2 = testConnection.getSyncHttpClient();

        assertNotNull(client1);
        assertEquals(client1, client2);
    }

    @Test
    public void testGetASyncHttpClientCached() {
        Mockito.when(mockSettings.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        MyAsyncHttpClient client1 = testConnection.getAsyncHttpClient();
        MyAsyncHttpClient client2 = testConnection.getAsyncHttpClient();

        assertNotNull(client1);
        assertEquals(client1, client2);
    }
}
