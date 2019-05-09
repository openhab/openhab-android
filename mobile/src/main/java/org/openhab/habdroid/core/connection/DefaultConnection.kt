package org.openhab.habdroid.core.connection

import okhttp3.OkHttpClient

open class DefaultConnection : AbstractConnection {
    internal constructor(httpClient: OkHttpClient, connectionType: Int,
                         baseUrl: String, username: String?, password: String?) :
            super(httpClient, connectionType, baseUrl, username, password) {}

    internal constructor(baseConnection: AbstractConnection, connectionType: Int) :
            super(baseConnection, connectionType) {}
}
