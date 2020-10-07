package org.openhab.habdroid.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.os.Process
import android.util.Log
import org.openhab.habdroid.codecs.Codec
import org.openhab.habdroid.codecs.CodecChannels
import org.openhab.habdroid.codecs.CodecFactory
import java.util.Arrays

/**
 * provides functionality for sampling audio from device mic on a separate thread
 */
class AudioSampler(audioQueue: AudioQueue) : Runnable {
    private val audioRecord: AudioRecord
    private val audioQueue: AudioQueue
    private val sampledSilence: ByteArray
    private var audioSamplingListener: AudioSamplingListener? = null
    private val canceler: AcousticEchoCanceler? = null
    private val minBufferSize: Int
    private var isSampling = false

    fun setAudioSamplingListener(audioSamplingListener: AudioSamplingListener?) {
        this.audioSamplingListener = audioSamplingListener
    }

    /**
     * starts the sampling process
     */
    override fun run() {
        Log.v(TAG, "run() ++")
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        isSampling = true
        audioRecord.startRecording()
        val buffer = ShortArray(minBufferSize * 2)
        val codec: Codec = CodecFactory.getCodec(CodecChannels.VideoDoorBellNBAudio)

        // place 250 ms of silence in the queue to establish the connection as quickly as possible
        audioQueue.offer(sampledSilence)
        try {
            while (isSampling) {
                // pull the PWM samples
                val bufferedCount = audioRecord.read(buffer, 0, buffer.size)
                Log.d(TAG, "Buffer Size: $bufferedCount")

                // encode the sample. Note that the third parameter is an index, thus -1 from the count.
                val encodedSample: ByteArray = codec.encode(buffer, 0, bufferedCount - 1)
                if (isSampling) {
                    // put the sample into the audio queue
                    audioQueue.offer(encodedSample)
                }
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, e.message.toString())
            if (audioSamplingListener != null) {
                audioSamplingListener!!.onAudioSamplingError(e)
            }
        } finally {
            Log.v(TAG, "run() sampling stopping ...")
            audioRecord.release() // Calling release() calls stop() as well.
            audioSamplingListener = null
        }
        Log.v(TAG, "run() --")
    }

    /**
     * stops the sampling process resulting in thread completion
     */
    fun stopSampling() {
        Log.v(TAG, "stopSampling() ++")
        isSampling = false
        Log.v(TAG, "stopSampling() --")
    }

    companion object {
        private val TAG = AudioSampler::class.java.simpleName
        private const val SAMPLED_SILENCE_BYTE_VALUE = 0xFF.toByte()
        private const val SILENCE_ARRAY_START_ELEMENT = 0
        private const val SAMPLE_RATE = 8000
        private const val SILENCE_ARRAY_UPPER_BOUND = SAMPLE_RATE / 4
    }

    /**
     * Constructs a new instance
     *
     * @param audioQueue - the queue where sampled audio should be placed
     */
    init {
        Log.v(TAG, "AudioSampler(audioQueue=$audioQueue)")
        this.audioQueue = audioQueue
        minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        Log.v(TAG, "Calculated minBufferSize=[$minBufferSize]")
        sampledSilence = ByteArray(SILENCE_ARRAY_UPPER_BOUND)
        Arrays.fill(
            sampledSilence,
            SILENCE_ARRAY_START_ELEMENT,
            SILENCE_ARRAY_UPPER_BOUND - 1,
            SAMPLED_SILENCE_BYTE_VALUE
        )
        audioRecord = AudioRecord( //DLUI  6248 change to ECHO canceling based on headset no on
            //Utilities.getInstance().readIntPreferences(DLAppConstants.HEADSET, DigitalLifeApplication.getAppContext()),
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 2
        )
        Log.v(TAG, "audioRecord.getState()=" + audioRecord.state)
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            val illegalStateException =
                IllegalStateException("Unable to initialize the voice recorder.")
            Log.e(TAG, illegalStateException.message.toString())
            Log.v(TAG, "AudioSampler() - constructor done. Throwing exception")
            throw illegalStateException
        }
        /*
        if(AcousticEchoCanceler.isAvailable()){
            canceler =  AcousticEchoCanceler.create(audioRecord.getAudioSessionId());
            canceler.setEnabled(true);
        }
        if (NoiseSuppressor.isAvailable()){
            NoiseSuppressor supp =  NoiseSuppressor.create(audioRecord.getAudioSessionId());
            supp.setEnabled(true);
        }*/if (AutomaticGainControl.isAvailable()) {
            //AutomaticGainControl gain =  AutomaticGainControl.create(audioRecord.getAudioSessionId());
            //gain.setEnabled(true);
        }
        Log.v(TAG, "AudioSampler()- constructor done")
    }
}
