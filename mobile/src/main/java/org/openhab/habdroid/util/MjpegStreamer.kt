/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.habdroid.util

import android.util.Log
import android.widget.ImageView
import kotlinx.coroutines.*

import org.openhab.habdroid.core.connection.Connection
import java.io.IOException

class MjpegStreamer(private val view: ImageView, connection: Connection, private val url: String) {
    private val httpClient = connection.httpClient
    private var job: Job? = null

    fun start() {
        val job = Job()
        this.job = job
        doStream(CoroutineScope(Dispatchers.IO + job))
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    @Throws(HttpClient.HttpException::class)
    private suspend fun startStream(): MjpegInputStream {
        val result = httpClient.get(url)
        Log.d(TAG, "MJPEG request finished, status = ${result.statusCode}")
        return MjpegInputStream(result.response.byteStream())
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
            } catch (e: HttpClient.HttpException) {
                Log.e(TAG, "MJPEG streaming from $url failed", e)
                // No point in continuing if the server returned failure
                break
            } catch (e: IOException) {
                Log.e(TAG, "MJPEG streaming from $url was interrupted", e)
            }
        }
    }

    companion object {
        private val TAG = MjpegStreamer::class.java.simpleName
    }
}
