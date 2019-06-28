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
import androidx.annotation.VisibleForTesting
import com.here.oksse.OkSse
import com.here.oksse.ServerSentEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpClient constructor(private val client: OkHttpClient, baseUrl: String?, username: String?, password: String?) {
    private val baseUrl: HttpUrl? = if (baseUrl != null) HttpUrl.parse(baseUrl) else null
    @VisibleForTesting val authHeader: String? = if (!username.isNullOrEmpty() && !password.isNullOrEmpty())
        Credentials.basic(username, password, okhttp3.internal.Util.UTF_8) else null

    enum class CachingMode {
        DEFAULT,
        AVOID_CACHE,
        FORCE_CACHE_IF_POSSIBLE
    }

    fun makeSse(url: HttpUrl, listener: ServerSentEvent.Listener): ServerSentEvent {
        val request = makeAuthenticatedRequestBuilder()
            .url(url)
            .build()
        val client = this.client.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        return OkSse(client).newServerSentEvent(request, listener)
    }

    fun buildUrl(url: String): HttpUrl {
        var absoluteUrl = HttpUrl.parse(url)
        if (absoluteUrl == null && baseUrl != null) {
            val actualUrl = if (url.startsWith("/")) url.substring(1) else url
            absoluteUrl = HttpUrl.parse(baseUrl.toString() + actualUrl)
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

    private suspend fun method(
        url: String,
        method: String,
        headers: Map<String, String>?,
        requestBody: String?,
        mediaType: String?,
        timeoutMillis: Long,
        caching: CachingMode
    ) = suspendCancellableCoroutine<HttpResult> { cont ->
        val requestBuilder = makeAuthenticatedRequestBuilder()
            .url(buildUrl(url))
        if (headers != null) {
            for ((key, value) in headers) {
                requestBuilder.addHeader(key, value)
            }
        }
        if (requestBody != null) {
            val actualMediaType = if (mediaType != null) MediaType.parse(mediaType) else null
            requestBuilder.method(method, RequestBody.create(actualMediaType, requestBody))
        }
        when (caching) {
            CachingMode.AVOID_CACHE -> requestBuilder.cacheControl(CacheControl.FORCE_NETWORK)
            CachingMode.FORCE_CACHE_IF_POSSIBLE -> {
                requestBuilder.cacheControl(CacheControl.Builder()
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
                val body = response.body()

                when {
                    !response.isSuccessful -> {
                        cont.resumeWithException(
                            HttpException(call.request(), url, response.message(), response.code()))
                    }
                    body == null -> {
                        cont.resumeWithException(HttpException(call.request(), url, "Empty body", 500))
                    }
                    else -> {
                        cont.resume(HttpResult(call.request(), url, body, response.code(), response.headers()))
                    }
                }
            }
        })
    }

    private fun makeAuthenticatedRequestBuilder(): Request.Builder {
        val builder = Request.Builder()
            .addHeader("User-Agent", "openHAB client for Android")
        if (authHeader != null) {
            builder.addHeader("Authorization", authHeader)
        }
        return builder
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
