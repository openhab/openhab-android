package org.openhab.habdroid.core.connection;

import android.content.Context;
import android.content.SharedPreferences;

public class DemoConnection extends AbstractConnection {
    DemoConnection(Context ctx) {
        super(ctx, Connection.TYPE_REMOTE, "https://demo.openhab.org:8443/", null, null, null);
    }
}
