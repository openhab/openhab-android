/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.util;

import android.support.annotation.VisibleForTesting;

import com.here.oksse.OkSse;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public abstract class HttpClient {
    public enum CachingMode {
        DEFAULT,
        AVOID_CACHE,
        FORCE_CACHE_IF_POSSIBLE
    }

    private final HttpUrl mBaseUrl;
    private final OkHttpClient mClient;
    @VisibleForTesting public final String mAuthHeader;

    protected HttpClient(OkHttpClient client, String baseUrl, String username, String password) {
        mBaseUrl = baseUrl != null ? HttpUrl.parse(baseUrl) : null;
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            mAuthHeader = Credentials.basic(username, password);
        } else {
            mAuthHeader = null;
        }
        mClient = client;
    }

    public OkSse makeSseClient() {
        return new OkSse(mClient);
    }

    public HttpUrl buildUrl(String url) {
        HttpUrl absoluteUrl = HttpUrl.parse(url);
        if (absoluteUrl == null && mBaseUrl != null) {
            if (url.startsWith("/")) {
                url = url.substring(1);
            }
            absoluteUrl = HttpUrl.parse(mBaseUrl.toString() + url);
        }
        if (absoluteUrl == null) {
            throw new IllegalArgumentException("URL '" + url + "' is invalid");
        }
        return absoluteUrl;
    }

    protected Call prepareCall(String url, String method, Map<String, String> additionalHeaders,
            String requestBody, String mediaType,
            long timeoutMillis, CachingMode caching) {
        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.url(buildUrl(url));
        requestBuilder.addHeader("User-Agent", "openHAB client for Android");
        if (mAuthHeader != null) {
            requestBuilder.addHeader("Authorization", mAuthHeader);
        }
        if (additionalHeaders != null) {
            for (Map.Entry<String, String> entry : additionalHeaders.entrySet()) {
                requestBuilder.addHeader(entry.getKey(), entry.getValue());
            }
        }
        if (requestBody != null) {
            requestBuilder.method(method, RequestBody.create(MediaType.parse(mediaType), requestBody));
        }
        switch (caching) {
            case AVOID_CACHE:
                requestBuilder.cacheControl(CacheControl.FORCE_NETWORK);
                break;
            case FORCE_CACHE_IF_POSSIBLE:
                requestBuilder.cacheControl(new CacheControl.Builder()
                        .maxStale(Integer.MAX_VALUE, TimeUnit.SECONDS)
                        .build());
                break;
            default:
                break;
        }
        Request request = requestBuilder.build();
        final OkHttpClient client = timeoutMillis > 0
                ? mClient.newBuilder().readTimeout(timeoutMillis, TimeUnit.MILLISECONDS).build()
                : mClient;
        return client.newCall(request);
    }
}
