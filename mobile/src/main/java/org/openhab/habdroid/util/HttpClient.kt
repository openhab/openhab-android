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

import android.graphics.Bitmap
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpClient constructor(client: OkHttpClient, baseUrl: String?, username: String?, password: String?) {
    private val client: OkHttpClient
    private val baseUrl: HttpUrl? = baseUrl?.toHttpUrlOrNull()
    @VisibleForTesting val authHeader: String? = if (!username.isNullOrEmpty() && !password.isNullOrEmpty())
        Credentials.basic(username, password, StandardCharsets.UTF_8) else null

    init {
        val clientBuilder = client.newBuilder()
        if (authHeader != null) {
            // Forcibly put authorization info into request, as redirect/retry interceptor might have removed it
            clientBuilder.addNetworkInterceptor { chain ->
                val request = chain.request()
                    .newBuilder()
                    .addHeader("Authorization", authHeader)
                    .build()
                chain.proceed(request)
            }
        }
        this.client = clientBuilder.build()
    }
    enum class CachingMode {
        DEFAULT,
        AVOID_CACHE,
        FORCE_CACHE_IF_POSSIBLE
    }

    fun makeSse(url: HttpUrl, listener: EventSourceListener): EventSource {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "openHAB client for Android")
            .build()
        val client = this.client.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        return EventSources.createFactory(client).newEventSource(request, listener)
    }

    fun buildUrl(url: String): HttpUrl {
        var absoluteUrl = url.toHttpUrlOrNull()
        if (absoluteUrl == null && baseUrl != null) {
            val actualUrl = if (url.startsWith("/")) url.substring(1) else url
            absoluteUrl = (baseUrl.toString() + actualUrl).toHttpUrlOrNull()
        }
        if (absoluteUrl == null) {
            throw IllegalArgumentException("URL '$url' is invalid")
        }
        return absoluteUrl
    }

    @Throws(HttpException::class)
    suspend fun get(
        url: String,
        headers: Map<String, String>? = null,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
        caching: CachingMode = CachingMode.AVOID_CACHE
    ): HttpResult {
        return method(url, "GET", headers, null, null, timeoutMillis, caching)
    }

    @Throws(HttpException::class)
    suspend fun post(
        url: String,
        requestBody: String,
        mediaType: String,
        headers: Map<String, String>? = null
    ): HttpResult {
        return method(url, "POST", headers, requestBody,
            mediaType, DEFAULT_TIMEOUT_MS, CachingMode.AVOID_CACHE)
    }

    @Throws(HttpException::class)
    suspend fun put(
        url: String,
        requestBody: String,
        mediaType: String,
        headers: Map<String, String>? = null
    ): HttpResult {
        return method(url, "PUT", headers, requestBody,
            mediaType, DEFAULT_TIMEOUT_MS, CachingMode.AVOID_CACHE)
    }

    private suspend fun method(
        url: String,
        method: String,
        headers: Map<String, String>?,
        requestBody: String?,
        mediaType: String?,
        timeoutMillis: Long,
        caching: CachingMode
    ) = suspendCancellableCoroutine<HttpResult> { cont ->
        val requestBuilder = Request.Builder()
            .url(buildUrl(url))
            .addHeader("User-Agent", "openHAB client for Android")
        if (headers != null) {
            for ((key, value) in headers) {
                requestBuilder.addHeader(key, value)
            }
        }
        if (requestBody != null) {
            val actualMediaType = mediaType?.toMediaTypeOrNull()
            requestBuilder.method(method, requestBody.toRequestBody(actualMediaType))
        }
        when (caching) {
            CachingMode.AVOID_CACHE -> requestBuilder.cacheControl(CacheControl.FORCE_NETWORK)
            CachingMode.FORCE_CACHE_IF_POSSIBLE -> {
                requestBuilder.cacheControl(
                    CacheControl.Builder()
                    .maxStale(Integer.MAX_VALUE, TimeUnit.SECONDS)
                    .build())
            }
            else -> {}
        }
        val request = requestBuilder.build()
        val call = if (timeoutMillis > 0)
            client.newBuilder().readTimeout(timeoutMillis, TimeUnit.MILLISECONDS).build().newCall(request)
        else
            client.newCall(request)

        cont.invokeOnCancellation { call.cancel() }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(HttpException(call.request(), url, e))
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body

                when {
                    !response.isSuccessful -> {
                        body?.close()
                        cont.resumeWithException(
                            HttpException(call.request(), url, response.message, response.code))
                    }
                    body == null -> {
                        cont.resumeWithException(HttpException(call.request(), url, "Empty body", 500))
                    }
                    else -> {
                        cont.resume(HttpResult(call.request(), url, body, response.code, response.headers))
                    }
                }
            }
        })
    }

    class HttpResult internal constructor(
        val request: Request,
        val originalUrl: String,
        val response: ResponseBody,
        val statusCode: Int,
        val headers: Headers
    ) {
        suspend fun close() = withContext(Dispatchers.IO) {
            response.close()
        }

        @Throws(HttpException::class)
        suspend fun asText(): HttpTextResult = try {
            val text = withContext(Dispatchers.IO) { response.string() }
            HttpTextResult(request, text, headers)
        } catch (e: IOException) {
            throw HttpException(request, originalUrl, e)
        } finally {
            close()
        }

        suspend fun asStatus(): HttpStatusResult {
            close()
            return HttpStatusResult(request, statusCode)
        }

        @Throws(HttpException::class)
        suspend fun asBitmap(sizeInPixels: Int, enforceSize: Boolean = false): HttpBitmapResult = try {
            val bitmap = withContext(Dispatchers.IO) { response.toBitmap(sizeInPixels, enforceSize) }
            HttpBitmapResult(request, bitmap)
        } catch (e: IOException) {
            throw HttpException(request, originalUrl, e)
        } finally {
            close()
        }
    }

    class HttpStatusResult internal constructor(val request: Request, val statusCode: Int)
    class HttpTextResult internal constructor(val request: Request, val response: String, val headers: Headers)
    class HttpBitmapResult internal constructor(val request: Request, val response: Bitmap)

    class HttpException : Exception {
        val request: Request
        val originalUrl: String
        val statusCode: Int

        constructor(request: Request, originalUrl: String, cause: IOException) : super(cause) {
            this.request = request
            this.originalUrl = originalUrl
            statusCode = 500
        }

        constructor(request: Request, originalUrl: String, message: String, statusCode: Int) : super(message) {
            this.request = request
            this.originalUrl = originalUrl
            this.statusCode = statusCode
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 30000
    }
}
