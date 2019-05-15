/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.util

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.IOException

class SyncHttpClient(client: OkHttpClient, baseUrl: String?, username: String?, password: String?) :
        HttpClient(client, baseUrl, username, password) {
    class HttpResult constructor(call: Call) {
        val request: Request
        val response: ResponseBody?
        val error: Throwable?
        val statusCode: Int

        val isSuccessful: Boolean
            get() = error == null

        init {
            var result: ResponseBody? = null
            var error: Throwable? = null
            var code = 500

            try {
                val response = call.execute()
                code = response.code()
                result = response.body()
                if (!response.isSuccessful) {
                    error = IOException(response.code().toString() + ": " + response.message())
                }
            } catch (e: IOException) {
                error = e
            }

            this.statusCode = code
            this.request = call.request()
            this.response = result
            this.error = error
        }

        fun close() {
            response?.close()
        }

        fun asText(): HttpTextResult {
            return HttpTextResult(this)
        }

        fun asStatus(): HttpStatusResult {
            return HttpStatusResult(this)
        }
    }

    class HttpStatusResult internal constructor(result: HttpResult) {
        val error: Throwable? = result.error
        val statusCode: Int = result.statusCode

        val isSuccessful: Boolean
            get() = error == null

        init {
            result.close()
        }
    }

    class HttpTextResult internal constructor(result: HttpResult) {
        val request = result.request
        val statusCode = result.statusCode
        val response: String?
        val error: Throwable?

        val isSuccessful: Boolean
            get() = error == null

        init {
            if (result.response == null) {
                this.response = null
                this.error = result.error
            } else {
                var response: String? = null
                var error = result.error
                try {
                    response = result.response.string()
                } catch (e: IOException) {
                    error = e
                }

                this.response = response
                this.error = error
            }
            result.close()
        }
    }

    operator fun get(url: String): HttpResult {
        return get(url, -1)
    }

    operator fun get(url: String, timeoutMillis: Long): HttpResult {
        return method(url, "GET", null, null, null, timeoutMillis)
    }

    operator fun get(url: String, headers: Map<String, String>? = null): HttpResult {
        return method(url, "GET", headers, null, null, -1)
    }

    fun post(url: String, requestBody: String,
             mediaType: String, headers: Map<String, String>? = null): HttpResult {
        return method(url, "POST", headers, requestBody, mediaType, -1)
    }

    protected fun method(url: String, method: String, headers: Map<String, String>?,
                         requestBody: String?, mediaType: String?, timeoutMillis: Long): HttpResult {
        val call = prepareCall(url, method, headers, requestBody,
                mediaType, timeoutMillis, CachingMode.AVOID_CACHE)
        return HttpResult(call)
    }
}
