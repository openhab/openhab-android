package org.openhab.habdroid.core.connection;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;

public class DefaultConnection extends AbstractConnection {
    DefaultConnection(Context ctx, SharedPreferences settings, int connectionType,
            String baseUrl, String username, String password) {
        super(ctx, settings, connectionType, baseUrl, username, password);
    }

    DefaultConnection(@NonNull AbstractConnection baseConnection, int connectionType) {
        super(baseConnection, connectionType);
    }
}
