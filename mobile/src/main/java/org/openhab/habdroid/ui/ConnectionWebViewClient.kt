/*
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
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
import de.duenndns.ssl.MemorizingTrustManager
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import java.io.ByteArrayInputStream
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory

open class ConnectionWebViewClient(
    private val connection: Connection
) : WebViewClient() {

    override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
        handler.proceed(connection.username, connection.password)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        val context = view.context
        val cert = getX509Certificate(error.certificate)
        val mtm = MemorizingTrustManager(context)
        if (cert != null && mtm.isCertKnown(cert)) {
            Log.d(TAG, "Invalid certificate, but the same one as the main connection")
            handler.proceed()
        } else {
            Log.e(TAG, "Invalid certificate")
            handler.cancel()
            val host = connection.httpClient.buildUrl("").host()
            val errorMessage = when (error.primaryError) {
                SslError.SSL_NOTYETVALID -> context.getString(R.string.error_certificate_not_valid_yet)
                SslError.SSL_EXPIRED -> context.getString(R.string.error_certificate_expired)
                SslError.SSL_IDMISMATCH -> context.getString(R.string.error_certificate_wrong_host, host)
                SslError.SSL_DATE_INVALID -> context.getString(R.string.error_certificate_invalid_date)
                else -> context.getString(R.string.webview_ssl)
            }

            val html = "<html><body><p>$errorMessage</p><p>${error.certificate}</p></body></html>"
            val encodedHtml = Base64.encodeToString(html.toByteArray(), Base64.NO_PADDING)
            view.loadData(encodedHtml, "text/html; charset=UTF-8", "base64")
        }
    }

    /**
     * @author Heath Borders at https://stackoverflow.com/questions/20228800/how-do-i-validate-an-android-net-http-sslcertificate-with-an-x509trustmanager
     */
    private fun getX509Certificate(sslCertificate: SslCertificate): Certificate? {
        val bundle = SslCertificate.saveState(sslCertificate)
        val bytes = bundle.getByteArray("x509-certificate") ?: return null
        return try {
            CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(bytes))
        } catch (e: CertificateException) {
            null
        }
    }

    companion object {
        private val TAG = ConnectionWebViewClient::class.java.simpleName
    }
}
