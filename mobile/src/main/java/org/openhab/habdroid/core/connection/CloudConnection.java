package org.openhab.habdroid.core.connection;

import android.util.Log;
import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.util.SyncHttpClient;

public class CloudConnection extends DefaultConnection {
    private static final String TAG = CloudConnection.class.getSimpleName();

    private String mSenderId;

    private CloudConnection(@NonNull AbstractConnection baseConnection, @NonNull String senderId) {
        super(baseConnection, TYPE_CLOUD);
        mSenderId = senderId;
    }

    @NonNull
    public String getMessagingSenderId() {
        return mSenderId;
    }

    /**
     * Creates a {@link CloudConnection} instance if possible.
     *
     * It does so by checking whether the given connection supports the needed HTTP endpoints.
     * As this means causing network I/O, this method MUST NOT be called from the main thread.
     *
     * @param connection  Connection to base the cloud connection on
     * @return  A cloud connection instance if the passed in connection supports the needed
     *          HTTP endpoints, or null otherwise.
     */
    public static CloudConnection fromConnection(AbstractConnection connection) {
        final SyncHttpClient client = connection.getSyncHttpClient();
        SyncHttpClient.HttpTextResult result = client.get("api/v1/settings/notifications").asText();
        if (!result.isSuccessful()) {
            Log.e(TAG, "Error loading notification settings: " + result.error);
            return null;
        }

        try {
            JSONObject json = new JSONObject(result.response);
            String senderId = json.getJSONObject("gcm").getString("senderId");
            return new CloudConnection(connection, senderId);
        } catch (JSONException e) {
            Log.d(TAG, "Unable to parse notification settings JSON", e);
            return null;
        }
    }
}
