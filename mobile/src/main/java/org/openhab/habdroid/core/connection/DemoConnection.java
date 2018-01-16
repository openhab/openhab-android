package org.openhab.habdroid.core.connection;

import android.content.Context;
import android.content.SharedPreferences;

public class DemoConnection extends AbstractConnection {
    DemoConnection(Context ctx, SharedPreferences settings) {
        super(ctx, settings, Connection.TYPE_REMOTE, null, null, "https://demo.openhab.org:8443/");
    }
}
