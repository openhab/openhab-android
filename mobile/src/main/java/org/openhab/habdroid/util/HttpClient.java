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
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
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
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public abstract class HttpClient {
    private static final String TAG = HttpClient.class.getSimpleName();

    private HttpUrl baseUrl;
    private Map<String, String> headers = new HashMap<>();
    private OkHttpClient client;

    protected HttpClient(Context context) {
        this(context, PreferenceManager.getDefaultSharedPreferences(context));
    }

    protected HttpClient(Context context, SharedPreferences prefs) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        if (prefs.getBoolean(Constants.PREFERENCE_SSLHOST, false)) {
            builder.hostnameVerifier(new HostnameVerifier() {
                @SuppressLint("BadHostnameVerifier")
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
        }

        X509TrustManager x509TrustManager = null;

        if (prefs.getBoolean(Constants.PREFERENCE_SSLCERT, false)) {
            x509TrustManager = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            };
        } else {
            // get default trust manager
            try {
                TrustManagerFactory trustManagerFactory =
                        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init((KeyStore) null);
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
            sslContext.init(MyKeyManager.getInstance(context),
                    new TrustManager[]{ x509TrustManager }, new SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            builder.sslSocketFactory(sslSocketFactory, x509TrustManager);
        } catch (Exception e) {
            Log.d(TAG, "Applying certificate trust settings failed", e);
        }

        client = builder.build();
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

    public HttpUrl getBaseUrl() {
        if (baseUrl == null) {
            throw new IllegalStateException("No baseUrl was set so far.");
        }
        return this.baseUrl;
    }

    public void setTimeout(int timeout) {
        client = client.newBuilder()
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .build();
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

    protected Call prepareCall(String url, String method, Map<String, String> additionalHeaders,
                               String requestBody, String mediaType) {
        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.url(getBaseUrl().newBuilder(url).build());
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestBuilder.addHeader(entry.getKey(), entry.getValue());
        }
        if (additionalHeaders != null) {
            for (Map.Entry<String, String> entry : additionalHeaders.entrySet()) {
                requestBuilder.addHeader(entry.getKey(), entry.getValue());
            }
        }
        if (requestBody != null) {
            requestBuilder.method(method, RequestBody.create(MediaType.parse(mediaType), requestBody));
        }
        Request request = requestBuilder.build();
        return client.newCall(request);
    }
}