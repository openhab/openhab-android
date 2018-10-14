package org.openhab.habdroid.core.connection;

import androidx.annotation.NonNull;

import okhttp3.OkHttpClient;

public class DefaultConnection extends AbstractConnection {
    DefaultConnection(OkHttpClient httpClient, int connectionType,
            String baseUrl, String username, String password) {
        super(httpClient, connectionType, baseUrl, username, password);
    }

    DefaultConnection(@NonNull AbstractConnection baseConnection, int connectionType) {
        super(baseConnection, connectionType);
    }
}
