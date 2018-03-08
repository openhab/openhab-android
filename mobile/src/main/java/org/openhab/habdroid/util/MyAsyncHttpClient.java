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

public class MyAsyncHttpClient extends MyHttpClient {
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

    public MyAsyncHttpClient(Context context) {
        super(context);
    }

    public MyAsyncHttpClient(Context context, SharedPreferences prefs) {
        super(context, prefs);
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
                if (!call.isCanceled()) {
                    runOnUiThread(() -> responseHandler.onFailure(call.request(), 0, e));
                }
            }

            @Override
            public void onResponse(final Call call, Response response) throws IOException {
                if (call.isCanceled()) {
                    return;
                }
                final int code = response.code();
                final T result = responseHandler.convertBodyInBackground(response.body());
                final boolean success = response.isSuccessful();
                final Headers headers = response.headers();
                final String message = response.message();
                runOnUiThread(() -> {
                    if (success) {
                        responseHandler.onSuccess(result, headers);
                    } else {
                        responseHandler.onFailure(call.request(), code, new IOException(message));
                    }
                });
            }
        });
        return call;
    }

    private void runOnUiThread(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }
}
