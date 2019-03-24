package org.openhab.habdroid.core.connection;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.security.KeyChain;
import android.security.KeyChainException;
import android.util.Log;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Pair;

import de.duenndns.ssl.MemorizingTrustManager;
import okhttp3.OkHttpClient;
import okhttp3.internal.tls.OkHostnameVerifier;
import okhttp3.logging.HttpLoggingInterceptor;

import org.openhab.habdroid.core.CloudMessagingHelper;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.core.connection.exception.NetworkNotAvailableException;
import org.openhab.habdroid.core.connection.exception.NetworkNotSupportedException;
import org.openhab.habdroid.core.connection.exception.NoUrlInformationException;
import org.openhab.habdroid.util.CacheManager;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.Util;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;

/**
 * A factory class, which is the main entry point to get a Connection to a specific openHAB
 * server. Use this factory class whenever you need to obtain a connection to load additional
 * data from the openHAB server or another supported source
 * (see the constants in {@link Connection}).
 */
public final class ConnectionFactory extends BroadcastReceiver implements
        SharedPreferences.OnSharedPreferenceChangeListener, Handler.Callback {
    private static final String TAG = ConnectionFactory.class.getSimpleName();
    private static final List<Integer> LOCAL_CONNECTION_TYPES = Arrays.asList(
            ConnectivityManager.TYPE_ETHERNET, ConnectivityManager.TYPE_WIFI,
            ConnectivityManager.TYPE_WIMAX, ConnectivityManager.TYPE_VPN);
    private static final List<String> CLIENT_CERT_UPDATE_TRIGGERING_KEYS = Arrays.asList(
            Constants.PREFERENCE_DEMOMODE, Constants.PREFERENCE_SSLCLIENTCERT
    );
    private static final List<String> UPDATE_TRIGGERING_KEYS = Arrays.asList(
            Constants.PREFERENCE_LOCAL_URL, Constants.PREFERENCE_REMOTE_URL,
            Constants.PREFERENCE_LOCAL_USERNAME, Constants.PREFERENCE_LOCAL_PASSWORD,
            Constants.PREFERENCE_REMOTE_USERNAME, Constants.PREFERENCE_REMOTE_PASSWORD,
            Constants.PREFERENCE_SSLCLIENTCERT, Constants.PREFERENCE_DEMOMODE);

    public interface UpdateListener {
        void onAvailableConnectionChanged();
        void onCloudConnectionChanged(CloudConnection connection);
    }

    private static final int MSG_UPDATE_AVAILABLE = 0;
    private static final int MSG_UPDATE_CLOUD = 1;
    private static final int MSG_AVAILABLE_DONE = 2;
    private static final int MSG_CLOUD_DONE = 3;

    private final Context mContext;
    private final SharedPreferences mPrefs;
    private final MemorizingTrustManager mTrustManager;
    private HttpLoggingInterceptor mHttpLogger;
    private OkHttpClient mHttpClient;
    private String mLastClientCertAlias;

    private Connection mLocalConnection;
    private AbstractConnection mRemoteConnection;
    private CloudConnection mCloudConnection;
    private Connection mAvailableConnection;
    private ConnectionException mConnectionFailureReason;
    private final HashSet<UpdateListener> mListeners = new HashSet<>();
    private boolean mNeedsUpdate;
    private boolean mIgnoreNextConnectivityChange;
    private boolean mAvailableInitialized;
    private boolean mCloudInitialized;
    private final Object mInitializationLock = new Object();

    private HandlerThread mUpdateThread;
    @VisibleForTesting
    public Handler mUpdateHandler;
    @VisibleForTesting
    public Handler mMainHandler;

    @VisibleForTesting
    public static ConnectionFactory sInstance;

    ConnectionFactory(Context context, SharedPreferences prefs) {
        mContext = context;
        mPrefs = prefs;
        prefs.registerOnSharedPreferenceChangeListener(this);

        mHttpLogger = new HttpLoggingInterceptor();
        updateHttpLoggerSettings();

        mTrustManager = new MemorizingTrustManager(context);
        mHttpClient = new OkHttpClient.Builder()
                .cache(CacheManager.getInstance(context).getHttpCache())
                .addInterceptor(mHttpLogger)
                .hostnameVerifier(mTrustManager.wrapHostnameVerifier(OkHostnameVerifier.INSTANCE))
                .build();
        updateHttpClientForClientCert(true);

        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        // Make sure to ignore the initial sticky broadcast, as we're only interested in changes
        mIgnoreNextConnectivityChange = context.registerReceiver(null, filter) != null;
        context.registerReceiver(this, filter);

        mUpdateThread = new HandlerThread("ConnectionUpdate");
        mUpdateThread.start();
        mUpdateHandler = new Handler(mUpdateThread.getLooper(), this);
        mMainHandler = new Handler(Looper.getMainLooper(), this);
    }

    public static void initialize(Context ctx) {
        sInstance = new ConnectionFactory(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        sInstance.updateConnections();
    }

    @VisibleForTesting
    public static void initialize(Context ctx, SharedPreferences settings) {
        sInstance = new ConnectionFactory(ctx, settings);
    }

    public static void shutdown() {
        sInstance.mContext.unregisterReceiver(sInstance);
        sInstance.mUpdateThread.quit();
    }

    /**
     * Wait for initialization of the factory.
     *
     * This method blocks until all asynchronous work (that is, determination of
     * available and cloud connection) is ready, so that {@link #getConnection(int)}
     * and {@link #getUsableConnection()} can safely be used.
     *
     * It MUST NOT be called from the main thread.
     */
    public static void waitForInitialization() {
        sInstance.triggerConnectionUpdateIfNeededAndPending();
        synchronized (sInstance.mInitializationLock) {
            while (!sInstance.mAvailableInitialized || !sInstance.mCloudInitialized) {
                try {
                    sInstance.mInitializationLock.wait();
                } catch (InterruptedException ignored) {
                    // ignored
                }
            }
        }
    }

    public static void addListener(UpdateListener l) {
        sInstance.addListenerInternal(l);
    }

    private void addListenerInternal(UpdateListener l) {
        if (mListeners.add(l)) {
            if (l instanceof Activity) {
                mTrustManager.bindDisplayActivity((Activity) l);
            }
            if (!triggerConnectionUpdateIfNeededAndPending()
                    && mLocalConnection != null && mListeners.size() == 1) {
                // When coming back from background, re-do connectivity check for
                // local connections, as the reachability of the local server might have
                // changed since we went to background
                NoUrlInformationException nuie =
                        mConnectionFailureReason instanceof NoUrlInformationException
                        ? (NoUrlInformationException) mConnectionFailureReason : null;
                boolean local = mAvailableConnection == mLocalConnection
                        || (nuie != null && nuie.wouldHaveUsedLocalConnection());
                if (local) {
                    triggerConnectionUpdateIfNeeded();
                }
            }
        }
    }

    public static void removeListener(UpdateListener l) {
        if (sInstance.mListeners.remove(l) && l instanceof Activity) {
            sInstance.mTrustManager.unbindDisplayActivity((Activity) l);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Constants.PREFERENCE_DEBUG_MESSAGES.equals(key)) {
            updateHttpLoggerSettings();
        }
        if (CLIENT_CERT_UPDATE_TRIGGERING_KEYS.contains(key)) {
            updateHttpClientForClientCert(false);
        }
        if (UPDATE_TRIGGERING_KEYS.contains(key)) {
            updateConnections();
        }
    }

    public static void restartNetworkCheck() {
        sInstance.triggerConnectionUpdateIfNeeded();
    }

    /**
     * Returns any openHAB connection that is most likely to work on the current network. The
     * connections available will be tried in the following order:
     *  - TYPE_LOCAL
     *  - TYPE_REMOTE
     *  - TYPE_CLOUD
     *
     * May return null if the available connection has not been initially determined yet.
     * Otherwise a Connection object is returned or, if there's an issue in configuration or
     * network connectivity, the respective exception is thrown.
     */
    public static Connection getUsableConnection() throws ConnectionException {
        sInstance.triggerConnectionUpdateIfNeededAndPending();
        if (sInstance.mConnectionFailureReason != null) {
            throw sInstance.mConnectionFailureReason;
        }
        return sInstance.mAvailableConnection;
    }

    /**
     * Returns a Connection of the specified type.
     *
     * May return null if no such connection is available
     * (in case the respective server isn't configured in settings)
     */
    public static Connection getConnection(int connectionType) {
        return sInstance.getConnectionInternal(connectionType);
    }

    private Connection getConnectionInternal(int connectionType) {
        switch (connectionType) {
            case Connection.TYPE_LOCAL:
                return mLocalConnection;
            case Connection.TYPE_REMOTE:
                return mRemoteConnection;
            case Connection.TYPE_CLOUD:
                return mCloudConnection;
            default:
                throw new IllegalArgumentException("Invalid Connection type requested.");
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mIgnoreNextConnectivityChange) {
            mIgnoreNextConnectivityChange = false;
            return;
        }
        if (mListeners.isEmpty()) {
            // We're running in background. Clear current state and postpone update for next
            // listener registration.
            mAvailableConnection = null;
            mConnectionFailureReason = null;
            synchronized (mInitializationLock) {
                mAvailableInitialized = false;
                mNeedsUpdate = true;
            }
        } else {
            triggerConnectionUpdateIfNeeded();
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_UPDATE_AVAILABLE: { // update thread
                //noinspection unchecked
                Pair<Connection, Connection> connections = (Pair<Connection, Connection>) msg.obj;
                Message result = mMainHandler.obtainMessage(MSG_AVAILABLE_DONE);
                try {
                    result.obj = determineAvailableConnection(mContext,
                            connections.first, connections.second);
                } catch (ConnectionException e) {
                    result.obj = e;
                }
                result.sendToTarget();
                return true;
            }
            case MSG_UPDATE_CLOUD: { // update thread
                AbstractConnection remote = (AbstractConnection) msg.obj;
                CloudConnection cloudConnection = remote != null
                        ? CloudConnection.fromConnection(remote) : null;
                mMainHandler.obtainMessage(MSG_CLOUD_DONE, cloudConnection).sendToTarget();
                return true;
            }
            case MSG_AVAILABLE_DONE: { // main thread
                Connection available = msg.obj instanceof Connection
                        ? (Connection) msg.obj : null;
                ConnectionException failureReason = msg.obj instanceof ConnectionException
                        ? (ConnectionException) msg.obj : null;
                handleAvailableCheckDone(available, failureReason);
                return true;
            }
            case MSG_CLOUD_DONE: { // main thread
                handleCloudCheckDone((CloudConnection) msg.obj);
                return true;
            }
            default:
                return false;
        }
    }

    private boolean updateAvailableConnection(Connection c, ConnectionException failureReason) {
        if (failureReason != null) {
            mConnectionFailureReason = failureReason;
            mAvailableConnection = null;
        } else if (c == mAvailableConnection) {
            return false;
        } else {
            mConnectionFailureReason = null;
            mAvailableConnection = c;
        }
        return true;
    }

    // called in update thread
    private static Connection determineAvailableConnection(Context context,
            Connection local, Connection remote) throws ConnectionException {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();

        if (info == null || !info.isConnected()) {
            Log.e(TAG, "Network is not available");
            throw new NetworkNotAvailableException();
        }

        // If we are on a mobile network go directly to remote URL from settings
        if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
            if (remote == null) {
                throw new NoUrlInformationException(false);
            }
            return remote;
        }

        // Else if we are on Wifi, Ethernet, WIMAX or VPN network
        if (LOCAL_CONNECTION_TYPES.contains(info.getType())) {
            // If local URL is configured and reachable
            if (local != null && local.checkReachabilityInBackground()) {
                Log.d(TAG, "Connecting to local URL");

                return local;
            }
            // If local URL is not reachable or not configured, try with remote URL
            if (remote != null) {
                Log.d(TAG, "Connecting to remote URL");
                return remote;
            } else {
                throw new NoUrlInformationException(true);
            }
            // Else we treat other networks types as unsupported
        } else {
            Log.e(TAG, "Network type (" + info.getTypeName() + ") is unsupported");
            throw new NetworkNotSupportedException(info);
        }
    }

    @VisibleForTesting
    public void updateConnections() {
        if (mPrefs.getBoolean(Constants.PREFERENCE_DEMOMODE, false)) {
            mLocalConnection = mRemoteConnection = new DemoConnection(mHttpClient);
            handleAvailableCheckDone(mLocalConnection, null);
            handleCloudCheckDone(null);
        } else {
            mLocalConnection = makeConnection(Connection.TYPE_LOCAL,
                    Constants.PREFERENCE_LOCAL_URL,
                    Constants.PREFERENCE_LOCAL_USERNAME, Constants.PREFERENCE_LOCAL_PASSWORD);
            mRemoteConnection = makeConnection(Connection.TYPE_REMOTE,
                    Constants.PREFERENCE_REMOTE_URL,
                    Constants.PREFERENCE_REMOTE_USERNAME, Constants.PREFERENCE_REMOTE_PASSWORD);

            synchronized (mInitializationLock) {
                mAvailableInitialized = false;
                mCloudInitialized = false;
            }
            mAvailableConnection = null;
            mCloudConnection = null;
            mConnectionFailureReason = null;
            triggerConnectionUpdateIfNeeded();
        }
    }

    private void updateHttpLoggerSettings() {
        if (mPrefs.getBoolean(Constants.PREFERENCE_DEBUG_MESSAGES, false)) {
            mHttpLogger.redactHeader("Authorization");
            mHttpLogger.redactHeader("set-cookie");
            mHttpLogger.setLevel(HttpLoggingInterceptor.Level.HEADERS);
        } else {
            mHttpLogger.setLevel(HttpLoggingInterceptor.Level.NONE);
        }
    }

    private void updateHttpClientForClientCert(boolean forceUpdate) {
        String clientCertAlias = mPrefs.getBoolean(Constants.PREFERENCE_DEMOMODE, false)
                ? null // No client cert in demo mode
                : mPrefs.getString(Constants.PREFERENCE_SSLCLIENTCERT, null);
        KeyManager[] keyManagers = clientCertAlias != null
                ? new KeyManager[] { new ClientKeyManager(mContext, clientCertAlias) }
                : null;

        // Updating the SSL socket factory is an expensive call;
        // make sure to only do this if really needed.
        if (!forceUpdate) {
            if (clientCertAlias == null && mLastClientCertAlias == null) {
                // No change: no client cert at all
                return;
            } else if (clientCertAlias != null && clientCertAlias.equals(mLastClientCertAlias)) {
                // No change: client cert stayed the same
                return;
            }
        }

        try {
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, new TrustManager[] { mTrustManager }, null);
            mHttpClient = mHttpClient.newBuilder()
                    .sslSocketFactory(sslContext.getSocketFactory(), mTrustManager)
                    .build();
            mLastClientCertAlias = clientCertAlias;
        } catch (Exception e) {
            Log.d(TAG, "Applying certificate trust settings failed", e);
        }
    }


    private void handleAvailableCheckDone(Connection available, ConnectionException failureReason) {
        // Check whether the passed connection matches a known one. If not, the
        // connections were updated while the thread was processing and we'll get
        // a new callback.
        if (failureReason != null
                || available == mLocalConnection
                || available == mRemoteConnection) {
            if (updateAvailableConnection(available, failureReason)) {
                for (UpdateListener l : mListeners) {
                    l.onAvailableConnectionChanged();
                }
            }
            synchronized (mInitializationLock) {
                mAvailableInitialized = true;
                mInitializationLock.notifyAll();
            }
        }
    }

    private void handleCloudCheckDone(CloudConnection connection) {
        if (connection != mCloudConnection) {
            mCloudConnection = connection;
            CloudMessagingHelper.onConnectionUpdated(mContext, connection);
            for (UpdateListener l : mListeners) {
                l.onCloudConnectionChanged(connection);
            }
        }
        synchronized (mInitializationLock) {
            mCloudInitialized = true;
            mInitializationLock.notifyAll();
        }
    }

    private boolean triggerConnectionUpdateIfNeededAndPending() {
        synchronized (mInitializationLock) {
            if (!mNeedsUpdate) {
                return false;
            }
            mNeedsUpdate = false;
            triggerConnectionUpdateIfNeeded();
        }
        return true;
    }

    private void triggerConnectionUpdateIfNeeded() {
        mUpdateHandler.removeMessages(MSG_UPDATE_AVAILABLE);
        mUpdateHandler.removeMessages(MSG_UPDATE_CLOUD);

        if (mLocalConnection instanceof DemoConnection) {
            return;
        }
        Pair<Connection, Connection> connections = Pair.create(mLocalConnection, mRemoteConnection);
        mUpdateHandler.obtainMessage(MSG_UPDATE_AVAILABLE, connections).sendToTarget();
        if (mRemoteConnection != null) {
            mUpdateHandler.obtainMessage(MSG_UPDATE_CLOUD, mRemoteConnection).sendToTarget();
        } else {
            mMainHandler.obtainMessage(MSG_CLOUD_DONE, null).sendToTarget();
        }
    }

    private AbstractConnection makeConnection(int type, String urlKey,
            String userNameKey, String passwordKey) {
        String url = Util.normalizeUrl(mPrefs.getString(urlKey, ""));
        if (url.isEmpty()) {
            return null;
        }
        return new DefaultConnection(mHttpClient, type, url,
                mPrefs.getString(userNameKey, null),
                mPrefs.getString(passwordKey, null));
    }

    private static class ClientKeyManager implements X509KeyManager {
        private static final String TAG = ClientKeyManager.class.getSimpleName();

        private Context mContext;
        private String mAlias;

        public ClientKeyManager(Context context, String alias) {
            mContext = context.getApplicationContext();
            mAlias = alias;
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            Log.d(TAG, "chooseClientAlias - alias: " + mAlias);
            return mAlias;
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            Log.d(TAG, "chooseServerAlias");
            return null;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            Log.d(TAG, "getCertificateChain", new Throwable());
            try {
                return KeyChain.getCertificateChain(mContext, alias);
            } catch (KeyChainException | InterruptedException e) {
                Log.e(TAG, "Failed loading certificate chain", e);
                return null;
            }
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            Log.d(TAG, "getClientAliases");
            return mAlias != null ? new String[] { mAlias } : null;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            Log.d(TAG, "getServerAliases");
            return null;
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            Log.d(TAG, "getPrivateKey", new Throwable());
            try {
                return KeyChain.getPrivateKey(mContext, mAlias);
            } catch (KeyChainException | InterruptedException e) {
                Log.e(TAG, "Failed loading private key", e);
                return null;
            }
        }
    }

}
