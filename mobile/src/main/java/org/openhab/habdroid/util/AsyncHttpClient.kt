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
import android.os.Handler
import android.os.Looper
import okhttp3.*
import java.io.IOException

class AsyncHttpClient(client: OkHttpClient, baseUrl: String?, username: String?, password: String?) :
        HttpClient(client, baseUrl, username, password) {

    private val handler = Handler(Looper.getMainLooper())

    interface ResponseHandler<T> {
        // called in background thread
        @Throws(IOException::class)
        fun convertBodyInBackground(body: ResponseBody): T

        fun onFailure(request: Request, statusCode: Int, error: Throwable)
        fun onSuccess(response: T, headers: Headers)
    }

    abstract class StringResponseHandler : ResponseHandler<String> {
        @Throws(IOException::class)
        override fun convertBodyInBackground(body: ResponseBody): String {
            return body.string()
        }
    }

    abstract class BitmapResponseHandler(private val targetSize: Int,
                                         private val enforceSize: Boolean = false) : ResponseHandler<Bitmap> {
        @Throws(IOException::class)
        override fun convertBodyInBackground(body: ResponseBody): Bitmap {
            return body.toBitmap(targetSize, enforceSize)
        }
    }

    operator fun <T> get(url: String, responseHandler: ResponseHandler<T>): Call {
        return method(url, "GET", null, null, null,
                DEFAULT_TIMEOUT_MS, CachingMode.AVOID_CACHE, responseHandler)
    }

    operator fun <T> get(url: String, headers: Map<String, String>,
                         responseHandler: ResponseHandler<T>): Call {
        return method(url, "GET", headers, null, null,
                DEFAULT_TIMEOUT_MS, CachingMode.AVOID_CACHE, responseHandler)
    }

    operator fun <T> get(url: String, headers: Map<String, String>,
                         timeoutMillis: Long, responseHandler: ResponseHandler<T>): Call {
        return method(url, "GET", headers, null, null,
                timeoutMillis, CachingMode.AVOID_CACHE, responseHandler)
    }

    operator fun <T> get(url: String, timeoutMillis: Long, caching: CachingMode,
                         responseHandler: ResponseHandler<T>): Call {
        return method(url, "GET", null, null, null, timeoutMillis, caching, responseHandler)
    }

    fun post(url: String, requestBody: String, mediaType: String,
             responseHandler: StringResponseHandler): Call {
        return method(url, "POST", null, requestBody,
                mediaType, DEFAULT_TIMEOUT_MS, CachingMode.AVOID_CACHE, responseHandler)
    }

    private fun <T> method(url: String, method: String, headers: Map<String, String>?,
                           requestBody: String?, mediaType: String?, timeoutMillis: Long,
                           caching: CachingMode, responseHandler: ResponseHandler<T>): Call {
        val call = prepareCall(url, method, headers, requestBody, mediaType, timeoutMillis, caching)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post {
                    if (!call.isCanceled) {
                        responseHandler.onFailure(call.request(), 0, e)
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body()
                val code = response.code()
                var converted: T? = null
                var conversionError: Throwable? = null
                if (body != null) {
                    try {
                        converted = responseHandler.convertBodyInBackground(body)
                    } catch (e: IOException) {
                        conversionError = e
                    } finally {
                        body.close()
                    }
                }
                val message = response.message()
                val error = if (response.isSuccessful) conversionError else IOException(message)
                val responseHeaders = response.headers()

                handler.post {
                    if (!call.isCanceled) {
                        if (error != null) {
                            responseHandler.onFailure(call.request(), code, error)
                        } else {
                            // cast is safe (convertBodyInBackground always returns T, if it failed error is != null)
                            @Suppress("UNCHECKED_CAST")
                            responseHandler.onSuccess(converted as T, responseHeaders)
                        }
                    }
                }
            }
        })
        return call
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 30000
    }
}
