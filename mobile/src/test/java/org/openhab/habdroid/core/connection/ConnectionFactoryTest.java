package org.openhab.habdroid.core.connection;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.core.connection.exception.NetworkNotAvailableException;
import org.openhab.habdroid.core.connection.exception.NetworkNotSupportedException;
import org.openhab.habdroid.core.connection.exception.NoUrlInformationException;
import org.openhab.habdroid.util.Constants;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class ConnectionFactoryTest {

    private Context mockContext;
    private ConnectivityManager mockConnectivityService;
    private SharedPreferences mockSettings;

    @Before
    public void setup() {
        mockConnectivityService = Mockito.mock(ConnectivityManager.class);

        mockContext = Mockito.mock(Context.class);
        when(mockContext.getString(anyInt())).thenReturn("");
        when(mockContext.getSystemService(eq(Context.CONNECTIVITY_SERVICE)))
                .thenReturn(mockConnectivityService);
        when(mockContext.getApplicationContext()).thenReturn(mockContext);
        when(mockContext.getMainLooper()).thenReturn(Looper.getMainLooper());

        mockSettings = Mockito.mock(SharedPreferences.class);

        ConnectionFactory.initialize(mockContext, mockSettings);

        ConnectionFactory.sInstance.mMainHandler = makeMockedHandler();
        ConnectionFactory.sInstance.mUpdateHandler = makeMockedHandler();
    }

    @Test
    public void testGetConnectionRemoteWithUrl() throws ConnectionException {
        when(mockSettings.getString(eq(Constants.PREFERENCE_REMOTE_URL), anyString()))
                .thenReturn("https://myopenhab.org:8443");
        ConnectionFactory.sInstance.updateConnections();
        Connection conn = ConnectionFactory.getConnection(Connection.TYPE_REMOTE);

        assertNotNull("Requesting a remote connection when a remote url is set, should return a " +
                "connection.", conn);
        assertEquals("The connection type of a remote connection should be LOGLEVEL_REMOTE.",
                Connection.TYPE_REMOTE, conn.getConnectionType());
    }

    @Test
    public void testGetConnectionRemoteWithoutUrl() throws ConnectionException {
        when(mockSettings.getString(eq(Constants.PREFERENCE_REMOTE_URL), anyString()))
                .thenReturn("");
        ConnectionFactory.sInstance.updateConnections();
        Connection conn = ConnectionFactory.getConnection(Connection.TYPE_REMOTE);

        assertNull("Requesting a remote connection when a remote url isn't set, should not " +
                "return a connection.", conn);
    }

    @Test
    public void testGetConnectionLocalWithUrl() throws ConnectionException {
        when(mockSettings.getString(eq(Constants.PREFERENCE_LOCAL_URL), anyString()))
                .thenReturn("https://openhab.local:8080");
        ConnectionFactory.sInstance.updateConnections();
        Connection conn = ConnectionFactory.getConnection(Connection.TYPE_LOCAL);

        assertNotNull("Requesting a local connection when local url is set, should " +
                "return a connection.", conn);
        assertEquals("The connection type of a local connection should be LOGLEVEL_LOCAL.",
                Connection.TYPE_LOCAL, conn.getConnectionType());
    }

    @Test
    public void testGetConnectionLocalWithoutUrl() throws ConnectionException {
        when(mockSettings.getString(eq(Constants.PREFERENCE_LOCAL_URL), anyString()))
                .thenReturn("");
        ConnectionFactory.sInstance.updateConnections();
        Connection conn = ConnectionFactory.getConnection(Connection.TYPE_LOCAL);

        assertNull("Requesting a remote connection when a local url isn't set, should not " +
                "return a connection.", conn);
    }

    @Test
    public void testGetConnectionCloudWithUrl() throws ConnectionException {
        when(mockSettings.getString(eq(Constants.PREFERENCE_REMOTE_URL), anyString()))
                .thenReturn("https://myopenhab.org:8443");
        ConnectionFactory.sInstance.updateConnections();
        Connection conn = ConnectionFactory.getConnection(Connection.TYPE_CLOUD);

        assertNotNull("Requesting a cloud connection when a remote url is set, should return a " +
                "connection.", conn);
        assertEquals("The connection type of a cloud connection should be LOGLEVEL_REMOTE.",
                Connection.TYPE_REMOTE, conn.getConnectionType());
    }

    @Test(expected = NetworkNotAvailableException.class)
    public void testGetAnyConnectionNoNetwork() throws ConnectionException {
        triggerNetworkUpdate(null);

        ConnectionFactory.getUsableConnection();
    }

    @Test(expected = NetworkNotSupportedException.class)
    public void testGetAnyConnectionUnsupportedNetwork() throws ConnectionException {
        triggerNetworkUpdate(ConnectivityManager.TYPE_BLUETOOTH);

        ConnectionFactory.getUsableConnection();
    }

    @Test
    public void testGetAnyConnectionWifiRemoteOnly() throws ConnectionException {
        when(mockSettings.getString(eq(Constants.PREFERENCE_REMOTE_URL), anyString()))
                .thenReturn("https://myopenhab.org:8443");
        ConnectionFactory.sInstance.updateConnections();
        triggerNetworkUpdate(ConnectivityManager.TYPE_WIFI);

        Connection conn = ConnectionFactory.getUsableConnection();

        assertNotNull("Requesting any connection in WIFI when only a remote url is set, should " +
                "return" +
                " a connection.", conn);
        assertEquals("The connection type of the connection should be TYPE_REMOTE.",
                Connection.TYPE_REMOTE, conn.getConnectionType());
    }

    @Test
    public void testGetAnyConnectionWifiLocalRemote() throws ConnectionException {
        when(mockSettings.getString(eq(Constants.PREFERENCE_REMOTE_URL), anyString()))
                .thenReturn("https://openhab.remote");
        when(mockSettings.getString(eq(Constants.PREFERENCE_LOCAL_URL), anyString()))
                .thenReturn("https://myopenhab.org:443");
        ConnectionFactory.sInstance.updateConnections();
        triggerNetworkUpdate(ConnectivityManager.TYPE_WIFI);

        Connection conn = ConnectionFactory.getUsableConnection();

        assertNotNull("Requesting any connection in WIFI when a local url is set, should return" +
                " a connection.", conn);
        assertEquals("The connection type of the connection should be TYPE_LOCAL.",
                Connection.TYPE_LOCAL, conn.getConnectionType());
    }

    @Test(expected = NoUrlInformationException.class)
    public void testGetAnyConnectionWifiNoLocalNoRemote() throws ConnectionException {
        when(mockSettings.getString(anyString(), anyString())).thenReturn(null);
        triggerNetworkUpdate(ConnectivityManager.TYPE_WIFI);

        ConnectionFactory.getUsableConnection();
    }

    private void triggerNetworkUpdate(int type) {
        NetworkInfo mockNetworkInfo = Mockito.mock(NetworkInfo.class);
        when(mockNetworkInfo.getType()).thenReturn(type);
        when(mockNetworkInfo.isConnected()).thenReturn(true);
        triggerNetworkUpdate(mockNetworkInfo);
    }

    private void triggerNetworkUpdate(NetworkInfo info) {
        when(mockConnectivityService.getActiveNetworkInfo()).thenReturn(info);

        ConnectionFactory.sInstance.onReceive(mockContext,
                new Intent(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    private Handler makeMockedHandler() {
        final Handler h = Mockito.mock(Handler.class);
        when(h.sendEmptyMessage(anyInt())).thenAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocationOnMock) throws Throwable {
                Message msg = new Message();
                msg.what = invocationOnMock.getArgument(0);
                ConnectionFactory.sInstance.handleMessage(msg);
                return Boolean.TRUE;
            }
        });
        when(h.sendMessage(any(Message.class))).thenAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocationOnMock) throws Throwable {
                Message msg = invocationOnMock.getArgument(0);
                ConnectionFactory.sInstance.handleMessage(msg);
                return Boolean.TRUE;
            }
        });
        when(h.obtainMessage(anyInt())).thenAnswer(new Answer<Message>() {
            @Override
            public Message answer(InvocationOnMock invocationOnMock) throws Throwable {
                Message msg = new Message();
                msg.what = invocationOnMock.getArgument(0);
                return msg;
            }
        });
        return h;
    }
}
