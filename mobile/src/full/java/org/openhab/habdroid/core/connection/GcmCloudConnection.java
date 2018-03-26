package org.openhab.habdroid.core.connection;

import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.util.MyHttpClient;
import org.openhab.habdroid.util.MySyncHttpClient;

import okhttp3.Call;
import okhttp3.Headers;

public class GcmCloudConnection extends DefaultConnection {
    private static final String TAG = GcmCloudConnection.class.getSimpleName();
    private static final String SETTINGS_ROUTE = "/api/v1/settings/notifications";

    private String mSenderId;

    private GcmCloudConnection(@NonNull AbstractConnection baseConnection, @NonNull String senderId) {
        super(baseConnection, TYPE_CLOUD);
        mSenderId = senderId;
    }

    @NonNull
    public String getGcmSenderId() {
        return mSenderId;
    }

    /**
     * Creates a {@link Connection} instance if possible.
     *
     * It does so by checking whether the given connection supports the needed HTTP endpoints.
     * As this means causing network I/O, this method MUST NOT be called from the main thread.
     *
     * @param connection  Connection to base the cloud connection on
     * @return  A cloud connection instance if the passed in connection supports the needed
     *          HTTP endpoints, or null otherwise.
     */
    public static Connection fromConnection(AbstractConnection connection) {
        if (connection == null) {
            return null;
        }

        final MySyncHttpClient client = connection.getSyncHttpClient();
        Connection[] holder = new Connection[1];

        client.get(SETTINGS_ROUTE, new MyHttpClient.TextResponseHandler() {
            @Override
            public void onFailure(Call call, int statusCode, Headers headers, String responseBody, Throwable error) {
                Log.e(TAG, "Error loading notification settings: " + error.getMessage());
            }

            @Override
            public void onSuccess(Call call, int statusCode, Headers headers, String responseBody) {
                try {
                    JSONObject json = new JSONObject(responseBody);
                    String senderId = json.getJSONObject("gcm").getString("senderId");
                    holder[0] = new GcmCloudConnection(connection, senderId);
                } catch (JSONException e) {
                    Log.d(TAG, "Unable to parse notification settings JSON", e);
                }
            }
        });
        return holder[0];
    }
}
