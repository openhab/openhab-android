package org.openhab.habdroid.audio

import android.os.Build
import android.os.Process
import android.util.Log
import org.openhab.habdroid.util.TLSSocketFactory
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.util.Arrays
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory

/**
 * Transmits video doorbell northbound audio
 */
class AudioPoster(
    private val audioQueue: AudioQueue,
    val audioConnectionValue: String,
    private val audioConnectionListener: AudioConnectionListener?
) : Runnable {
    private val encodedSilence: ByteArray
    private var continueSendingAudio: Boolean

    /**
     * Stops the sending of audio samples resulting in termination of the thread
     */
    fun stopSendingAudio() {
        continueSendingAudio = false
    }

    /**
     * Starts the process of sending northbound audio
     */
    override fun run() {
        Log.i(TAG, "run() ++")
        var connectionMade = false
        // place this thread at a higher priority as it is processing real time audio
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        var connection: URLConnection? = null
        var noDataSentCycleCount = 0
        try {
            val url = URL(audioConnectionValue)
            connection = url.openConnection()

            if( connection == null ) {
                return
            }

            //adding TLS 1.1, 1.2 to older versions of droid
            if (connection is HttpsURLConnection) {
                try {
                    val sslContext = SSLContext.getInstance("TLS")
                    sslContext.init(null, null, null)
                    var noSSLv3Factory: SSLSocketFactory? = null
                    if (Build.VERSION.SDK_INT <= 19) {
                        noSSLv3Factory = TLSSocketFactory(sslContext.socketFactory)
                    } else {
                        noSSLv3Factory = sslContext.socketFactory
                    }
                    connection.sslSocketFactory = noSSLv3Factory
                } catch (e: Exception) {
                }
            }
            (connection as HttpURLConnection?)!!.requestMethod = HTTP_METHOD
            connection.setFixedLengthStreamingMode(CONTENT_LENGTH_HEADER_VALUE_IOS_SUCCESS)
            connection.setConnectTimeout(CONNECTION_TIMEOUT_MS)
            connection.setDoInput(true)
            connection.setDoOutput(true)
            val outputStream = connection.getOutputStream()

            // send 500ms of silence to get things started
            outputStream.write(encodedSilence)
            outputStream.flush()
            var successPackets = 0

            // if there is no audio available for posting, ensure we are sending 8KB of silence
            //  every second
            while (true) {
                val audioContentBuffer = audioQueue.poll()

                /*
                always ensure the audio queue is empty prior to determining if we should stop
                    sending audio
                 */if (audioContentBuffer != null) {
                    Log.i(TAG, "run() posting data...")
                    outputStream.write(audioContentBuffer)
                    outputStream.flush()

                    // reset the count of cycles without sending any data
                    noDataSentCycleCount = 0
                } else {
                    if (!continueSendingAudio) {
                        break
                    }
                }
                try {
                    Thread.sleep(SEND_DELAY_ONE_HUNDRED_MS.toLong())
                } catch (e: InterruptedException) {
                    Log.e(
                        TAG,
                        "run(): Encountered an InterruptedException.  Exception says:[$e]"
                    )
                    outputStream.flush()
                    break
                }
                noDataSentCycleCount += 1
                //send silence if we have 1 second of no audie  1 sec = 75X13
                if (noDataSentCycleCount > 13) {
                    Log.i(TAG, "sending silence")
                    // time to send some silence
                    outputStream.write(encodedSilence)
                    outputStream.flush()

                    //send connection success only after we have sent a packet silence sucessfully
                    if (!connectionMade) successPackets++
                    noDataSentCycleCount = 0
                } else {
                    //Log.i(TAG, "completed iteration check - it is not time yet....  " + noDataSentCycleCount);
                }

                //send success after a couple of seconds of silence so we
                //give time for connection to burp
                if (!connectionMade && successPackets > 3) {
                    notifyConnectionListener(ConnectionState.SUCCESS)
                    connectionMade = true
                    successPackets = 0
                }
            }
            Log.i(TAG, "run() exiting audio send loop")
            outputStream.close()
            /*
            int responseCode = connection.getResponseCode();

            // process the response
            BufferedReader responseStream;
            if (responseCode > 199 && responseCode < 300) {
                // success
                responseStream = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            } else {
                // error
                notifyConnectionListener(ConnectionState.FAIL);
                responseStream = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
            }

            String inputLine;
            StringBuilder responsePayload = new StringBuilder();
            while ((inputLine = responseStream.readLine()) != null) {
                responsePayload.append(inputLine);
            }
            responseStream.close();
            System.out.println(responsePayload.toString());
            */
        } catch (e: IOException) {
            val errorMessage = e.toString()

            /*
            try {
                if (connection != null) {

                    BufferedReader responseStream = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream()));

                    String inputLine;
                    StringBuilder responsePayload = new StringBuilder();
                    while ((inputLine = responseStream.readLine()) != null) {
                        responsePayload.append(inputLine);
                    }

                    Log.e(TAG, responsePayload.toString());

                    responseStream.close();
                }
            } catch (IOException e1) {
                Log.e(TAG, e1.toString());
            }
            */

            // this is a terrible way to evaluate why this exception was thrown but LG
            //  requires an artificially large stream size on the Content-Length
            //  which throws an IOException if the stream is closed before
            //  the Content-Length was actually sent
            if (errorMessage.contains("unexpected end of stream")) {
                notifyConnectionListener(ConnectionState.CLOSED)
            } else if (errorMessage.contains("Broken pipe") || errorMessage.contains("403") || errorMessage.contains("499")) {
                notifyConnectionListener(ConnectionState.BUSY)
            } else {
                notifyConnectionListener(ConnectionState.FAIL)
            }
            Log.e(
                TAG,
                "run(): Encountered an IOException.  Exception says:[$e]"
            )
        } finally {
            if (connection != null) {
                Log.d(
                    TAG,
                    "run(): disconnecting the connection from exception handler"
                )
                (connection as HttpURLConnection).disconnect()
            }
        }
        Log.i(TAG, "run() --")
    }

    private fun notifyConnectionListener(state: ConnectionState) {
        if (audioConnectionListener != null) {
            when (state) {
                ConnectionState.SUCCESS -> audioConnectionListener.connectionSucceeded()
                ConnectionState.CLOSED -> audioConnectionListener.connectionClosed()
                ConnectionState.BUSY -> audioConnectionListener.connectionBusy()
                ConnectionState.FAIL -> audioConnectionListener.connectionFailed()
                else -> audioConnectionListener.connectionFailed()
            }
        }
    }

    private enum class ConnectionState {
        SUCCESS, FAIL, BUSY,  // Other device is already connected.
        CLOSED
    }

    companion object {
        private val TAG = AudioPoster::class.java.simpleName
        private const val HTTP_METHOD = "POST"

        // this strange hard coded value is currently required by the LG camera specifications
        //  to be included as the content length value on the camera POST connection
        private const val CONTENT_LENGTH_HEADER_VALUE_IOS_SUCCESS = 9995999

        //decrese delay so queue doesnt back fill waiting to be emptied
        private const val SEND_DELAY_ONE_HUNDRED_MS = 75
        private const val CONNECTION_TIMEOUT_MS = 8000
        private const val ONE_KILO_BYTE = 1024
        private const val SAMPLED_SILENCE = 0xFF.toByte()
    }

    /**
     * Instance initialization with necessary dependencies and data elements
     *
     * @param audioQueue              - monitored queue to which audio to be sent is posted
     * @param audioConnectionValue    - endpoint address audio is sent to (via HTTP POST)
     * @param audioConnectionListener - optional processor to which connection status changes are sent
     *
     *
     * The sendAuthentication value is currently only included for POC development.  Long term requirements
     * may dictate an alternate approach for managing authentication.
     *
     */
    init {

        // LG recommends sending silence in 8KB chunks
        encodedSilence = ByteArray(ONE_KILO_BYTE * 8)
        Arrays.fill(encodedSilence, SAMPLED_SILENCE)
        continueSendingAudio = true
    }
}
