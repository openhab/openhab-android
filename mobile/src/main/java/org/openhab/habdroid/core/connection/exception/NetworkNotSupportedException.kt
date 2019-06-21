package org.openhab.habdroid.core.connection.exception

import android.net.NetworkInfo

class NetworkNotSupportedException(val networkInfo: NetworkInfo) : ConnectionException()
