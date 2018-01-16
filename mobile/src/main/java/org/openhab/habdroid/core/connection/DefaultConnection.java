package org.openhab.habdroid.core.connection;

import android.content.Context;
import android.content.SharedPreferences;

public class DefaultConnection extends AbstractConnection {
    DefaultConnection(Context ctx, SharedPreferences settings, int connectionType,
                      String username, String password, String baseUrl) {
        super(ctx, settings, connectionType, username, password, baseUrl);
    }
}
