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
import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MySyncHttpClient extends MyHttpClient<Response> {

    public MySyncHttpClient(Context ctx, Boolean ignoreSSLHostname, Boolean ignoreCertTrust) {
        clientSSLSetup(ctx, ignoreSSLHostname, ignoreCertTrust);
    }

    protected Response method(String url, String method, Map<String, String> addHeaders, String
            requestBody, String mediaType, final ResponseHandler responseHandler) {
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
        Request request = requestBuilder.build();
        Call call = client.newCall(request);
        try {
            Response resp = call.execute();
            if (resp.isSuccessful()) {
                responseHandler.onSuccess(call, resp.code(), resp.headers(), resp.body().bytes());
            } else {
                responseHandler.onFailure(call, resp.code(), resp.headers(), resp.body().bytes(),
                        new IOException(resp.code() + ": " + resp.message()));
            }
            return resp;
        } catch(IOException ex) {
            responseHandler.onFailure(call, 0, new Headers.Builder().build(), null, ex);
            return new Response
                    .Builder()
                    .code(500)
                    .message(ex.getClass().getName() + ": " + ex.getMessage())
                    .request(request)
                    .protocol(Protocol.HTTP_1_0)
                    .build();
        }
    }

    @NonNull
    private ResponseHandler getResponseHandler(final TextResponseHandler textResponseHandler) {
        return new ResponseHandler() {
            @Override
            public void onFailure(Call call, int statusCode, Headers headers, byte[] responseBody, Throwable error) {
                try {
                    String responseString = responseBody == null ? null : new String(responseBody, "UTF-8");
                    textResponseHandler.onFailure(call, statusCode, headers, responseString, error);
                } catch (UnsupportedEncodingException e) {
                    textResponseHandler.onFailure(call, statusCode, headers, null, e);
                }
            }

            @Override
            public void onSuccess(Call call, int statusCode, Headers headers, byte[] responseBody) {
                try {
                    String responseString = responseBody == null ? null : new String(responseBody, "UTF-8");
                    textResponseHandler.onSuccess(call, statusCode, headers, responseString);
                } catch (UnsupportedEncodingException e) {
                    textResponseHandler.onFailure(call, statusCode, headers, null, e);
                }
            }
        };
    }

    private void runOnUiThread(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }


}
