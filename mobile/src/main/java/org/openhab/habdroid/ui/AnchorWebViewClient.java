
/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.http.SslError;
import android.preference.PreferenceManager;
import android.util.Log;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.HttpAuthHandler;

import org.openhab.habdroid.util.Constants;

class AnchorWebViewClient extends WebViewClient {
    private static final String TAG = AnchorWebViewClient.class.getSimpleName();
    private String anchor = null;
    private String username;
    private String password;

    public AnchorWebViewClient(String url, String username, String password) {
        this.username = username;
        this.password = password;
        int pos = url.lastIndexOf("#") + 1;
        if(pos != 0 && pos<url.length()) {
            this.anchor = url.substring(pos);
            Log.d(TAG, "Found anchor " + anchor + " from url "+ url);
        } else {
            Log.d(TAG, "Did not find anchor from url "+ url);
        }
    }

    @Override
    public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
        handler.proceed(this.username, this.password);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        if (anchor != null && !anchor.isEmpty()) {
            Log.d(TAG, "Now jumping to anchor " + anchor);
            view.loadUrl("javascript:location.hash = '#" + anchor + "';");
        }
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        Context mCtx = view.getContext();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mCtx);
        if (settings.getBoolean(Constants.PREFERENCE_SSLCERT, false)) {
            handler.proceed();
        }
    }
}
