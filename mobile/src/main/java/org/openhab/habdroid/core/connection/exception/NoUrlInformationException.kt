package org.openhab.habdroid.core.connection.exception

class NoUrlInformationException(private val local: Boolean) : ConnectionException() {

    fun wouldHaveUsedLocalConnection(): Boolean {
        return local
    }
}
