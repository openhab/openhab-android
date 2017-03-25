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
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLSocketFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Credentials;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Route;

public class MyAsyncHttpClient {

    public interface ResponseHandler {
        void onFailure(int statusCode, Headers headers, byte[] responseBody, Throwable error);
        void onSuccess(int statusCode, Headers headers, byte[] responseBody);
    }

    public interface TextResponseHandler {
        void onFailure(int statusCode, Headers headers, String responseBody, Throwable error);
        void onSuccess(int statusCode, Headers headers, String responseBody);
    }

    private Map<String, String> headers = new HashMap<String, String>();
    OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
    OkHttpClient client = clientBuilder.build();

    public MyAsyncHttpClient(Context ctx) {
        if (PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(Constants.PREFERENCE_SSLHOST, false)) {
            clientBuilder.hostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            client = clientBuilder.build();
        }
	}

    public void setBasicAuth(String username, String password) {
        setBasicAuth(username, password, false);
    }

    public void setBasicAuth(final String username, final String password, boolean preemtive) {
        clientBuilder.authenticator(new Authenticator() {
			@Override public Request authenticate(Route route, Response response) throws IOException {
				System.out.println("Authenticating for response: " + response);
				System.out.println("Challenges: " + response.challenges());
				String credential = Credentials.basic(username, password);
				return response.request().newBuilder()
						.header("Authorization", credential)
						.build();
			}
		});
        client = clientBuilder.build();
	}

    public void setTimeout(int timeout) {
        clientBuilder.readTimeout(timeout, TimeUnit.MILLISECONDS);
        client = clientBuilder.build();
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    public Call get(String url, ResponseHandler responseHandler) {
        return method(url, "GET", null, null, null, responseHandler);
    }

    public Call get(String url, TextResponseHandler textResponseHandler) {
        return get(url, getResponseHandler(textResponseHandler));
    }

    public Call get(String url, Map<String, String> headers, ResponseHandler responseHandler) {
        return method(url, "GET", headers, null, null, responseHandler);
    }

    public Call post(String url, String requestBody, String mediaType, ResponseHandler responseHandler) {
        return method(url, "POST", null, requestBody, mediaType, responseHandler);
    }

    public Call post(String url, String requestBody, String mediaType, TextResponseHandler textResponseHandler) {
        return post(url, requestBody, mediaType, getResponseHandler(textResponseHandler));
    }

    public Call delete(String url, ResponseHandler responseHandler) {
        return method(url, "DELETE", null, null, null, responseHandler);
    }

    private Call method(String url, String method, Map<String, String> addHeaders, String requestBody, String mediaType, final ResponseHandler responseHandler) {
        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.url(url);
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
            public void onFailure(Call call, final IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        responseHandler.onFailure(0, new Headers.Builder().build(), null, e);
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final int code = response.code();
                final byte[] body = response.body().bytes();
                final boolean success = response.isSuccessful();
                final Headers headers = response.headers();
                final String message = response.message();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
                            responseHandler.onSuccess(code, headers, body);
                        } else {
                            responseHandler.onFailure(code, headers, body, new IOException(message));
                        }
                    }
                });
            }
        });
        return call;
    }

    @NonNull
    private ResponseHandler getResponseHandler(final TextResponseHandler textResponseHandler) {
        return new ResponseHandler() {
            @Override
            public void onFailure(int statusCode, Headers headers, byte[] responseBody, Throwable error) {
                try {
                    textResponseHandler.onFailure(statusCode, headers, new String(responseBody, "UTF-8"), error);
                } catch (UnsupportedEncodingException e) {
                    textResponseHandler.onFailure(statusCode, headers, null, e);
                }
            }

            @Override
            public void onSuccess(int statusCode, Headers headers, byte[] responseBody) {
                try {
                    textResponseHandler.onSuccess(statusCode, headers, new String(responseBody, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    textResponseHandler.onFailure(statusCode, headers, null, e);
                }
            }
        };
    }

    private void runOnUiThread(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }


}
