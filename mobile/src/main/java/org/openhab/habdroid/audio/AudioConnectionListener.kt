package org.openhab.habdroid.audio

/**
 * Behavior required to process connection notifications from the {@see AudioPoster}
 */
interface AudioConnectionListener {
    /**
     * Invoked on successfully establishing a connection to the target.
     */
    fun connectionSucceeded()

    /**
     * Invoked when a connection to the target could not be successfully established.
     */
    fun connectionFailed()

    /**
     * Invoked when a connection to the target get rejected because it was locked by other connection.
     */
    fun connectionBusy()

    /**
     * Invoked when an established connection is closed.
     */
    fun connectionClosed()
}
