/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *  @author Slawomir Jaranowski
 *
 */
package org.openhab.habdroid.util;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManager;
import javax.net.ssl.X509KeyManager;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.security.KeyChain;
import android.security.KeyChainException;
import android.util.Log;

/**
 * Key manager to support selected client certificate.
 */
class MyKeyManager implements X509KeyManager {

    private final static String TAG = MyKeyManager.class.getSimpleName();

    private static String alias;
    private static X509Certificate[] certificateChain;
    private static PrivateKey privateKey;

    private MyKeyManager(final Context ctx) {

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(ctx);
        final String preferencesAlias = preferences.getString(Constants.PREFERENCE_SSLCLIENTCERT, null);

        if (preferencesAlias == null) {
            alias = null;
            certificateChain = null;
            privateKey = null;
            return;
        }

        if (!preferencesAlias.equals(alias) || certificateChain == null || privateKey == null) {

            // refresh cached certificate and  keys
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        certificateChain = KeyChain.getCertificateChain(ctx, preferencesAlias);
                        privateKey = KeyChain.getPrivateKey(ctx, preferencesAlias);
                        alias = preferencesAlias;
                    } catch (KeyChainException | InterruptedException e) {
                        Log.e(TAG, e.getMessage(), e);
                    }
                }
            }).start();
        }
    }

    public static KeyManager[] getInstance(Context ctx) {
        return new KeyManager[] { new MyKeyManager(ctx)};
    }

    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
        Log.d(TAG, "chooseClientAlias - alias: " + alias);
        return alias;
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
        Log.d(TAG, "chooseServerAlias");
        return null;
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        Log.d(TAG, "getCertificateChain");
        return certificateChain;
    }

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        Log.d(TAG, "getClientAliases");
        return alias!= null ? new String[] {alias} : null;
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        Log.d(TAG, "getServerAliases");
        return null;
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
        Log.d(TAG, "getPrivateKey");
        return privateKey;
    }
}
