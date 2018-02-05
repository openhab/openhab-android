package org.openhab.habdroid.core.connection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.VisibleForTesting;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.core.connection.exception.NetworkNotAvailableException;
import org.openhab.habdroid.core.connection.exception.NetworkNotSupportedException;
import org.openhab.habdroid.core.connection.exception.NoUrlInformationException;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A factory class, which is the main entry point to get a Connection to a specific openHAB
 * server. Use this factory class whenever you need to obtain a connection to load additional
 * data from the openHAB server or another supported source (see the constants in {@link Connection}).
 */
final public class ConnectionFactory extends BroadcastReceiver implements
        SharedPreferences.OnSharedPreferenceChangeListener, Handler.Callback {
    public static final String ACTION_NETWORK_CHANGED =
            "org.openhab.habdroid.core.connection.NETWORK_CHANGED";

    private static final String TAG = ConnectionFactory.class.getSimpleName();
    private static final List<Integer> localConnectionTypes = new ArrayList<>(
            Arrays.asList(ConnectivityManager.TYPE_ETHERNET, ConnectivityManager.TYPE_WIFI,
                    ConnectivityManager.TYPE_WIMAX));
    private static final List<String> needInvalidateCachePreferenceKeys = Arrays.asList(Constants
            .PREFERENCE_REMOTE_URL, Constants.PREFERENCE_LOCAL_USERNAME, Constants
            .PREFERENCE_LOCAL_PASSWORD, Constants.PREFERENCE_REMOTE_PASSWORD, Constants
            .PREFERENCE_REMOTE_USERNAME, Constants.PREFERENCE_LOCAL_URL, Constants.PREFERENCE_DEMOMODE);

    private static final int MSG_TRIGGER_UPDATE = 0;
    private static final int MSG_UPDATE_DONE = 1;

    private Context ctx;
    private SharedPreferences settings;

    private Connection mLocalConnection;
    private Connection mRemoteConnection;
    private Connection mAvailableConnection;
    private ConnectionException mConnectionFailureReason;

    private HandlerThread mUpdateThread;
    @VisibleForTesting
    public Handler mUpdateHandler;
    @VisibleForTesting
    public Handler mMainHandler;

    @VisibleForTesting
    public static ConnectionFactory sInstance;

    ConnectionFactory(Context ctx, SharedPreferences settings) {
        if (Build.VERSION.SDK_INT >= 21) {
            localConnectionTypes.add(ConnectivityManager.TYPE_VPN);
        }

        this.ctx = ctx;
        this.settings = settings;
        this.settings.registerOnSharedPreferenceChangeListener(this);

        ctx.registerReceiver(this,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
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

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (needInvalidateCachePreferenceKeys.contains(key)) {
            updateConnections();
        }
    }

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
                // TODO: Need a proper way of finding if the connection supports openHAB cloud
                // things, e.g. by checking if the /api/v1/settings/notifications endpoint works,
                // but currently does not work for myopenhab.org
                return mRemoteConnection;
            case Connection.TYPE_ANY:
                if (mConnectionFailureReason != null) {
                    throw mConnectionFailureReason;
                }
                return mAvailableConnection;
            default:
                throw new IllegalArgumentException("Invalid Connection type requested.");
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        mUpdateHandler.sendEmptyMessage(MSG_TRIGGER_UPDATE);
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_TRIGGER_UPDATE: // update thread
                Message result = mMainHandler.obtainMessage(MSG_UPDATE_DONE);
                Connection local, remote;
                synchronized (this) {
                    local = mLocalConnection;
                    remote = mRemoteConnection;
                }
                try {
                    result.obj = determineAvailableConnection(ctx, local, remote);
                } catch (ConnectionException e) {
                    result.obj = e;
                }
                mMainHandler.sendMessage(result);
                return true;
            case MSG_UPDATE_DONE: // main thread
                if (msg.obj instanceof ConnectionException) {
                    mAvailableConnection = null;
                    mConnectionFailureReason = (ConnectionException) msg.obj;
                } else {
                    // Check whether the passed connection matches a known one. If not, the
                    // connections were updated while the thread was processing and we'll get
                    // a new callback.
                    if (msg.obj == mLocalConnection || msg.obj == mRemoteConnection) {
                        mConnectionFailureReason = null;
                        if (mAvailableConnection != msg.obj) {
                            mAvailableConnection = (Connection) msg.obj;
                            LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(ctx);
                            lbm.sendBroadcast(new Intent(ACTION_NETWORK_CHANGED));
                        }
                    }
                }
                return true;
        }
        return false;
    }

    // called in update thread
    private static Connection determineAvailableConnection(Context context,
            Connection local, Connection remote) throws ConnectionException {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();

        if (info == null) {
            Log.e(TAG, "Network is not available");
            throw new NetworkNotAvailableException(
                    context.getString(R.string.error_network_not_available));
        }

        // If we are on a mobile network go directly to remote URL from settings
        if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
            if (remote == null) {
                throw new NoUrlInformationException(context.getString(R.string.error_no_url));
            }
            return remote;
        }

        // Else if we are on Wifi, Ethernet, WIMAX or VPN network
        if (localConnectionTypes.contains(info.getType())) {
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
                throw new NoUrlInformationException(context.getString(R.string.error_no_url));
            }
            // Else we treat other networks types as unsupported
        } else {
            Log.e(TAG, "Network type (" + info.getTypeName() + ") is unsupported");
            String message = context.getString(R.string.error_network_type_unsupported,
                    info.getTypeName());
            throw new NetworkNotSupportedException(message, info);
        }
    }

    @VisibleForTesting
    public synchronized void updateConnections() {
        if (settings.getBoolean(Constants.PREFERENCE_DEMOMODE, false)) {
            mAvailableConnection = new DemoConnection(ctx, settings);
            mLocalConnection = mRemoteConnection = mAvailableConnection;
            return;
        }
        mLocalConnection = makeConnection(Connection.TYPE_LOCAL, Constants.PREFERENCE_LOCAL_URL,
                Constants.PREFERENCE_LOCAL_USERNAME, Constants.PREFERENCE_LOCAL_USERNAME);
        mRemoteConnection = makeConnection(Connection.TYPE_REMOTE, Constants.PREFERENCE_REMOTE_URL,
                Constants.PREFERENCE_REMOTE_USERNAME, Constants.PREFERENCE_REMOTE_USERNAME);
        mUpdateHandler.sendEmptyMessage(MSG_TRIGGER_UPDATE);
    }

    private Connection makeConnection(int type, String urlKey,
            String userNameKey, String passwordKey) {
        String url = Util.normalizeUrl(settings.getString(urlKey, ""));
        if (url.isEmpty()) {
            return null;
        }
        return new DefaultConnection(ctx, settings, type, url,
                settings.getString(userNameKey, null), settings.getString(passwordKey, null));
    }
}
