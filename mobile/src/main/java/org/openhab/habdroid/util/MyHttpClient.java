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
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.Headers;
import okhttp3.OkHttpClient;

public abstract class MyHttpClient<T> {

    private static final String TAG = MyHttpClient.class.getSimpleName();

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

    protected void clientSSLSetup(Boolean ignoreSSLHostname, Boolean ignoreCertTrust) {
        if (ignoreSSLHostname) {
            clientBuilder.hostnameVerifier(org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            client = clientBuilder.build();
        }
        if (ignoreCertTrust) {
            X509TrustManager trustAllCertsTrustManager =
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[]{};
                    }
                };

            try {
                final SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[] { trustAllCertsTrustManager }, new java
                        .security.SecureRandom());
                final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
                clientBuilder.sslSocketFactory(sslSocketFactory, trustAllCertsTrustManager);
            } catch (Exception e) {
                Log.d(TAG, "Applying certificate trust settings failed", e);
            }
        }
        client = clientBuilder.build();
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
