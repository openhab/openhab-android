package org.openhab.habdroid.core.connection;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

public class CloudConnection extends DefaultConnection {
    static final String SETTINGS_ROUTE = "/api/v1/settings/notifications";

    private String mSenderId;

    public CloudConnection(Context ctx, SharedPreferences settings,
            Connection baseConnection, JSONObject settingsObject) throws JSONException {
        super(ctx, settings, TYPE_CLOUD, baseConnection.getOpenHABUrl(),
                baseConnection.getUsername(), baseConnection.getPassword());
        mSenderId = settingsObject.getJSONObject("gcm").getString("senderId");
    }

    public String getMessagingSenderId() {
        return mSenderId;
    }
}
