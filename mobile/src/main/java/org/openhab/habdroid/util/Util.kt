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

package org.openhab.habdroid.util

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.StyleRes
import androidx.core.net.toUri
import es.dmoral.toasty.Toasty

import org.openhab.habdroid.BuildConfig
import org.openhab.habdroid.R

import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.CertificateRevokedException

import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException

object Util {
    val TAG: String = Util::class.java.simpleName

    private val isFlavorStable get() = BuildConfig.FLAVOR.toLowerCase().contains("stable")
    val isFlavorBeta get() = !isFlavorStable
    val isFlavorFull get() = BuildConfig.FLAVOR.toLowerCase().contains("full")
    val isFlavorFoss get() = !isFlavorFull

    @StyleRes
    @JvmOverloads
    fun getActivityThemeId(activity: Activity, theme: String? = null): Int {
        val actualTheme = theme ?: activity.getPrefs().getString(Constants.PREFERENCE_THEME)

        return when (actualTheme) {
            activity.getString(R.string.theme_value_dark) -> R.style.HABDroid_Dark
            activity.getString(R.string.theme_value_black) -> R.style.HABDroid_Black
            activity.getString(R.string.theme_value_basic_ui) -> R.style.HABDroid_Basic_ui
            activity.getString(R.string.theme_value_basic_ui_dark) -> R.style.HABDroid_Basic_ui_dark
            else -> R.style.HABDroid_Light
        }
    }

    fun getHumanReadableErrorMessage(context: Context, url: String, statusCode: Int, error: Throwable): CharSequence {
        if (statusCode >= 400) {
            return if (error.message == "openHAB is offline") {
                context.getString(R.string.error_openhab_offline)
            } else {
                try {
                    context.getString(context.resources.getIdentifier(
                        "error_http_code_$statusCode",
                        "string", context.packageName))
                } catch (e: Resources.NotFoundException) {
                    context.getString(R.string.error_http_connection_failed, statusCode)
                }
            }
        } else if (error is UnknownHostException) {
            Log.e(TAG, "Unable to resolve hostname")
            return context.getString(R.string.error_unable_to_resolve_hostname)
        } else if (error is SSLException) {
            // if ssl exception, check for some common problems
            return if (error.hasCause(CertPathValidatorException::class.java)) {
                context.getString(R.string.error_certificate_not_trusted)
            } else if (error.hasCause(CertificateExpiredException::class.java)) {
                context.getString(R.string.error_certificate_expired)
            } else if (error.hasCause(CertificateNotYetValidException::class.java)) {
                context.getString(R.string.error_certificate_not_valid_yet)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                error.hasCause(CertificateRevokedException::class.java)
            ) {
                context.getString(R.string.error_certificate_revoked)
            } else if (error.hasCause(SSLPeerUnverifiedException::class.java)) {
                context.getString(R.string.error_certificate_wrong_host, url.toUri().host)
            } else {
                context.getString(R.string.error_connection_sslhandshake_failed)
            }
        } else if (error is ConnectException || error is SocketTimeoutException) {
            return context.getString(R.string.error_connection_failed)
        } else if (error is IOException && error.hasCause(EOFException::class.java)) {
            return context.getString(R.string.error_http_to_https_port)
        } else {
            Log.e(TAG, "REST call to $url failed", error)
            return error.message.orEmpty()
        }
    }

    /**
     * Shows an orange Toast with the openHAB icon. Can be called from the background.
     */
    fun showToast(context: Context, message: CharSequence) {
        Handler(Looper.getMainLooper()).post {
            Toasty.custom(context, message, R.drawable.ic_openhab_appicon_24dp,
                R.color.openhab_orange, Toasty.LENGTH_SHORT, true, true)
                .show()
        }
    }

    fun isEmulator(context: Context): Boolean {
        val isEmulator = Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MODEL.contains("sdk_phone_armv7") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            "google_sdk" == Build.PRODUCT
        Log.d(TAG, "Device is emulator: $isEmulator")
        if (context.getPrefs().isDebugModeEnabled()) {
            Log.d(TAG, "Fingerprint: ${Build.FINGERPRINT}, Model: ${Build.MODEL}," +
                " Manufacturer: ${Build.MANUFACTURER}, Brand: ${Build.BRAND}, Device: ${Build.DEVICE}," +
                " Product: ${Build.PRODUCT}")
        }
        return isEmulator
    }
}
