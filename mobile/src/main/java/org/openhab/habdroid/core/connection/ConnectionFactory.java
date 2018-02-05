package org.openhab.habdroid.core.connection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import org.openhab.habdroid.R;
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
final public class ConnectionFactory
        extends BroadcastReceiver implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String NETWORK_CHANGED = "org.openhab.habdroid.core" +
            ".connection.NETWORK_CHANGED";

    private static final String TAG = ConnectionFactory.class.getSimpleName();
    private static final List<Integer> localConnectionTypes = new ArrayList<>(
            Arrays.asList(ConnectivityManager.TYPE_ETHERNET, ConnectivityManager.TYPE_WIFI,
                    ConnectivityManager.TYPE_WIMAX));
    private static final List<String> needInvalidateCachePreferenceKeys = Arrays.asList(Constants
            .PREFERENCE_REMOTE_URL, Constants.PREFERENCE_LOCAL_USERNAME, Constants
            .PREFERENCE_LOCAL_PASSWORD, Constants.PREFERENCE_REMOTE_PASSWORD, Constants
            .PREFERENCE_REMOTE_USERNAME, Constants.PREFERENCE_LOCAL_URL, Constants.PREFERENCE_DEMOMODE);

    private Context ctx;
    private SharedPreferences settings;
    private NetworkInfo activeNetworkInfo;

    private Connection mLocalConnection;
    private Connection mRemoteConnection;

    private static ConnectionFactory sInstance;

    ConnectionFactory(Context ctx, SharedPreferences settings) {
        if (Build.VERSION.SDK_INT >= 21) {
            localConnectionTypes.add(ConnectivityManager.TYPE_VPN);
        }

        this.ctx = ctx;
        this.settings = settings;
        this.settings.registerOnSharedPreferenceChangeListener(this);

        ctx.registerReceiver(this,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
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
                return getAvailableConnection();
            default:
                throw new IllegalArgumentException("Invalid Connection type requested.");
        }
    }

    private Connection getAvailableConnection() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);

        activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        if (activeNetworkInfo == null) {
            Log.e(TAG, "Network is not available");
            throw new NetworkNotAvailableException(
                    ctx.getString(R.string.error_network_not_available));
        }

        // If we are on a mobile network go directly to remote URL from settings
        if (activeNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
            if (mRemoteConnection == null) {
                throw new NoUrlInformationException(ctx.getString(R.string.error_no_url));
            }
            return mRemoteConnection;
        }

        // Else if we are on Wifi, Ethernet, WIMAX or VPN network
        if (localConnectionTypes.contains(activeNetworkInfo.getType())) {
            // If local URL is configured and rechable
            if (mLocalConnection != null && mLocalConnection.isReachable()) {
                Log.d(TAG, "Connecting to local URL");

                return mLocalConnection;
            }
            // If local URL is not reachable or not configured, try with remote URL
            if (mRemoteConnection != null) {
                Log.d(TAG, "Connecting to remote URL");
                return mRemoteConnection;
            } else {
                throw new NoUrlInformationException(ctx.getString(R.string.error_no_url));
            }
            // Else we treat other networks types as unsupported
        } else {
            Log.e(TAG, "Network type (" + activeNetworkInfo.getTypeName() + ") is unsupported");
            NetworkNotSupportedException ex = new NetworkNotSupportedException(
                    String.format(ctx.getString(R.string.error_network_type_unsupported),
                            activeNetworkInfo.getTypeName()));

            ex.setNetworkInfo(activeNetworkInfo);

            throw ex;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (activeNetworkInfo == null || intent.getExtras() == null) {
            return;
        }
        NetworkInfo otherNetworkInfo = (NetworkInfo) intent.getExtras()
                .get(ConnectivityManager.EXTRA_NETWORK_INFO);
        if (otherNetworkInfo == null) {
            return;
        }
        if (activeNetworkInfo.getType() == otherNetworkInfo.getType() && otherNetworkInfo.isConnected()) {
            return;
        }

        Intent networkChangedIntent = new Intent(NETWORK_CHANGED);
        ctx.sendBroadcast(networkChangedIntent);
    }

    @VisibleForTesting
    public void updateConnections() {
        if (settings.getBoolean(Constants.PREFERENCE_DEMOMODE, false)) {
            mLocalConnection = mRemoteConnection = new DemoConnection(ctx, settings);
            return;
        }
        mLocalConnection = makeConnection(Connection.TYPE_LOCAL, Constants.PREFERENCE_LOCAL_URL,
                Constants.PREFERENCE_LOCAL_USERNAME, Constants.PREFERENCE_LOCAL_USERNAME);
        mRemoteConnection = makeConnection(Connection.TYPE_REMOTE, Constants.PREFERENCE_REMOTE_URL,
                Constants.PREFERENCE_REMOTE_USERNAME, Constants.PREFERENCE_REMOTE_USERNAME);
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
