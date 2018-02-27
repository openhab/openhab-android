package org.openhab.habdroid.core.connection;

import android.content.Context;
import android.content.SharedPreferences;

public class DefaultConnection extends AbstractConnection {
    DefaultConnection(Context ctx, SharedPreferences settings, int connectionType,
            String baseUrl, String username, String password) {
        super(ctx, settings, connectionType, baseUrl, username, password);
    }
}
