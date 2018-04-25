/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class SyncHttpClient extends HttpClient {
    public static class HttpResult {
        public final Request request;
        public final ResponseBody response;
        public final Throwable error;
        public final int statusCode;

        private HttpResult(Call call) {
            ResponseBody result = null;
            Throwable error = null;
            int code = 500;

            try {
                Response response = call.execute();
                code = response.code();
                result = response.body();
                if (!response.isSuccessful()) {
                    error = new IOException(response.code() + ": " + response.message());
                }
            } catch (IOException e) {
                error = e;
            }

            this.statusCode = code;
            this.request = call.request();
            this.response = result;
            this.error = error;
        }

        public boolean isSuccessful() {
            return error == null;
        }
        public void close() {
            if (response != null) {
                response.close();
            }
        }

        public HttpTextResult asText() {
            return new HttpTextResult(this);
        }
        public HttpStatusResult asStatus() {
            return new HttpStatusResult(this);
        }
    }

    public static class HttpStatusResult {
        public final Throwable error;
        public final int statusCode;

        HttpStatusResult(HttpResult result) {
            this.error = result.error;
            this.statusCode = result.statusCode;
            result.close();
        }

        public boolean isSuccessful() {
            return error == null;
        }
    }

    public static class HttpTextResult {
        public final Request request;
        public final String response;
        public final Throwable error;
        public final int statusCode;

        HttpTextResult(HttpResult result) {
            this.request = result.request;
            this.statusCode = result.statusCode;
            if (result.response == null) {
                this.response = null;
                this.error = result.error;
            } else {
                String response = null;
                Throwable error = result.error;
                try {
                    response = result.response.string();
                } catch (IOException e) {
                    error = e;
                }
                this.response = response;
                this.error = error;
            }
            result.close();
        }

        public boolean isSuccessful() {
            return error == null;
        }
    }

    public SyncHttpClient(Context context, String baseUrl) {
        super(context, baseUrl);
    }

    public SyncHttpClient(Context context, SharedPreferences prefs, String baseUrl) {
        super(context, prefs, baseUrl);
    }

    public HttpResult get(String url) {
        return get(url, null);
    }

    public HttpResult get(String url, Map<String, String> headers) {
        return method(url, "GET", headers, null, null);
    }

    public HttpResult post(String url, String requestBody, String mediaType) {
        return method(url, "POST", null, requestBody, mediaType);
    }

    protected HttpResult method(String url, String method, Map<String, String> headers,
            String requestBody, String mediaType) {
        return new HttpResult(
                prepareCall(url, method, headers, requestBody, mediaType, CachingMode.AVOID_CACHE));
    }
}
