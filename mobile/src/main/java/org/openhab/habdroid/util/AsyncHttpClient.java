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
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class AsyncHttpClient extends HttpClient {
    public interface ResponseHandler<T> {
        T convertBodyInBackground(ResponseBody body) throws IOException; // called in background thread
        void onFailure(Request request, int statusCode, Throwable error);
        void onSuccess(T body, Headers headers);
    }

    public static abstract class StringResponseHandler implements ResponseHandler<String> {
        @Override
        public String convertBodyInBackground(ResponseBody body) throws IOException {
            return body.string();
        }
    }

    private Handler mHandler = new Handler(Looper.getMainLooper());

    public AsyncHttpClient(Context context, String baseUrl) {
        super(context, baseUrl);
    }

    public AsyncHttpClient(Context context, SharedPreferences prefs, String baseUrl) {
        super(context, prefs, baseUrl);
	}

    public <T> Call get(String url, ResponseHandler<T> responseHandler) {
        return get(url, null, responseHandler);
    }

    public <T> Call get(String url, Map<String, String> headers, ResponseHandler<T> responseHandler) {
        return method(url, "GET", headers, null, null, responseHandler);
    }

    public Call post(String url, String requestBody, String mediaType, StringResponseHandler responseHandler) {
        return method(url, "POST", null, requestBody, mediaType, responseHandler);
    }

    private <T> Call method(String url, String method, Map<String, String> headers,
            String requestBody, String mediaType, final ResponseHandler<T> responseHandler) {
        Call call = prepareCall(url, method, headers, requestBody, mediaType);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(final Call call, final IOException e) {
                mHandler.post(() -> {
                    if (!call.isCanceled()) {
                        responseHandler.onFailure(call.request(), 0, e);
                    }
                });
            }

            @Override
            public void onResponse(final Call call, Response response) {
                final ResponseBody body = response.body();
                final int code = response.code();
                T converted = null;
                Throwable conversionError = null;
                if (body != null) {
                    try {
                        converted = responseHandler.convertBodyInBackground(body);
                    } catch (IOException e) {
                        conversionError = e;
                    } finally {
                        body.close();
                    }
                }
                final T result = converted;
                final String message = response.message();
                final Throwable error = response.isSuccessful()
                        ? conversionError : new IOException(message);
                final Headers headers = response.headers();
                mHandler.post(() -> {
                    if (!call.isCanceled()) {
                        if (error != null) {
                            responseHandler.onFailure(call.request(), code, error);
                        } else {
                            responseHandler.onSuccess(result, headers);
                        }
                    }
                });
            }
        });
        return call;
    }
}
