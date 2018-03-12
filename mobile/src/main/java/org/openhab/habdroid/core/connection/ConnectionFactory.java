package org.openhab.habdroid.core.connection;

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
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.loopj.android.image.WebImageCache;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.core.CloudMessagingHelper;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.core.connection.exception.NetworkNotAvailableException;
import org.openhab.habdroid.core.connection.exception.NetworkNotSupportedException;
import org.openhab.habdroid.core.connection.exception.NoUrlInformationException;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.MyHttpClient;
import org.openhab.habdroid.util.MySyncHttpClient;
import org.openhab.habdroid.util.MyWebImage;
import org.openhab.habdroid.util.Util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import okhttp3.Call;
import okhttp3.Headers;

/**
 * A factory class, which is the main entry point to get a Connection to a specific openHAB
 * server. Use this factory class whenever you need to obtain a connection to load additional
 * data from the openHAB server or another supported source (see the constants in {@link Connection}).
 */
final public class ConnectionFactory extends BroadcastReceiver implements
        SharedPreferences.OnSharedPreferenceChangeListener, Handler.Callback {
    private static final String TAG = ConnectionFactory.class.getSimpleName();
    private static final List<Integer> LOCAL_CONNECTION_TYPES = Arrays.asList(
            ConnectivityManager.TYPE_ETHERNET, ConnectivityManager.TYPE_WIFI,
            ConnectivityManager.TYPE_WIMAX, ConnectivityManager.TYPE_VPN);
    private static final List<String> UPDATE_TRIGGERING_KEYS = Arrays.asList(
            Constants.PREFERENCE_LOCAL_URL, Constants.PREFERENCE_REMOTE_URL,
            Constants.PREFERENCE_LOCAL_USERNAME, Constants.PREFERENCE_LOCAL_PASSWORD,
            Constants.PREFERENCE_REMOTE_USERNAME, Constants.PREFERENCE_REMOTE_PASSWORD,
            Constants.PREFERENCE_DEMOMODE);

    public interface UpdateListener {
        void onConnectionChanged();
    }

    private static final int MSG_TRIGGER_UPDATE = 0;
    private static final int MSG_UPDATE_CLOUD_CONNECTION = 1;
    private static final int MSG_UPDATE_DONE = 2;

    private Context ctx;
    private SharedPreferences settings;

    private Connection mLocalConnection;
    private AbstractConnection mRemoteConnection;
    private CloudConnection mCloudConnection;
    private Connection mAvailableConnection;
    private ConnectionException mConnectionFailureReason;
    private HashSet<UpdateListener> mListeners = new HashSet<>();
    private boolean mNeedsUpdate;
    private boolean mIgnoreNextConnectivityChange;
    private boolean mIsInitialized;
    private Object mInitializationLock = new Object();

    private HandlerThread mUpdateThread;
    @VisibleForTesting
    public Handler mUpdateHandler;
    @VisibleForTesting
    public Handler mMainHandler;

    @VisibleForTesting
    public static ConnectionFactory sInstance;

    ConnectionFactory(Context ctx, SharedPreferences settings) {
        this.ctx = ctx;
        this.settings = settings;
        this.settings.registerOnSharedPreferenceChangeListener(this);

        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        // Make sure to ignore the initial sticky broadcast, as we're only interested in changes
        mIgnoreNextConnectivityChange = ctx.registerReceiver(null, filter) != null;
        ctx.registerReceiver(this, filter);

        mUpdateThread = new HandlerThread("ConnectionUpdate");
        mUpdateThread.start();
        mUpdateHandler = new Handler(mUpdateThread.getLooper(), this);
        mMainHandler = new Handler(Looper.getMainLooper(), this);

        updateConnections();
    }

    public static void initialize(Context ctx) {
        initialize(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
    }

    public static void initialize(Context ctx, SharedPreferences settings) {
        sInstance = new ConnectionFactory(ctx, settings);
    }

    public static void shutdown() {
        sInstance.ctx.unregisterReceiver(sInstance);
        sInstance.mUpdateThread.quit();
    }

    public static void blockingWaitForInitialization() {
        synchronized (sInstance.mInitializationLock) {
            while (!sInstance.mIsInitialized) {
                try {
                    sInstance.mInitializationLock.wait();
                } catch (InterruptedException e) {
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
            if (mNeedsUpdate) {
                mUpdateHandler.sendEmptyMessage(MSG_TRIGGER_UPDATE);
                mNeedsUpdate = false;
            } else if (mLocalConnection != null && mListeners.size() == 1) {
                // When coming back from background, re-do connectivity check for
                // local connections, as the reachability of the local server might have
                // changed since we went to background
                NoUrlInformationException nuie = mConnectionFailureReason instanceof NoUrlInformationException
                        ? (NoUrlInformationException) mConnectionFailureReason : null;
                boolean local = mAvailableConnection == mLocalConnection
                        || (nuie != null && nuie.wouldHaveUsedLocalConnection());
                if (local) {
                    mUpdateHandler.sendEmptyMessage(MSG_TRIGGER_UPDATE);
                }
            }
        }
    }

    public static void removeListener(UpdateListener l) {
        sInstance.mListeners.remove(l);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (UPDATE_TRIGGERING_KEYS.contains(key)) {
            updateConnections();
        }
    }

    public static void restartNetworkCheck() {
        sInstance.mUpdateHandler.sendEmptyMessage(MSG_TRIGGER_UPDATE);
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
        if (sInstance.mNeedsUpdate) {
            restartNetworkCheck();
            sInstance.mNeedsUpdate = false;
        }
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
            mNeedsUpdate = true;
        } else {
            mUpdateHandler.sendEmptyMessage(MSG_TRIGGER_UPDATE);
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_TRIGGER_UPDATE: { // update thread
                Connection local, remote;
                synchronized (this) {
                    local = mLocalConnection;
                    remote = mRemoteConnection;
                }
                ConnectionUpdateResult result = new ConnectionUpdateResult();
                try {
                    result.available = determineAvailableConnection(ctx, local, remote);
                } catch (ConnectionException e) {
                    result.availableFailureReason = e;
                }
                if (remote != null) {
                    mUpdateHandler.obtainMessage(MSG_UPDATE_CLOUD_CONNECTION, result).sendToTarget();
                } else {
                    mMainHandler.obtainMessage(MSG_UPDATE_DONE, result).sendToTarget();
                }
                return true;
            }
            case MSG_UPDATE_CLOUD_CONNECTION: { // update thread
                ConnectionUpdateResult result = (ConnectionUpdateResult) msg.obj;
                final AbstractConnection remote;
                synchronized (this) {
                    remote = mRemoteConnection;
                }
                if (remote != null) {
                    final MySyncHttpClient client = remote.getSyncHttpClient();
                    client.get(CloudConnection.SETTINGS_ROUTE, new MyHttpClient.TextResponseHandler() {
                        @Override
                        public void onFailure(Call call, int statusCode, Headers headers, String responseBody, Throwable error) {
                            Log.e(TAG, "Error loading notification settings: " + error.getMessage());
                        }

                        @Override
                        public void onSuccess(Call call, int statusCode, Headers headers, String responseBody) {
                            try {
                                JSONObject json = new JSONObject(responseBody);
                                String senderId = json.getJSONObject("gcm").getString("senderId");
                                result.cloud = new CloudConnection(remote, senderId);
                            } catch (JSONException e) {
                                Log.d(TAG, "Unable to parse notification settings JSON", e);
                            }
                        }
                    });
                }
                mMainHandler.obtainMessage(MSG_UPDATE_DONE, result).sendToTarget();
                return true;
            }
            case MSG_UPDATE_DONE: { // main thread
                ConnectionUpdateResult result = (ConnectionUpdateResult) msg.obj;
                boolean changed = false;
                // Check whether the passed connection matches a known one. If not, the
                // connections were updated while the thread was processing and we'll get
                // a new callback.
                if (result.availableFailureReason != null
                        || result.available == mLocalConnection
                        || result.available == mRemoteConnection) {
                    changed = updateAvailableConnection(result.available, result.availableFailureReason);
                }
                if (result.cloud != mCloudConnection) {
                    mCloudConnection = result.cloud;
                    CloudMessagingHelper.onConnectionUpdated(ctx, mCloudConnection);
                    changed = true;
                }
                if (changed) {
                    for (UpdateListener l : mListeners) {
                        l.onConnectionChanged();
                    }
                }
                synchronized (mInitializationLock) {
                    mIsInitialized = true;
                    mInitializationLock.notifyAll();
                }
                return true;
            }
        }
        return false;
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
        WebImageCache imageCache = MyWebImage.getWebImageCache();
        if (imageCache != null) {
            imageCache.clear();
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
            // If local URL is configured and rechable
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
    public synchronized void updateConnections() {
        mCloudConnection = null;

        if (settings.getBoolean(Constants.PREFERENCE_DEMOMODE, false)) {
            mLocalConnection = mRemoteConnection = new DemoConnection(ctx, settings);
            updateAvailableConnection(mLocalConnection, null);
            CloudMessagingHelper.onConnectionUpdated(ctx, null);
        } else {
            mLocalConnection = makeConnection(Connection.TYPE_LOCAL, Constants.PREFERENCE_LOCAL_URL,
                    Constants.PREFERENCE_LOCAL_USERNAME, Constants.PREFERENCE_LOCAL_PASSWORD);
            mRemoteConnection = makeConnection(Connection.TYPE_REMOTE, Constants.PREFERENCE_REMOTE_URL,
                    Constants.PREFERENCE_REMOTE_USERNAME, Constants.PREFERENCE_REMOTE_PASSWORD);

            synchronized (mInitializationLock) {
                mIsInitialized = false;
            }
            mAvailableConnection = null;
            mConnectionFailureReason = null;
            mUpdateHandler.sendEmptyMessage(MSG_TRIGGER_UPDATE);
        }
    }

    private AbstractConnection makeConnection(int type, String urlKey,
            String userNameKey, String passwordKey) {
        String url = Util.normalizeUrl(settings.getString(urlKey, ""));
        if (url.isEmpty()) {
            return null;
        }
        return new DefaultConnection(ctx, settings, type, url,
                settings.getString(userNameKey, null), settings.getString(passwordKey, null));
    }

    private static class ConnectionUpdateResult {
        CloudConnection cloud;
        Connection available;
        ConnectionException availableFailureReason;
    }
}
