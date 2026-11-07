/*
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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

package org.openhab.habdroid.wear.connection

import android.util.Base64
import android.util.Log
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.internal.tls.OkHostnameVerifier

/**
 * Trusts the certificates the system trusts and additionally the certificates the user accepted in the phone app.
 * Like on the phone, an accepted certificate is also trusted if the host name doesn't match.
 */
class SyncedCertificates(encodedCertificates: List<String>) {
    private val certificates: Set<X509Certificate> = encodedCertificates.mapNotNull { encoded ->
        decode(encoded)
    }.toSet()

    private val systemTrustManager: X509TrustManager = TrustManagerFactory
        .getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .apply { init(null as KeyStore?) }
        .trustManagers
        .filterIsInstance<X509TrustManager>()
        .first()

    val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
            systemTrustManager.checkClientTrusted(chain, authType)

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
            try {
                systemTrustManager.checkServerTrusted(chain, authType)
            } catch (e: CertificateException) {
                if (chain.firstOrNull() !in certificates) {
                    throw e
                }
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = systemTrustManager.acceptedIssuers
    }

    val hostnameVerifier = HostnameVerifier { hostname: String, session: SSLSession ->
        OkHostnameVerifier.verify(hostname, session) || session.peerCertificates.firstOrNull() in certificates
    }

    private fun decode(encoded: String): X509Certificate? = try {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
    } catch (e: Exception) {
        Log.w(TAG, "Unable to decode certificate", e)
        null
    }

    companion object {
        private val TAG = SyncedCertificates::class.java.simpleName
    }
}
