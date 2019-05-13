/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.util

import androidx.annotation.VisibleForTesting

import com.here.oksse.OkSse
import com.here.oksse.ServerSentEvent

import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit

abstract class HttpClient protected constructor(private val client: OkHttpClient, baseUrl: String?, username: String?, password: String?) {
    private val baseUrl: HttpUrl?
    @VisibleForTesting val authHeader: String?

    enum class CachingMode {
        DEFAULT,
        AVOID_CACHE,
        FORCE_CACHE_IF_POSSIBLE
    }

    init {
        this.baseUrl = if (baseUrl != null) HttpUrl.parse(baseUrl) else null
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            authHeader = Credentials.basic(username, password)
        } else {
            authHeader = null
        }
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

    protected fun prepareCall(url: String, method: String, additionalHeaders: Map<String, String>?,
                              requestBody: String?, mediaType: String?,
                              timeoutMillis: Long, caching: CachingMode): Call {
        val requestBuilder = makeAuthenticatedRequestBuilder()
                .url(buildUrl(url))
        if (additionalHeaders != null) {
            for ((key, value) in additionalHeaders) {
                requestBuilder.addHeader(key, value)
            }
        }
        if (requestBody != null) {
            val actualMediaType = if (mediaType != null) MediaType.parse(mediaType) else null
            requestBuilder.method(method, RequestBody.create(actualMediaType, requestBody))
        }
        when (caching) {
            HttpClient.CachingMode.AVOID_CACHE -> requestBuilder.cacheControl(CacheControl.FORCE_NETWORK)
            HttpClient.CachingMode.FORCE_CACHE_IF_POSSIBLE -> {
                requestBuilder.cacheControl(CacheControl.Builder()
                        .maxStale(Integer.MAX_VALUE, TimeUnit.SECONDS)
                        .build())
            }
            else -> {}
        }
        val request = requestBuilder.build()
        val actualClient = if (timeoutMillis > 0)
            client.newBuilder().readTimeout(timeoutMillis, TimeUnit.MILLISECONDS).build()
        else
            client
        return actualClient.newCall(request)
    }

    private fun makeAuthenticatedRequestBuilder(): Request.Builder {
        val builder = Request.Builder()
                .addHeader("User-Agent", "openHAB client for Android")
        if (authHeader != null) {
            builder.addHeader("Authorization", authHeader)
        }
        return builder
    }
}
