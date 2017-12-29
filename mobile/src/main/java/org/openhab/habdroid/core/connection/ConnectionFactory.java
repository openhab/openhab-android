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
import android.util.Log;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.exception.NetworkNotAvailableException;
import org.openhab.habdroid.core.connection.exception.NetworkNotSupportedException;
import org.openhab.habdroid.core.connection.exception.NoUrlInformationException;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A factory class, which is the main entry point to get a Connection to a specific openHAB
 * server. Use this factory class whenever you need to obtain a connection to load additional
 * data from the openHAB server or another supported source (see the constants in {@link Connection}).
 */
final public class ConnectionFactory
        extends BroadcastReceiver implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = ConnectionFactory.class.getSimpleName();
    private static final List<Integer> localConnectionTypes = new ArrayList<>(
            Arrays.asList(ConnectivityManager.TYPE_ETHERNET, ConnectivityManager.TYPE_WIFI,
                    ConnectivityManager.TYPE_WIMAX));
    private static final List<String> needInvalidateCachePreferenceKeys = Arrays.asList(Constants
            .PREFERENCE_ALTURL, Constants.PREFERENCE_LOCAL_USERNAME, Constants
            .PREFERENCE_LOCAL_PASSWORD, Constants.PREFERENCE_REMOTE_PASSWORD, Constants
            .PREFERENCE_REMOTE_USERNAME, Constants.PREFERENCE_URL);

    private Context ctx;
    private SharedPreferences settings;
    private NetworkInfo activeNetworkInfo;

    Map<Integer, Connection> cachedConnections = new HashMap<>();

    private static class InstanceHolder {
        public static final ConnectionFactory INSTANCE = new ConnectionFactory();
    }

    static ConnectionFactory getInstance() {
        return InstanceHolder.INSTANCE;
    }

    public ConnectionFactory() {
        if (Build.VERSION.SDK_INT >= 21) {
            localConnectionTypes.add(ConnectivityManager.TYPE_VPN);
        }
    }

    private void setContext(Context ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (needInvalidateCachePreferenceKeys.contains(key)) {
            cachedConnections.clear();
        }
    }

    private void setSettings(SharedPreferences settings) {
        if (this.settings != null) {
            this.settings.unregisterOnSharedPreferenceChangeListener(this);
        }
        this.settings = settings;

        this.settings.registerOnSharedPreferenceChangeListener(this);
    }

    public static Connection getConnection(int connectionType, Context ctx) {
        return getConnection(connectionType, ctx,
                PreferenceManager.getDefaultSharedPreferences(ctx));
    }

    public static Connection getConnection(int connectionType, Context ctx,
                                           SharedPreferences settings) {
        // FIXME: Once we're on API level 19, use Objects.requireNonNull();
        if (ctx == null) {
            throw new NullPointerException("Context ctx can not be null.");
        }

        ConnectionFactory factory = getInstance();
        factory.setSettings(settings);
        factory.setContext(ctx);

        ctx.registerReceiver(factory, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        if (factory.cachedConnections.containsKey(Connection.TYPE_ANY)) {
            return factory.cachedConnections.get(Connection.TYPE_ANY);
        }

        Connection conn;
        switch (connectionType) {
            case Connection.TYPE_LOCAL:
                conn = factory.getLocalConnection();
                break;
            case Connection.TYPE_REMOTE:
                conn = factory.getRemoteConnection();
                break;
            case Connection.TYPE_CLOUD:
                // TODO: Need a proper way of finding if the connection supports openHAB cloud
                // things, e.g. by checking if the /api/v1/settings/notifications endpoint works,
                // but currently does not work for myopenhab.org
                conn = factory.getRemoteConnection();
                break;
            case Connection.TYPE_ANY:
                conn = factory.getAvailableConnection();
                break;
            default:
                throw new IllegalArgumentException("Invalid Connection type requested.");
        }

        if (conn != null)
            factory.cachedConnections.put(connectionType, conn);

        return conn;
    }

    private Connection getLocalConnection() {
        String openHABUrl = Util.normalizeUrl(settings.getString(Constants
                .PREFERENCE_URL, ""));
        // If local URL is configured
        if (openHABUrl.length() > 0) {
            Log.d(TAG, "Connecting to local URL " + openHABUrl);
            Connection conn = new DefaultConnection(ctx);
            conn.setOpenHABUrl(openHABUrl);
            conn.setConnectionType(Connection.TYPE_LOCAL);

            if (hasUsername()) {
                conn.setUsername(getUsername());
            }
            if (hasPassword()) {
                conn.setPassword(getPassword());
            }

            return conn;
        } else {
            return null;
        }
    }

    private String getPassword() {
        return settings.getString(Constants.PREFERENCE_LOCAL_PASSWORD, null);
    }

    private boolean hasPassword() {
        return getPassword() != null && !getPassword().isEmpty();
    }

    private String getUsername() {
        return settings.getString(Constants.PREFERENCE_LOCAL_USERNAME, null);
    }

    private boolean hasUsername() {
        return getUsername() != null && !getUsername().isEmpty();
    }

    private Connection getRemoteConnection() {
        String openHABUrl = Util.normalizeUrl(settings.getString(Constants
                .PREFERENCE_ALTURL, ""));
        // If remote URL is configured
        if (openHABUrl.length() > 0) {
            Log.d(TAG, "Connecting to remote URL " + openHABUrl);
            Connection conn = new DefaultConnection(ctx);
            conn.setOpenHABUrl(openHABUrl);
            conn.setConnectionType(Connection.TYPE_REMOTE);

            if (hasRemoteUsername()) {
                conn.setUsername(getRemoteUsername());
            }
            if (hasRemotePassword()) {
                conn.setPassword(getRemotePassword());
            }

            return conn;
        } else {
            return null;
        }
    }

    private String getRemotePassword() {
        return settings.getString(Constants.PREFERENCE_REMOTE_PASSWORD, null);
    }

    private boolean hasRemotePassword() {
        return getRemotePassword() != null && !getRemotePassword().isEmpty();
    }

    private String getRemoteUsername() {
        return settings.getString(Constants.PREFERENCE_REMOTE_USERNAME, null);
    }

    private boolean hasRemoteUsername() {
        return getRemoteUsername() != null && !getRemoteUsername().isEmpty();
    }

    private Connection getAvailableConnection() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);

        activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        if (activeNetworkInfo != null) {
            if (settings.getBoolean(Constants.PREFERENCE_DEMOMODE, false)) {
                Log.d(TAG, "Demo mode");

                return new DemoConnection(ctx);
            } else {
                // If we are on a mobile network go directly to remote URL from settings
                if (activeNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                    if (getRemoteConnection() == null) {
                        throw new NoUrlInformationException();
                    }
                    return getRemoteConnection();

                    // Else if we are on Wifi or Ethernet network
                } else if (localConnectionTypes.contains(activeNetworkInfo.getType())) {
                    // See if we have a local URL configured in settings
                    Connection localConnection = getLocalConnection();

                    // If local URL is configured and rechable
                    if (localConnection != null && localConnection.isReachable()) {
                        Log.d(TAG, "Connecting to local URL");

                        return localConnection;
                    }
                    // If local URL is not reachable or not configured, try with remote URL
                    Connection remoteConnection = getRemoteConnection();
                    if (remoteConnection != null) {
                        Log.d(TAG, "Connecting to remote URL");
                        return getRemoteConnection();
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
        } else {
            Log.e(TAG, "Network is not available");
            throw new NetworkNotAvailableException(
                    ctx.getString(R.string.error_network_not_available));
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

        cachedConnections.clear();
    }
}
