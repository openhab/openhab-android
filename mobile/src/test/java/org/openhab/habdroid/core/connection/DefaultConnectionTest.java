package org.openhab.habdroid.core.connection;

import android.content.Context;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openhab.habdroid.TestUtils;
import org.openhab.habdroid.util.AsyncHttpClient;
import org.openhab.habdroid.util.HttpClient;
import org.openhab.habdroid.util.SyncHttpClient;

import java.io.IOException;

import okhttp3.Credentials;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

public class DefaultConnectionTest {
    private static final String TEST_BASE_URL = "https://demo.local:8443";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Connection testConnection;
    private Connection testConnectionRemote;
    private Connection testConnectionCloud;

    private Context mockContext;

    @Before
    public void setup() throws IOException {
        mockContext = TestUtils.makeMockedAppContext(tempFolder);
        testConnection = new DefaultConnection(mockContext, Connection.TYPE_LOCAL,
                TEST_BASE_URL, null, null, null);
        testConnectionRemote = new DefaultConnection(mockContext,
                Connection.TYPE_REMOTE, null, null, null, null);
        testConnectionCloud = new DefaultConnection(mockContext,
                Connection.TYPE_CLOUD, null, null, null, null);
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
        Connection connection = new DefaultConnection(mockContext, Connection.TYPE_LOCAL,
                TEST_BASE_URL, "Test-User", null, null);
        assertEquals("Test-User", connection.getUsername());
    }

    @Test
    public void testGetPasswordSet() {
        Connection connection = new DefaultConnection(mockContext, Connection.TYPE_LOCAL,
                TEST_BASE_URL, null, "Test-Password", null);
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

        assertFalse(httpClient.getHeaders().containsKey("Authorization"));
    }

    @Test
    public void testSyncHasNoUsernamePassword() {
        HttpClient httpClient = testConnection.getSyncHttpClient();

        assertFalse(httpClient.getHeaders().containsKey("Authorization"));
    }

    @Test
    public void testAsyncHasUsernamePassword() {
        Connection connection = new DefaultConnection(mockContext, Connection.TYPE_LOCAL,
                TEST_BASE_URL, "Test-User", "Test-Password", null);
        HttpClient httpClient = connection.getAsyncHttpClient();

        assertTrue(httpClient.getHeaders().containsKey("Authorization"));
        assertEquals(Credentials.basic("Test-User", "Test-Password"),
                httpClient.getHeaders().get("Authorization"));
    }

    @Test
    public void testSyncHasUsernamePassword() {
        Connection connection = new DefaultConnection(mockContext, Connection.TYPE_LOCAL,
                TEST_BASE_URL, "Test-User", "Test-Password", null);
        HttpClient httpClient = connection.getSyncHttpClient();

        assertTrue(httpClient.getHeaders().containsKey("Authorization"));
        assertEquals(Credentials.basic("Test-User", "Test-Password"),
                httpClient.getHeaders().get("Authorization"));
    }

    @Test
    public void testSyncResolveRelativeUrl() {
        SyncHttpClient.HttpResult result = testConnection.getSyncHttpClient().get("rest/test");
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
