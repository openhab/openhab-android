/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public abstract class MyHttpClient<T> {
    private static final String TAG = MyHttpClient.class.getSimpleName();

    private HttpUrl baseUrl;

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

    protected void clientSSLSetup(Context ctx, Boolean ignoreSSLHostname, Boolean ignoreCertTrust) {
        if (ignoreSSLHostname) {
            clientBuilder.hostnameVerifier(new HostnameVerifier() {
                @SuppressLint("BadHostnameVerifier")
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
        }

        X509TrustManager x509TrustManager = null;

        if (ignoreCertTrust) {
            x509TrustManager =
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
        } else {
            // get default trust manager
            try {
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init((KeyStore)null);
                TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

                for (TrustManager trustManager : trustManagers) {
                    if (trustManager instanceof X509TrustManager) {
                        x509TrustManager = (X509TrustManager) trustManager;
                        break;
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "Getting default trust manager failed", e);
            }
        }

        try {
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init( MyKeyManager.getInstance(ctx), new TrustManager[]{x509TrustManager}, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
                clientBuilder.sslSocketFactory(sslSocketFactory, x509TrustManager);
        } catch (Exception e) {
            Log.d(TAG, "Applying certificate trust settings failed", e);
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

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = HttpUrl.parse(baseUrl);
    }

    protected HttpUrl getBaseUrl() {
        if (baseUrl == null) {
            throw new IllegalStateException("No baseUrl was set so far.");
        }
        return this.baseUrl;
    }

    public void setTimeout(int timeout) {
        clientBuilder.readTimeout(timeout, TimeUnit.MILLISECONDS);
        client = clientBuilder.build();
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    public void removeHeader(String key) {
        headers.remove(key);
    }

    @VisibleForTesting
    public Map<String, String> getHeaders() {
        return headers;
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
