/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.util;

import android.support.annotation.NonNull;

import org.apache.http.conn.ssl.SSLSocketFactory;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.Headers;
import okhttp3.OkHttpClient;

public abstract class MyHttpClient<T> {

    public interface ResponseHandler {
        void onFailure(Call call, int statusCode, Headers headers, byte[] responseBody, Throwable error);
        void onSuccess(Call call, int statusCode, Headers headers, byte[] responseBody);
    }

    public interface TextResponseHandler {
        void onFailure(Call call, int statusCode, Headers headers, String responseBody, Throwable error);
        void onSuccess(Call call, int statusCode, Headers headers, String responseBody);
    }

    protected Map<String, String> headers = new HashMap<String, String>();
    protected OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
    protected OkHttpClient client = clientBuilder.build();

    protected void clientSSLSetup(Boolean ignoreSSLHostname) {
        if (ignoreSSLHostname) {
            clientBuilder.hostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            client = clientBuilder.build();
        }
    }

    public void setBasicAuth(String username, String password) {
        setBasicAuth(username, password, false);
    }

    public void setBasicAuth(final String username, final String password, boolean preemtive) {
        String credential = Credentials.basic(username, password);
        headers.put("Authorization", credential);
    }

    public void setTimeout(int timeout) {
        clientBuilder.readTimeout(timeout, TimeUnit.MILLISECONDS);
        client = clientBuilder.build();
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    public T get(String url, ResponseHandler responseHandler) {
        return method(url, "GET", null, null, null, responseHandler);
    }

    public T get(String url, TextResponseHandler textResponseHandler) {
        return get(url, getResponseHandler(textResponseHandler));
    }

    public T get(String url, Map<String, String> headers, ResponseHandler responseHandler) {
        return method(url, "GET", headers, null, null, responseHandler);
    }

    public T post(String url, String requestBody, String mediaType, ResponseHandler responseHandler) {
        return method(url, "POST", null, requestBody, mediaType, responseHandler);
    }

    public T post(String url, String requestBody, String mediaType, TextResponseHandler textResponseHandler) {
        return post(url, requestBody, mediaType, getResponseHandler(textResponseHandler));
    }

    public T delete(String url, ResponseHandler responseHandler) {
        return method(url, "DELETE", null, null, null, responseHandler);
    }

    protected abstract T method(String url, String method, Map<String, String> addHeaders,
                                   String requestBody, String mediaType, final ResponseHandler
                                           responseHandler);

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
}
