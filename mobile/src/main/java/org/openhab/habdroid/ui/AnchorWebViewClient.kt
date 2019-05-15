/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui

import android.net.http.SslCertificate
import android.net.http.SslError
import android.util.Base64
import android.util.Log
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import de.duenndns.ssl.MemorizingTrustManager
import org.openhab.habdroid.R
import java.io.ByteArrayInputStream
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory

open class AnchorWebViewClient(url: String, private val userName: String?, private val password: String?) : WebViewClient() {
    private val anchor: String?
    private val host: String? = url.toUri().host

    init {
        val pos = url.lastIndexOf("#") + 1
        if (pos != 0 && pos < url.length - 1) {
            anchor = url.substring(pos)
            Log.d(TAG, "Found anchor $anchor from url $url")
        } else {
            Log.d(TAG, "Did not find anchor from url $url")
            anchor = null
        }
    }

    override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler,
                                           host: String, realm: String) {
        handler.proceed(userName, password)
    }

    override fun onPageFinished(view: WebView, url: String) {
        if (anchor != null) {
            Log.d(TAG, "Now jumping to anchor $anchor")
            view.loadUrl("javascript:location.hash = '#$anchor';")
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        val context = view.context
        val sslCertificate = error.certificate
        val cert = getX509Certificate(sslCertificate)
        val mtm = MemorizingTrustManager(context)
        if (cert != null && mtm.isCertKnown(cert)) {
            Log.d(TAG, "Invalid certificate, but the same one as the main connection")
            handler.proceed()
        } else {
            Log.e(TAG, "Invalid certificate")
            handler.cancel()
            val errorMessage = when (error.primaryError) {
                SslError.SSL_NOTYETVALID -> context.getString(R.string.error_certificate_not_valid_yet)
                SslError.SSL_EXPIRED -> context.getString(R.string.error_certificate_expired)
                SslError.SSL_IDMISMATCH -> context.getString(R.string.error_certificate_wrong_host, host)
                SslError.SSL_DATE_INVALID -> context.getString(R.string.error_certificate_invalid_date)
                else -> context.getString(R.string.webview_ssl)
            }

            val encodedHtml = Base64.encodeToString(("<html><body><p>" + errorMessage + "</p><p>"
                    + sslCertificate.toString() + "</p></body></html>").toByteArray(),
                    Base64.NO_PADDING)
            view.loadData(encodedHtml, "text/html; charset=UTF-8", "base64")
        }
    }

    /**
     * @author Heath Borders at https://stackoverflow.com/questions/20228800/how-do-i-validate-an-android-net-http-sslcertificate-with-an-x509trustmanager
     */
    private fun getX509Certificate(sslCertificate: SslCertificate): Certificate? {
        val bundle = SslCertificate.saveState(sslCertificate)
        val bytes = bundle.getByteArray("x509-certificate")
        return if (bytes == null) {
            null
        } else {
            try {
                val certFactory = CertificateFactory.getInstance("X.509")
                certFactory.generateCertificate(ByteArrayInputStream(bytes))
            } catch (e: CertificateException) {
                null
            }

        }
    }

    companion object {
        private val TAG = AnchorWebViewClient::class.java.simpleName
    }
}
