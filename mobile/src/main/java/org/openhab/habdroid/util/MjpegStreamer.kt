/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.util

import android.graphics.Bitmap
import android.os.AsyncTask
import android.os.Handler
import android.util.Log
import android.widget.ImageView

import org.openhab.habdroid.core.connection.Connection

import java.io.IOException

class MjpegStreamer(view: ImageView, connection: Connection, private val url: String) {
    private val httpClient: SyncHttpClient = connection.syncHttpClient
    private val handler: Handler
    private var downloadImageTask: DownloadImageTask? = null

    init {
        handler = Handler { msg ->
            if (downloadImageTask != null) {
                val bmp = msg.obj as Bitmap
                view.setImageBitmap(bmp)
            }
            false
        }
    }

    fun start() {
        downloadImageTask = DownloadImageTask()
        downloadImageTask?.execute()
    }

    fun stop() {
        downloadImageTask?.cancel(true)
        downloadImageTask = null
    }

    @Throws(IOException::class)
    private fun startStream(): MjpegInputStream {
        val result = httpClient.get(url)
        Log.d(TAG, "MJPEG request finished, status = " + result.statusCode)
        if (result.error != null) {
            throw HttpException(result.statusCode, result.error)
        }
        return MjpegInputStream(result.response!!.byteStream())
    }

    private class HttpException(code: Int, cause: Throwable) : IOException("HTTP failure code $code") {
        init {
            initCause(cause)
        }
    }

    private inner class DownloadImageTask : AsyncTask<Void, Void, Void>() {
        override fun doInBackground(vararg params: Void): Void? {
            while (!isCancelled) {
                try {
                    doStreamOnce()
                } catch (e: IOException) {
                    Log.e(TAG, "MJPEG streaming from $url failed", e)
                    if (e is HttpException) {
                        // no point in continuing if the server returned failure
                        break
                    }
                }

            }
            return null
        }

        @Throws(IOException::class)
        private fun doStreamOnce() {
            startStream().use { stream ->
                while (!isCancelled) {
                    val bitmap = stream.readMjpegFrame()
                    handler.obtainMessage(0, bitmap).sendToTarget()
                }
            }
        }

        override fun onPostExecute(result: Void) {
            downloadImageTask = null
        }
    }

    companion object {
        private val TAG = MjpegStreamer::class.java.simpleName
    }
}