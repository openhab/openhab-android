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
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MyAsyncHttpClient extends MyHttpClient<Call> {

    public MyAsyncHttpClient(Context ctx, Boolean ignoreSSLHostname, Boolean ignoreCertTrust) {
        clientSSLSetup(ctx, ignoreSSLHostname, ignoreCertTrust);
	}

    protected Call method(String url, String method, Map<String, String> addHeaders, String
            requestBody, String mediaType, final MyHttpClient.ResponseHandler responseHandler) {
        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.url(getBaseUrl().newBuilder(url).build());
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestBuilder.addHeader(entry.getKey(), entry.getValue());
        }
        if (addHeaders != null) {
            for (Map.Entry<String, String> entry : addHeaders.entrySet()) {
                requestBuilder.addHeader(entry.getKey(), entry.getValue());
            }
        }
        if (requestBody != null) {
            requestBuilder.method(method, RequestBody.create(MediaType.parse(mediaType), requestBody));
        }
        Call call = client.newCall(requestBuilder.build());
        call.enqueue(new Callback() {
            @Override
            public void onFailure(final Call call, final IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        responseHandler.onFailure(call, 0, new Headers.Builder().build(), null, e);
                    }
                });
            }

            @Override
            public void onResponse(final Call call, Response response) throws IOException {
                final int code = response.code();
                final byte[] body = response.body().bytes();
                final boolean success = response.isSuccessful();
                final Headers headers = response.headers();
                final String message = response.message();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
                            responseHandler.onSuccess(call, code, headers, body);
                        } else {
                            responseHandler.onFailure(call, code, headers, body, new IOException(message));
                        }
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
