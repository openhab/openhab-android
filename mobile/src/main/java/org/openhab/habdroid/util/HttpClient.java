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
import android.preference.PreferenceManager;
import android.security.KeyChain;
import android.security.KeyChainException;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import java.net.Socket;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import de.duenndns.ssl.MemorizingTrustManager;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public abstract class HttpClient {
    private static final String TAG = HttpClient.class.getSimpleName();

    public enum CachingMode {
        DEFAULT,
        AVOID_CACHE,
        FORCE_CACHE_IF_POSSIBLE
    }

    private final HttpUrl mBaseUrl;
    private final Map<String, String> headers = new HashMap<>();

    private OkHttpClient mClient;
    private HostnameVerifier mDefaultHostnameVerifier;
    private SSLSocketFactory mDefaultSocketFactory;

    private static final List<String> SSL_RELEVANT_KEYS = Arrays.asList(
            Constants.PREFERENCE_SSLHOST, Constants.PREFERENCE_SSLCERT,
            Constants.PREFERENCE_SSLCLIENTCERT
    );

    protected HttpClient(Context context, String baseUrl) {
        this(context, PreferenceManager.getDefaultSharedPreferences(context), baseUrl);
    }

    protected HttpClient(Context context, SharedPreferences prefs, String baseUrl) {
        final Context appContext = context.getApplicationContext();
        mClient = new OkHttpClient.Builder()
                .cache(CacheManager.getInstance(context).getHttpCache())
                .build();
        mBaseUrl = baseUrl != null ? HttpUrl.parse(baseUrl) : null;

        mDefaultHostnameVerifier = mClient.hostnameVerifier();
        mDefaultSocketFactory = mClient.sslSocketFactory();

        applySslProperties(appContext, prefs);
        prefs.registerOnSharedPreferenceChangeListener((prefsInstance, key) -> {
            if (SSL_RELEVANT_KEYS.contains(key)) {
                applySslProperties(appContext, prefs);
            }
        });
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

    public HttpUrl buildUrl(String url) {
        HttpUrl absoluteUrl = HttpUrl.parse(url);
        if (absoluteUrl == null && mBaseUrl != null) {
            absoluteUrl = HttpUrl.parse(mBaseUrl.toString() + url);
        }
        if (absoluteUrl == null) {
            throw new IllegalArgumentException("URL '" + url + "' is invalid");
        }
        return absoluteUrl;
    }

    protected Call prepareCall(String url, String method, Map<String, String> additionalHeaders,
                               String requestBody, String mediaType, CachingMode caching) {
        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.url(buildUrl(url));
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
        switch (caching) {
            case AVOID_CACHE:
                requestBuilder.cacheControl(CacheControl.FORCE_NETWORK);
                break;
            case FORCE_CACHE_IF_POSSIBLE:
                requestBuilder.cacheControl(new CacheControl.Builder()
                        .maxStale(Integer.MAX_VALUE, TimeUnit.SECONDS)
                        .build());
                break;
            default:
                break;
        }
        Request request = requestBuilder.build();
        return mClient.newCall(request);
    }

    private void applySslProperties(Context context, SharedPreferences prefs) {
        OkHttpClient.Builder builder = mClient.newBuilder();

        if (prefs.getBoolean(Constants.PREFERENCE_SSLHOST, false)) {
            builder.hostnameVerifier((hostname, session) -> true);
        } else {
            builder.hostnameVerifier(mDefaultHostnameVerifier);
        }

        X509TrustManager trustManager = prefs.getBoolean(Constants.PREFERENCE_SSLCERT, false)
                ? new DummyTrustManager()
                : MemorizingTrustManager.getInstance(context);

        String clientCertAlias = prefs.getString(Constants.PREFERENCE_SSLCLIENTCERT, null);
        KeyManager[] keyManagers = clientCertAlias != null
                ? new KeyManager[] { new ClientKeyManager(context, clientCertAlias) }
                : new KeyManager[0];

        try {
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, new TrustManager[] { trustManager }, new SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            builder.sslSocketFactory(sslSocketFactory, trustManager);
        } catch (Exception e) {
            Log.d(TAG, "Applying certificate trust settings failed", e);
            builder.sslSocketFactory(mDefaultSocketFactory, trustManager);
        }

        mClient = builder.build();
    }

    private static X509TrustManager getDefaultTrustManager() {
        try {
            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

            for (TrustManager trustManager : trustManagers) {
                if (trustManager instanceof X509TrustManager) {
                    return (X509TrustManager) trustManager;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Getting default trust manager failed", e);
        }

        return null;
    }

    private static class DummyTrustManager implements X509TrustManager {
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
