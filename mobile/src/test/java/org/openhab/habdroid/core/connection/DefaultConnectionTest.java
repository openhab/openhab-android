package org.openhab.habdroid.core.connection;

import org.junit.Before;
import org.junit.Test;
import org.openhab.habdroid.util.AsyncHttpClient;
import org.openhab.habdroid.util.HttpClient;
import org.openhab.habdroid.util.SyncHttpClient;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

public class DefaultConnectionTest {
    private static final String TEST_BASE_URL = "https://demo.local:8443";

    private OkHttpClient client;

    private Connection testConnection;
    private Connection testConnectionRemote;
    private Connection testConnectionCloud;

    @Before
    public void setup() {
        client = new OkHttpClient.Builder().build();
        testConnection = new DefaultConnection(client, Connection.TYPE_LOCAL,
                TEST_BASE_URL, null, null);
        testConnectionRemote = new DefaultConnection(client, Connection.TYPE_REMOTE,
                null, null, null);
        testConnectionCloud = new DefaultConnection(client,
                Connection.TYPE_CLOUD, null, null, null);
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
        Connection connection = new DefaultConnection(client, Connection.TYPE_LOCAL,
                TEST_BASE_URL, "Test-User", null);
        assertEquals("Test-User", connection.getUsername());
    }

    @Test
    public void testGetPasswordSet() {
        Connection connection = new DefaultConnection(client, Connection.TYPE_LOCAL,
                TEST_BASE_URL, null, "Test-Password");
        assertEquals("Test-Password", connection.getPassword());
    }

    @Test
    public void testGetSyncHttpClientCached() {
        SyncHttpClient client1 = testConnection.getSyncHttpClient();
        SyncHttpClient client2 = testConnection.getSyncHttpClient();

        assertNotNull(client1);
        assertEquals(client1, client2);
    }

    @Test
    public void testGetAsyncHttpClientCached() {
        AsyncHttpClient client1 = testConnection.getAsyncHttpClient();
        AsyncHttpClient client2 = testConnection.getAsyncHttpClient();

        assertNotNull(client1);
        assertEquals(client1, client2);
    }

    @Test
    public void testAsyncHasNoUsernamePassword() {
        HttpClient httpClient = testConnection.getAsyncHttpClient();
        assertNull(httpClient.mAuthHeader);
    }

    @Test
    public void testSyncHasNoUsernamePassword() {
        HttpClient httpClient = testConnection.getSyncHttpClient();
        assertNull(httpClient.mAuthHeader);
    }

    @Test
    public void testAsyncHasUsernamePassword() {
        Connection connection = new DefaultConnection(client, Connection.TYPE_LOCAL,
                TEST_BASE_URL, "Test-User", "Test-Password");
        HttpClient httpClient = connection.getAsyncHttpClient();

        assertEquals(Credentials.basic("Test-User", "Test-Password"),
                httpClient.mAuthHeader);
    }

    @Test
    public void testSyncHasUsernamePassword() {
        Connection connection = new DefaultConnection(client, Connection.TYPE_LOCAL,
                TEST_BASE_URL, "Test-User", "Test-Password");
        HttpClient httpClient = connection.getSyncHttpClient();

        assertEquals(Credentials.basic("Test-User", "Test-Password"),
                httpClient.mAuthHeader);
    }

    @Test
    public void testSyncResolveRelativeUrl() {
        SyncHttpClient.HttpResult result = testConnection.getSyncHttpClient().get("rest/test");
        assertFalse("The request should never succeed in tests", result.isSuccessful());
        assertEquals(TEST_BASE_URL + "/rest/test", result.request.url().toString());
        result.close();

        result = testConnection.getSyncHttpClient().get("/rest/test");
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
