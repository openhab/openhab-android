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
import android.security.KeyChain;
import android.security.KeyChainException;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import de.duenndns.ssl.MemorizingTrustManager;
import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.internal.tls.OkHostnameVerifier;

public abstract class HttpClient {
    private static final String TAG = HttpClient.class.getSimpleName();

    private static MemorizingTrustManager sTrustManagerInstance;
    private final HttpUrl mBaseUrl;
    private final Map<String, String> headers = new HashMap<>();
    private OkHttpClient mClient;

    protected HttpClient(Context context, String baseUrl, String clientCertAlias) {
        MemorizingTrustManager mtm = getTrustManagerInstance(context);

        mBaseUrl = baseUrl != null ? HttpUrl.parse(baseUrl) : null;
        mClient = applyClientCert(new OkHttpClient.Builder(), context, clientCertAlias, mtm)
                .hostnameVerifier(mtm.wrapHostnameVerifier(OkHostnameVerifier.INSTANCE))
                .build();
    }

    public void setBasicAuth(String username, String password) {
        String credential = Credentials.basic(username, password);
        headers.put("Authorization", credential);
    }

    public void setTimeout(int timeout) {
        mClient = mClient.newBuilder()
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

    public static MemorizingTrustManager getTrustManagerInstance(Context c) {
        if (sTrustManagerInstance == null) {
            sTrustManagerInstance = new MemorizingTrustManager(c.getApplicationContext());
        }
        return sTrustManagerInstance;
    }

    protected Call prepareCall(String url, String method, Map<String, String> additionalHeaders,
                               String requestBody, String mediaType) {
        Request.Builder requestBuilder = new Request.Builder();
        if (mBaseUrl == null) {
            requestBuilder.url(url);
        } else {
            requestBuilder.url(mBaseUrl.newBuilder(url).build());
        }
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
        return mClient.newCall(request);
    }

    private static OkHttpClient.Builder applyClientCert(OkHttpClient.Builder builder,
            Context context, String clientCertAlias, X509TrustManager trustManager) {
        KeyManager[] keyManagers = clientCertAlias != null
                ? new KeyManager[] { new ClientKeyManager(context, clientCertAlias) }
                : null;

        try {
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, new TrustManager[] { trustManager }, null);
            builder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);
        } catch (Exception e) {
            Log.d(TAG, "Applying certificate trust settings failed", e);
        }

        return builder;
    }

    private static class ClientKeyManager implements X509KeyManager {
        private final static String TAG = ClientKeyManager.class.getSimpleName();

        private Context mContext;
        private String mAlias;

        public ClientKeyManager(Context context, String alias) {
            mContext = context.getApplicationContext();
            mAlias = alias;
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            Log.d(TAG, "chooseClientAlias - alias: " + mAlias);
            return mAlias;
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return null;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            Log.d(TAG, "getCertificateChain(" + alias + ")");
            try {
                return KeyChain.getCertificateChain(mContext, alias);
            } catch (KeyChainException | InterruptedException e) {
                Log.e(TAG, "Failed loading certificate chain", e);
                return null;
            }
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return mAlias != null ? new String[] { mAlias } : null;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return null;
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            Log.d(TAG, "getPrivateKey(" + alias + ")");
            try {
                return KeyChain.getPrivateKey(mContext, mAlias);
            } catch (KeyChainException | InterruptedException e) {
                Log.e(TAG, "Failed loading private key", e);
                return null;
            }
        }
    }
}