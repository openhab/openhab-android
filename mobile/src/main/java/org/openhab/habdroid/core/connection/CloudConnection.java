package org.openhab.habdroid.core.connection;

import android.support.annotation.NonNull;

public class CloudConnection extends DefaultConnection {
    static final String SETTINGS_ROUTE = "/api/v1/settings/notifications";

    private String mSenderId;

    public CloudConnection(@NonNull AbstractConnection baseConnection, @NonNull String senderId) {
        super(baseConnection, TYPE_CLOUD);
        mSenderId = senderId;
    }

    @NonNull
    public String getMessagingSenderId() {
        return mSenderId;
    }
}
