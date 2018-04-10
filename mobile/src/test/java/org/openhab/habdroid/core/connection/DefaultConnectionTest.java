package org.openhab.habdroid.core.connection;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.openhab.habdroid.util.AsyncHttpClient;
import org.openhab.habdroid.util.HttpClient;
import org.openhab.habdroid.util.SyncHttpClient;

import okhttp3.Credentials;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;

public class DefaultConnectionTest {
    private static final String TEST_BASE_URL = "https://demo.local:8443";

    private Connection testConnection;
    private Connection testConnectionNoUrl;
    private Connection testConnectionRemote;
    private Connection testConnectionCloud;

    private SharedPreferences mockSettings;
    private Context mockContext;

    @Before
    public void setup() {
        mockContext = Mockito.mock(Application.class);
        Mockito.when(mockContext.getApplicationContext()).thenReturn(mockContext);
        mockSettings = Mockito.mock(SharedPreferences.class);
        testConnection = new DefaultConnection(mockContext, mockSettings, Connection.TYPE_LOCAL,
                TEST_BASE_URL, null, null);
        testConnectionNoUrl = new DefaultConnection(mockContext, mockSettings, Connection.TYPE_LOCAL,
                null, null, null);
        testConnectionRemote = new DefaultConnection(mockContext, mockSettings,
                Connection.TYPE_REMOTE, null, null, null);
        testConnectionCloud = new DefaultConnection(mockContext, mockSettings,
                Connection.TYPE_CLOUD, null, null, null);
    }

    @Test
    public void testGetOpenHABUrlNotSet() {
        assertNull(testConnectionNoUrl.getOpenHABUrl());
    }

    @Test
    public void testGetOpenHABUrlSet() {
        assertEquals(TEST_BASE_URL, testConnection.getOpenHABUrl());
    }

    @Test
    public void testGetConnectionTypeSetRemote() {
        assertEquals(Connection.TYPE_REMOTE, testConnectionRemote.getConnectionType());
    }

    @Test
    public void testGetConnectionTypeSetLocal() {
        assertEquals(Connection.TYPE_LOCAL, testConnection.getConnectionType());
    }

    @Test
    public void testGetConnectionTypeSetCloud() {
        assertEquals(Connection.TYPE_CLOUD, testConnectionCloud.getConnectionType());
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
        Connection connection = new DefaultConnection(mockContext, mockSettings, Connection.TYPE_LOCAL,
                TEST_BASE_URL, "Test-User", null);
        assertEquals("Test-User", connection.getUsername());
    }

    @Test
    public void testGetPasswordSet() {
        Connection connection = new DefaultConnection(mockContext, mockSettings, Connection.TYPE_LOCAL,
                TEST_BASE_URL, null, "Test-Password");
        assertEquals("Test-Password", connection.getPassword());
    }

    @Test
    public void testGetSyncHttpClientCached() {
        Mockito.when(mockSettings.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        SyncHttpClient client1 = testConnection.getSyncHttpClient();
        SyncHttpClient client2 = testConnection.getSyncHttpClient();

        assertNotNull(client1);
        assertEquals(client1, client2);
    }

    @Test
    public void testGetAsyncHttpClientCached() {
        Mockito.when(mockSettings.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        AsyncHttpClient client1 = testConnection.getAsyncHttpClient();
        AsyncHttpClient client2 = testConnection.getAsyncHttpClient();

        assertNotNull(client1);
        assertEquals(client1, client2);
    }

    @Test
    public void testAsyncHasNoUsernamePassword() {
        HttpClient httpClient = testConnection.getAsyncHttpClient();

        assertFalse(httpClient.getHeaders().containsKey("Authorization"));
    }

    @Test
    public void testSyncHasNoUsernamePassword() {
        HttpClient httpClient = testConnection.getSyncHttpClient();

        assertFalse(httpClient.getHeaders().containsKey("Authorization"));
    }

    @Test
    public void testAsyncHasUsernamePassword() {
        Connection connection = new DefaultConnection(mockContext, mockSettings, Connection.TYPE_LOCAL,
                TEST_BASE_URL, "Test-User", "Test-Password");
        HttpClient httpClient = connection.getAsyncHttpClient();

        assertTrue(httpClient.getHeaders().containsKey("Authorization"));
        assertEquals(Credentials.basic("Test-User", "Test-Password"),
                httpClient.getHeaders().get("Authorization"));
    }

    @Test
    public void testSyncHasUsernamePassword() {
        Connection connection = new DefaultConnection(mockContext, mockSettings, Connection.TYPE_LOCAL,
                TEST_BASE_URL, "Test-User", "Test-Password");
        HttpClient httpClient = connection.getSyncHttpClient();

        assertTrue(httpClient.getHeaders().containsKey("Authorization"));
        assertEquals(Credentials.basic("Test-User", "Test-Password"),
                httpClient.getHeaders().get("Authorization"));
    }

    @Test
    public void testSyncResolveRelativeUrl() {
        SyncHttpClient.HttpResult result = testConnection.getSyncHttpClient().get("/rest/test");
        assertFalse("The request should never succeed in tests", result.isSuccessful());
        assertEquals(TEST_BASE_URL + "/rest/test", result.request.url().toString());
        result.close();
    }

    @Test
    public void testSyncResolveAbsoluteUrl() {
        SyncHttpClient.HttpResult result =
                testConnection.getSyncHttpClient().get("http://mylocalmachine.local/rest/test");
        assertFalse("The request should never succeed in tests", result.isSuccessful());
        assertEquals("http://mylocalmachine.local/rest/test", result.request.url().toString());
        result.close();
    }
}
