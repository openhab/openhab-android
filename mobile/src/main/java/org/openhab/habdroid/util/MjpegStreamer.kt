/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.util

import android.util.Log
import android.widget.ImageView
import kotlinx.coroutines.*

import org.openhab.habdroid.core.connection.Connection

import java.io.IOException

class MjpegStreamer(private val view: ImageView, connection: Connection, private val url: String) {
    private val httpClient = connection.syncHttpClient
    private var job: Job? = null

    fun start() {
        job = Job()
        doStream(CoroutineScope(Dispatchers.IO + job!!))
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    @Throws(IOException::class)
    private fun startStream(): MjpegInputStream {
        val result = httpClient.get(url)
        Log.d(TAG, "MJPEG request finished, status = ${result.statusCode}")
        if (result.error != null) {
            throw HttpException(result.statusCode, result.error)
        }
        return MjpegInputStream(result.response!!.byteStream())
    }

    private fun doStream(scope: CoroutineScope) = scope.launch {
        while (isActive) {
            try {
                startStream().use { stream ->
                    while (isActive) {
                        val bitmap = stream.readMjpegFrame()
                        if (bitmap != null && isActive) {
                            withContext(Dispatchers.Main) {
                                view.setImageBitmap(bitmap)
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "MJPEG streaming from $url failed", e)
                if (e is HttpException) {
                    // no point in continuing if the server returned failure
                    break
                }
            }
        }
    }

    private class HttpException(code: Int, cause: Throwable) : IOException("HTTP failure code $code") {
        init {
            initCause(cause)
        }
    }

    companion object {
        private val TAG = MjpegStreamer::class.java.simpleName
    }
}