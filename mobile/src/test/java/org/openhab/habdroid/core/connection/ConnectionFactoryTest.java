package org.openhab.habdroid.core.connection;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.openhab.habdroid.core.connection.exception.NetworkNotAvailableException;
import org.openhab.habdroid.core.connection.exception.NetworkNotSupportedException;
import org.openhab.habdroid.core.connection.exception.NoUrlInformationException;
import org.openhab.habdroid.util.Constants;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

public class ConnectionFactoryTest {

    private Context mockContext;
    private ConnectivityManager mockConnectivityService;
    private SharedPreferences mockSettings;

    @Before
    public void setup() {
        mockConnectivityService = Mockito.mock(ConnectivityManager.class);

        mockContext = Mockito.mock(Context.class);
        Mockito.when(mockContext.getString(anyInt())).thenReturn("");
        Mockito.when(mockContext.getSystemService(eq(Context.CONNECTIVITY_SERVICE)))
                .thenReturn(mockConnectivityService);

        mockSettings = Mockito.mock(SharedPreferences.class);

        ConnectionFactory.initialize(mockContext, mockSettings);
    }

    @Test
    public void testGetConnectionRemoteWithUrl() {
        Mockito.when(mockSettings.getString(eq(Constants.PREFERENCE_REMOTE_URL), anyString()))
                .thenReturn("https://myopenhab.org:8443");
        ConnectionFactory.sInstance.updateConnections();
        Connection conn = ConnectionFactory.getConnection(Connection.TYPE_REMOTE);

        assertNotNull("Requesting a remote connection when a remote url is set, should return a " +
                "connection.", conn);
        assertEquals("The connection type of a remote connection should be LOGLEVEL_REMOTE.",
                Connection.TYPE_REMOTE, conn.getConnectionType());
    }

    @Test
    public void testGetConnectionRemoteWithoutUrl() {
        Mockito.when(mockSettings.getString(eq(Constants.PREFERENCE_REMOTE_URL), anyString()))
                .thenReturn("");
        ConnectionFactory.sInstance.updateConnections();
        Connection conn = ConnectionFactory.getConnection(Connection.TYPE_REMOTE);

        assertNull("Requesting a remote connection when a remote url isn't set, should not " +
                "return a connection.", conn);
    }

    @Test
    public void testGetConnectionLocalWithUrl() {
        Mockito.when(mockSettings.getString(eq(Constants.PREFERENCE_LOCAL_URL), anyString()))
                .thenReturn("https://openhab.local:8080");
        ConnectionFactory.sInstance.updateConnections();
        Connection conn = ConnectionFactory.getConnection(Connection.TYPE_LOCAL);

        assertNotNull("Requesting a local connection when local url is set, should " +
                "return a connection.", conn);
        assertEquals("The connection type of a local connection should be LOGLEVEL_LOCAL.",
                Connection.TYPE_LOCAL, conn.getConnectionType());
    }

    @Test
    public void testGetConnectionLocalWithoutUrl() {
        Mockito.when(mockSettings.getString(eq(Constants.PREFERENCE_LOCAL_URL), anyString()))
                .thenReturn("");
        ConnectionFactory.sInstance.updateConnections();
        Connection conn = ConnectionFactory.getConnection(Connection.TYPE_LOCAL);

        assertNull("Requesting a remote connection when a local url isn't set, should not " +
                "return a connection.", conn);
    }

    @Test
    public void testGetConnectionCloudWithUrl() {
        Mockito.when(mockSettings.getString(eq(Constants.PREFERENCE_REMOTE_URL), anyString()))
                .thenReturn("https://myopenhab.org:8443");
        ConnectionFactory.sInstance.updateConnections();
        Connection conn = ConnectionFactory.getConnection(Connection.TYPE_CLOUD);

        assertNotNull("Requesting a cloud connection when a remote url is set, should return a " +
                "connection.", conn);
        assertEquals("The connection type of a cloud connection should be LOGLEVEL_REMOTE.",
                Connection.TYPE_REMOTE, conn.getConnectionType());
    }

    @Test(expected = NetworkNotAvailableException.class)
    public void testGetAnyConnectionNoNetwork() {
        Mockito.when(mockConnectivityService.getActiveNetworkInfo()).thenReturn(null);

        ConnectionFactory.getConnection(Connection.TYPE_ANY);
    }

    @Test(expected = NetworkNotSupportedException.class)
    public void testGetAnyConnectionUnsupportedNetwork() {
        NetworkInfo mockNetworkInfo = Mockito.mock(NetworkInfo.class);
        Mockito.when(mockNetworkInfo.getType()).thenReturn(ConnectivityManager.TYPE_BLUETOOTH);
        Mockito.when(mockNetworkInfo.getTypeName()).thenReturn("");

        Mockito.when(mockConnectivityService.getActiveNetworkInfo()).thenReturn(mockNetworkInfo);

        ConnectionFactory.getConnection(Connection.TYPE_ANY);
    }

    @Test
    public void testGetAnyConnectionWifiRemoteOnly() {
        Mockito.when(mockSettings.getString(eq(Constants.PREFERENCE_REMOTE_URL), anyString()))
                .thenReturn("https://myopenhab.org:8443");
        ConnectionFactory.sInstance.updateConnections();
        NetworkInfo mockNetworkInfo = Mockito.mock(NetworkInfo.class);
        Mockito.when(mockNetworkInfo.getType()).thenReturn(ConnectivityManager.TYPE_WIFI);

        Mockito.when(mockConnectivityService.getActiveNetworkInfo()).thenReturn(mockNetworkInfo);

        Connection conn = ConnectionFactory.getConnection(Connection.TYPE_ANY);

        assertNotNull("Requesting any connection in WIFI when only a remote url is set, should " +
                "return" +
                " a connection.", conn);
        assertEquals("The connection type of the connection should be TYPE_REMOTE.",
                Connection.TYPE_REMOTE, conn.getConnectionType());
    }

    @Test
    public void testGetAnyConnectionWifiLocalRemote() {
        Mockito.when(mockSettings.getString(eq(Constants.PREFERENCE_REMOTE_URL), anyString()))
                .thenReturn("https://openhab.remote");
        Mockito.when(mockSettings.getString(eq(Constants.PREFERENCE_LOCAL_URL), anyString()))
                .thenReturn("https://myopenhab.org:443");
        ConnectionFactory.sInstance.updateConnections();
        NetworkInfo mockNetworkInfo = Mockito.mock(NetworkInfo.class);
        Mockito.when(mockNetworkInfo.getType()).thenReturn(ConnectivityManager.TYPE_WIFI);

        Mockito.when(mockConnectivityService.getActiveNetworkInfo()).thenReturn(mockNetworkInfo);

        Connection conn = ConnectionFactory.getConnection(Connection.TYPE_ANY);

        assertNotNull("Requesting any connection in WIFI when a local url is set, should return" +
                " a connection.", conn);
        assertEquals("The connection type of the connection should be TYPE_LOCAL.",
                Connection.TYPE_LOCAL, conn.getConnectionType());
    }

    @Test(expected = NoUrlInformationException.class)
    public void testGetAnyConnectionWifiNoLocalNoRemote() {
        Mockito.when(mockSettings.getString(anyString(), anyString())).thenReturn(null);
        NetworkInfo mockNetworkInfo = Mockito.mock(NetworkInfo.class);
        Mockito.when(mockNetworkInfo.getType()).thenReturn(ConnectivityManager.TYPE_WIFI);

        Mockito.when(mockConnectivityService.getActiveNetworkInfo()).thenReturn(mockNetworkInfo);

        ConnectionFactory.getConnection(Connection.TYPE_ANY);
    }
}
