package org.openhab.habdroid.audio

import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Threadsafe queue of sampled audio.
 * of silence
 */
class AudioQueue {
    private val audioSampleQueue: ConcurrentLinkedQueue<ByteArray>

    /**
     * Adds audio sample to the current queue
     *
     * @param audioSample - audio sample to be added to the queue
     * @return - true if the sample was successfully added to the queue, otherwise false
     */
    fun offer(audioSample: ByteArray): Boolean {

        //LLog.v(TAG, "offer()++" + audioSampleQueue.size());
        //LLog.v(TAG, "offer() returning:[" + (offerResult ? "true" : "false") + "]");
        //LLog.v(TAG, "offer()-- - audioSample received");
        return audioSampleQueue.offer(audioSample)
    }

    /**
     * Provides the next available audio sample
     *
     * @return - next available audio sample, otherwise null
     */
    fun poll(): ByteArray {

        //LLog.v(TAG, "poll()++" );
        //LLog.v(TAG, "poll() - " + (audioSample == null ? "no " : "") + "samples found");
        //LLog.v(TAG, "poll()--");
        return audioSampleQueue.poll()
    }

    /**
     * Provides the size of the current queue size
     *
     * @return - number of audio samples in the queue
     */
    val queueSize: Int
        get() {
            Log.v(TAG, "getQueueSize()++")
            val queueSize = audioSampleQueue.size
            Log.v(TAG, "getQueueSize() returning:[$queueSize]")
            Log.v(TAG, "getQueueSize()--")
            return queueSize
        }

    companion object {
        private val TAG = AudioQueue::class.java.simpleName
    }

    init {
        audioSampleQueue = ConcurrentLinkedQueue()
    }
}
