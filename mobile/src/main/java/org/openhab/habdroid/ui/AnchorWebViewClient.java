
/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import de.duenndns.ssl.MemorizingTrustManager;
import org.openhab.habdroid.R;

import java.io.ByteArrayInputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

public class AnchorWebViewClient extends WebViewClient {
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
            Log.d(TAG, "Found anchor " + mAnchor + " from url " + url);
        } else {
            Log.d(TAG, "Did not find anchor from url " + url);
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

    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        SslCertificate sslCertificate = error.getCertificate();
        Certificate cert = getX509Certificate(sslCertificate);
        MemorizingTrustManager mtm = new MemorizingTrustManager(view.getContext());
        if (cert != null && mtm.isCertKnown(cert)) {
            Log.d(TAG, "Invalid certificate, but the same one as the main connection");
            handler.proceed();
        } else {
            Log.e(TAG, "Invalid certificate: "
                    + (cert == null ? "Cert is null" : cert.getPublicKey().toString()));
            handler.cancel();
            String errorMessage = "<html><body>" + view.getContext().getString(R.string.webview_ssl)
                    + "</body></html>";
            String encodedHtml = Base64.encodeToString(errorMessage.getBytes(), Base64.NO_PADDING);
            view.loadData(encodedHtml, "text/html", "base64");
        }
    }

    /**
     * @author Heath Borders at https://stackoverflow.com/questions/20228800/how-do-i-validate-an-android-net-http-sslcertificate-with-an-x509trustmanager
     */
    private Certificate getX509Certificate(SslCertificate sslCertificate) {
        Bundle bundle = SslCertificate.saveState(sslCertificate);
        byte[] bytes = bundle.getByteArray("x509-certificate");
        if (bytes == null) {
            return null;
        } else {
            try {
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                return certFactory.generateCertificate(new ByteArrayInputStream(bytes));
            } catch (CertificateException e) {
                return null;
            }
        }
    }
}
