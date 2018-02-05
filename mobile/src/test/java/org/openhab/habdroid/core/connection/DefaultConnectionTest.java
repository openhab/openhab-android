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
    private Connection testConnectionRemote;
    private Connection testConnectionCloud;

    private SharedPreferences mockSettings;
    private Context mockContext;

    @Before
    public void setup() {
        mockContext = Mockito.mock(Context.class);
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
    public void testAsyncHasUsernamePassword() {
        Connection connection = new DefaultConnection(mockContext, mockSettings, Connection.TYPE_LOCAL,
                TEST_BASE_URL, "Test-User", "Test-Password");
        MyHttpClient httpClient = connection.getAsyncHttpClient();

        assertTrue(httpClient.getHeaders().containsKey("Authorization"));
        assertEquals(Credentials.basic("Test-User", "Test-Password"),
                httpClient.getHeaders().get("Authorization"));
    }

    @Test
    public void testSyncHasUsernamePassword() {
        Connection connection = new DefaultConnection(mockContext, mockSettings, Connection.TYPE_LOCAL,
                TEST_BASE_URL, "Test-User", "Test-Password");
        MyHttpClient httpClient = connection.getSyncHttpClient();

        assertTrue(httpClient.getHeaders().containsKey("Authorization"));
        assertEquals(Credentials.basic("Test-User", "Test-Password"),
                httpClient.getHeaders().get("Authorization"));
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
