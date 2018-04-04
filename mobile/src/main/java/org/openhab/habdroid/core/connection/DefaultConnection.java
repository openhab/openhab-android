package org.openhab.habdroid.core.connection;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;

public class DefaultConnection extends AbstractConnection {
    DefaultConnection(Context ctx, int connectionType, String baseUrl,
            String username, String password, String clientCertAlias) {
        super(ctx, connectionType, baseUrl, username, password, clientCertAlias);
    }

    DefaultConnection(@NonNull AbstractConnection baseConnection, int connectionType) {
        super(baseConnection, connectionType);
    }
}
