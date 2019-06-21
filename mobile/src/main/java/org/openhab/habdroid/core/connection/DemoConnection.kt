package org.openhab.habdroid.core.connection

import okhttp3.OkHttpClient

class DemoConnection internal constructor(httpClient: OkHttpClient) :
        AbstractConnection(httpClient, Connection.TYPE_REMOTE, "https://demo.openhab.org:8443/", null, null)
