package org.openhab.habdroid.core.connection;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.openhab.habdroid.util.MyAsyncHttpClient;
import org.openhab.habdroid.util.MyHttpClient;
import org.openhab.habdroid.util.MySyncHttpClient;

import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.Headers;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;

public class DefaultConnectionTest {
    private static final String TEST_BASE_URL = "https://demo.local:8443";

    private Connection testConnection;

    private Connection testConnectionNoUrl;
    private SharedPreferences mockSettings;

    @Before
    public void setup() {
        Context mockContext = Mockito.mock(Context.class);
        mockSettings = Mockito.mock(SharedPreferences.class);
        testConnection = new DefaultConnection(mockContext, mockSettings);
        testConnectionNoUrl = new DefaultConnection(mockContext, mockSettings);
        testConnection.setOpenHABUrl(TEST_BASE_URL);
    }

    @Test
    public void testGetOpenHABUrlNotSet() {
        assertNull(testConnectionNoUrl.getOpenHABUrl());
    }

    @Test
    public void testGetOpenHABUrlSet() {
        testConnection.setOpenHABUrl("Test-URL");
        assertEquals("Test-URL", testConnection.getOpenHABUrl());
    }

    @Test
    public void testGetConnectionTypeNotSet() {
        assertEquals(Connection.TYPE_ANY, testConnection.getConnectionType());
    }

    @Test
    public void testGetConnectionTypeSetRemote() {
        testConnection.setConnectionType(Connection.TYPE_REMOTE);
        assertEquals(Connection.TYPE_REMOTE, testConnection.getConnectionType());
    }

    @Test
    public void testGetConnectionTypeSetLocal() {
        testConnection.setConnectionType(Connection.TYPE_LOCAL);
        assertEquals(Connection.TYPE_LOCAL, testConnection.getConnectionType());
    }

    @Test
    public void testGetConnectionTypeSetCloud() {
        testConnection.setConnectionType(Connection.TYPE_CLOUD);
        assertEquals(Connection.TYPE_CLOUD, testConnection.getConnectionType());
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
    public void testGetAsyncHttpClientCached() {
        Mockito.when(mockSettings.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        MyAsyncHttpClient client1 = testConnection.getAsyncHttpClient();
        MyAsyncHttpClient client2 = testConnection.getAsyncHttpClient();

        assertNotNull(client1);
        assertEquals(client1, client2);
    }

    @Test
    public void testAsyncHasUsernamePassword() {
        testConnection.setUsername("Test-User");
        testConnection.setPassword("Test-Password");
        MyHttpClient httpClient = testConnection.getAsyncHttpClient();

        assertTrue(httpClient.getHeaders().containsKey("Authorization"));
        assertEquals(Credentials.basic("Test-User", "Test-Password"),
                httpClient.getHeaders().get("Authorization"));
    }

    @Test
    public void testSyncHasUsernamePassword() {
        testConnection.setUsername("Test-User");
        testConnection.setPassword("Test-Password");
        MyHttpClient httpClient = testConnection.getSyncHttpClient();

        assertTrue(httpClient.getHeaders().containsKey("Authorization"));
        assertEquals(Credentials.basic("Test-User", "Test-Password"),
                httpClient.getHeaders().get("Authorization"));
    }

    @Test
    public void testAsyncHasNoUsernamePassword() {
        MyHttpClient httpClient = testConnection.getAsyncHttpClient();

        assertFalse(httpClient.getHeaders().containsKey("Authorization"));
    }

    @Test
    public void testSyncHasNoUsernamePassword() {
        MyHttpClient httpClient = testConnection.getSyncHttpClient();

        assertFalse(httpClient.getHeaders().containsKey("Authorization"));
    }

    @Test
    public void testSyncResolveRelativeUrl() {
        MyHttpClient httpClient = testConnection.getSyncHttpClient();

        httpClient.get("/rest/test", new MyHttpClient.TextResponseHandler() {
            @Override
            public void onFailure(Call call, int statusCode, Headers headers, String responseBody, Throwable error) {
                assertEquals(TEST_BASE_URL + "/rest/test", call.request().url().toString());
            }

            @Override
            public void onSuccess(Call call, int statusCode, Headers headers, String responseBody) {
                fail("The request should never succeed in tests.");
            }
        });
    }

    @Test
    public void testSyncResolveAbsoluteUrl() {
        MyHttpClient httpClient = testConnection.getSyncHttpClient();

        httpClient.get("http://mylocalmachine.local/rest/test",
                new MyHttpClient.TextResponseHandler() {
                    @Override
                    public void onFailure(Call call, int statusCode, Headers headers, String responseBody, Throwable error) {
                        assertEquals("http://mylocalmachine.local/rest/test", call.request().url().toString());
                    }

                    @Override
                    public void onSuccess(Call call, int statusCode, Headers headers, String responseBody) {
                        fail("The request should never succeed in tests.");
                    }
                });
    }
}
