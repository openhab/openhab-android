
/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.HttpAuthHandler;

class AnchorWebViewClient extends WebViewClient {
    private static final String TAG = AnchorWebViewClient.class.getSimpleName();

    private String mAnchor;
    private String mUserName;
    private String mPassword;

    public AnchorWebViewClient(String url, String username, String password) {
        mUserName = username;
        mPassword = password;
        int pos = url.lastIndexOf("#") + 1;
        if (pos != 0 && pos < url.length()) {
            mAnchor = url.substring(pos);
            Log.d(TAG, "Found anchor " + mAnchor + " from url "+ url);
        } else {
            Log.d(TAG, "Did not find anchor from url "+ url);
        }
    }

    @Override
    public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler,
            String host, String realm) {
        handler.proceed(mUserName, mPassword);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        if (mAnchor != null && !mAnchor.isEmpty()) {
            Log.d(TAG, "Now jumping to anchor " + mAnchor);
            view.loadUrl("javascript:location.hash = '#" + mAnchor + "';");
        }
    }
}
