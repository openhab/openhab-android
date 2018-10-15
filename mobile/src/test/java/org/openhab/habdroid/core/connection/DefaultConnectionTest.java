package org.openhab.habdroid.core.connection;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.junit.Before;
import org.junit.Test;
import org.openhab.habdroid.util.AsyncHttpClient;
import org.openhab.habdroid.util.HttpClient;
import org.openhab.habdroid.util.SyncHttpClient;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

public class DefaultConnectionTest {
    private static final String TEST_BASE_URL = "https://demo.local:8443";

    private OkHttpClient mClient;

    private Connection mTestConnection;
    private Connection mTestConnectionRemote;
    private Connection mTestConnectionCloud;

    @Before
    public void setup() {
        mClient = new OkHttpClient.Builder().build();
        mTestConnection = new DefaultConnection(mClient, Connection.TYPE_LOCAL,
                TEST_BASE_URL, null, null);
        mTestConnectionRemote = new DefaultConnection(mClient, Connection.TYPE_REMOTE,
                null, null, null);
        mTestConnectionCloud = new DefaultConnection(mClient,
                Connection.TYPE_CLOUD, null, null, null);
    }

    @Test
    public void testGetConnectionTypeSetRemote() {
        assertEquals(Connection.TYPE_REMOTE, mTestConnectionRemote.getConnectionType());
    }

    @Test
    public void testGetConnectionTypeSetLocal() {
        assertEquals(Connection.TYPE_LOCAL, mTestConnection.getConnectionType());
    }

    @Test
    public void testGetConnectionTypeSetCloud() {
        assertEquals(Connection.TYPE_CLOUD, mTestConnectionCloud.getConnectionType());
    }

    @Test
    public void testGetUsernameNotSet() {
        assertNull(mTestConnection.getUsername());
    }

    @Test
    public void testGetPasswordNotSet() {
        assertNull(mTestConnection.getPassword());
    }

    @Test
    public void testGetUsernameSet() {
        Connection connection = new DefaultConnection(mClient, Connection.TYPE_LOCAL,
                TEST_BASE_URL, "Test-User", null);
        assertEquals("Test-User", connection.getUsername());
    }

    @Test
    public void testGetPasswordSet() {
        Connection connection = new DefaultConnection(mClient, Connection.TYPE_LOCAL,
                TEST_BASE_URL, null, "Test-Password");
        assertEquals("Test-Password", connection.getPassword());
    }

    @Test
    public void testGetSyncHttpClientCached() {
        SyncHttpClient client1 = mTestConnection.getSyncHttpClient();
        SyncHttpClient client2 = mTestConnection.getSyncHttpClient();

        assertNotNull(client1);
        assertEquals(client1, client2);
    }

    @Test
    public void testGetAsyncHttpClientCached() {
        AsyncHttpClient client1 = mTestConnection.getAsyncHttpClient();
        AsyncHttpClient client2 = mTestConnection.getAsyncHttpClient();

        assertNotNull(client1);
        assertEquals(client1, client2);
    }

    @Test
    public void testAsyncHasNoUsernamePassword() {
        HttpClient httpClient = mTestConnection.getAsyncHttpClient();
        assertNull(httpClient.mAuthHeader);
    }

    @Test
    public void testSyncHasNoUsernamePassword() {
        HttpClient httpClient = mTestConnection.getSyncHttpClient();
        assertNull(httpClient.mAuthHeader);
    }

    @Test
    public void testAsyncHasUsernamePassword() {
        Connection connection = new DefaultConnection(mClient, Connection.TYPE_LOCAL,
                TEST_BASE_URL, "Test-User", "Test-Password");
        HttpClient httpClient = connection.getAsyncHttpClient();

        assertEquals(Credentials.basic("Test-User", "Test-Password"),
                httpClient.mAuthHeader);
    }

    @Test
    public void testSyncHasUsernamePassword() {
        Connection connection = new DefaultConnection(mClient, Connection.TYPE_LOCAL,
                TEST_BASE_URL, "Test-User", "Test-Password");
        HttpClient httpClient = connection.getSyncHttpClient();

        assertEquals(Credentials.basic("Test-User", "Test-Password"),
                httpClient.mAuthHeader);
    }

    @Test
    public void testSyncResolveRelativeUrl() {
        SyncHttpClient.HttpResult result = mTestConnection.getSyncHttpClient().get("rest/test");
        assertFalse("The request should never succeed in tests", result.isSuccessful());
        assertEquals(TEST_BASE_URL + "/rest/test", result.request.url().toString());
        result.close();

        result = mTestConnection.getSyncHttpClient().get("/rest/test");
        assertEquals(TEST_BASE_URL + "/rest/test", result.request.url().toString());
        result.close();
    }

    @Test
    public void testSyncResolveAbsoluteUrl() {
        SyncHttpClient.HttpResult result =
                mTestConnection.getSyncHttpClient().get("http://mylocalmachine.local/rest/test");
        assertFalse("The request should never succeed in tests", result.isSuccessful());
        assertEquals("http://mylocalmachine.local/rest/test", result.request.url().toString());
        result.close();
    }
}
