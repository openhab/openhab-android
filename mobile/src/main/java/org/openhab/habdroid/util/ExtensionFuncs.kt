/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.caverock.androidsvg.SVG
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.openhab.habdroid.R
import org.openhab.habdroid.core.OpenHabApplication
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.CertificateRevokedException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException

fun Throwable?.hasCause(cause: Class<out Throwable>): Boolean {
    var error = this
    while (error != null) {
        if (error.javaClass == cause) {
            return true
        }
        error = error.cause
    }
    return false
}

/**
 * Replaces everything after the first clearTextCharCount chars with asterisks
 * @param clearTextCharCount leave the first clearTextCharCount in clear text
 * @return obfuscated string
 */
fun String.obfuscate(clearTextCharCount: Int = 3): String {
    if (length < clearTextCharCount) {
        return this
    }
    return substring(0, clearTextCharCount) + "*".repeat(length - clearTextCharCount)
}

fun String?.toNormalizedUrl(): String {
    return try {
        val url = URL(orEmpty())
            .toString()
            .replace("\n", "")
            .replace(" ", "")
        if (url.endsWith("/")) url else "$url/"
    } catch (e: MalformedURLException) {
        Log.d(Util.TAG, "normalizeUrl(): invalid URL '$this'")
        ""
    }
}

fun String?.orDefaultIfEmpty(defaultValue: String) = if (isNullOrEmpty()) defaultValue else this!!

fun Uri?.openInBrowser(context: Context) {
    if (this == null) {
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, this)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toasty.error(context, R.string.error_no_browser_found, Toasty.LENGTH_LONG).show()
    }
}

/**
 * This method converts dp unit to equivalent pixels, depending on device density.
 *
 * @param dp A value in dp (density independent pixels) unit. Which we need to convert into
 *          pixels
 * @return A float value to represent px equivalent to dp depending on device density
 * @author https://stackoverflow.com/a/9563438
 */
fun Resources.dpToPixel(dp: Float): Float {
    return dp * displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT
}

@Throws(IOException::class)
fun ResponseBody.toBitmap(targetSize: Int, enforceSize: Boolean = false): Bitmap {
    if (!contentType().isSvg()) {
        val bitmap = BitmapFactory.decodeStream(byteStream())
            ?: throw IOException("Bitmap decoding failed")
        return if (!enforceSize) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, false)
        }
    }

    return byteStream().svgToBitmap(targetSize)
}

fun MediaType?.isSvg(): Boolean {
    return this != null && this.type == "image" && this.subtype.contains("svg")
}

@Throws(IOException::class)
fun InputStream.svgToBitmap(targetSize: Int): Bitmap {
    return try {
        val svg = SVG.getFromInputStream(this)
        val displayMetrics = Resources.getSystem().displayMetrics
        svg.renderDPI = DisplayMetrics.DENSITY_DEFAULT.toFloat()
        var density: Float? = displayMetrics.density
        svg.setDocumentHeight("100%")
        svg.setDocumentWidth("100%")
        var docWidth = (svg.documentWidth * displayMetrics.density).toInt()
        var docHeight = (svg.documentHeight * displayMetrics.density).toInt()

        if (docWidth < 0 || docHeight < 0) {
            val aspectRatio = svg.documentAspectRatio
            if (aspectRatio > 0) {
                val heightForAspect = targetSize.toFloat() / aspectRatio
                val widthForAspect = targetSize.toFloat() * aspectRatio
                if (widthForAspect < heightForAspect) {
                    docWidth = Math.round(widthForAspect)
                    docHeight = targetSize
                } else {
                    docWidth = targetSize
                    docHeight = Math.round(heightForAspect)
                }
            } else {
                docWidth = targetSize
                docHeight = targetSize
            }

            // we didn't take density into account anymore when calculating docWidth
            // and docHeight, so don't scale with it and just let the renderer
            // figure out the scaling
            density = null
        }

        if (docWidth != targetSize || docHeight != targetSize) {
            val scaleWidth = targetSize.toFloat() / docWidth
            val scaleHeight = targetSize.toFloat() / docHeight
            density = (scaleWidth + scaleHeight) / 2
        }

        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (density != null) {
            canvas.scale(density, density)
        }
        svg.renderToCanvas(canvas)
        bitmap
    } catch (e: Exception) {
        throw IOException("SVG decoding failed", e)
    }
}

fun NodeList.forEach(action: (Node) -> Unit) =
    (0 until length).forEach { index -> action(item(index)) }

fun JSONArray.forEach(action: (JSONObject) -> Unit) =
    (0 until length()).forEach { index -> action(getJSONObject(index)) }

inline fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> {
    return (0 until length()).map { index -> transform(getJSONObject(index)) }
}

fun JSONObject.optStringOrNull(key: String): String? {
    return optStringOrFallback(key, null)
}

fun JSONObject.optStringOrFallback(key: String, fallback: String?): String? {
    return if (has(key)) getString(key) else fallback
}

fun Context.getPrefs(): SharedPreferences {
    return PreferenceManager.getDefaultSharedPreferences(this)
}

fun Context.getSecretPrefs(): SharedPreferences {
    return (applicationContext as OpenHabApplication).secretPrefs
}

enum class ToastType {
    NORMAL,
    SUCCESS,
    ERROR
}

/**
 * Shows an Toast with the openHAB icon. Can be called from the background.
 */
fun Context.showToast(message: CharSequence, type: ToastType = ToastType.NORMAL) {
    val color = when (type) {
        ToastType.SUCCESS -> R.color.pref_icon_green
        ToastType.ERROR -> R.color.pref_icon_red
        else -> R.color.openhab_orange
    }
    val length = if (type == ToastType.ERROR) Toasty.LENGTH_LONG else Toasty.LENGTH_SHORT

    GlobalScope.launch(Dispatchers.Main) {
        Toasty.custom(
            applicationContext,
            message,
            R.drawable.ic_openhab_appicon_24dp,
            color,
            length,
            true,
            true
        ).show()
    }
}

/**
 * Shows an Toast with the openHAB icon. Can be called from the background.
 */
fun Context.showToast(@StringRes message: Int, type: ToastType = ToastType.NORMAL) {
    showToast(getString(message), type)
}

fun Context.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
}

fun Context.getHumanReadableErrorMessage(url: String, statusCode: Int, error: Throwable): CharSequence {
    if (statusCode >= 400) {
        return if (error.message == "openHAB is offline") {
            getString(R.string.error_openhab_offline)
        } else {
            try {
                getString(
                    resources.getIdentifier("error_http_code_$statusCode", "string", packageName),
                    statusCode
                )
            } catch (e: Resources.NotFoundException) {
                getString(R.string.error_http_connection_failed, statusCode)
            }
        }
    } else if (error is UnknownHostException) {
        Log.e(Util.TAG, "Unable to resolve hostname")
        return getString(R.string.error_unable_to_resolve_hostname)
    } else if (error is SSLException) {
        // If ssl exception, check for some common problems
        return if (error.hasCause(CertPathValidatorException::class.java)) {
            getString(R.string.error_certificate_not_trusted)
        } else if (error.hasCause(CertificateExpiredException::class.java)) {
            getString(R.string.error_certificate_expired)
        } else if (error.hasCause(CertificateNotYetValidException::class.java)) {
            getString(R.string.error_certificate_not_valid_yet)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            error.hasCause(CertificateRevokedException::class.java)
        ) {
            getString(R.string.error_certificate_revoked)
        } else if (error.hasCause(SSLPeerUnverifiedException::class.java)) {
            getString(R.string.error_certificate_wrong_host, url.toUri().host)
        } else {
            getString(R.string.error_connection_sslhandshake_failed)
        }
    } else if (error is ConnectException || error is SocketTimeoutException) {
        return getString(R.string.error_connection_failed)
    } else if (error is IOException && error.hasCause(EOFException::class.java)) {
        return getString(R.string.error_http_to_https_port)
    } else {
        Log.e(Util.TAG, "REST call to $url failed", error)
        return error.localizedMessage ?: getString(R.string.error_unknown, error.javaClass)
    }
}

fun Context.getShortHumanReadableErrorMessage(url: String, statusCode: Int, error: Throwable?): CharSequence {
    if (statusCode >= 400) {
        return if (error?.message == "openHAB is offline") {
            getString(R.string.error_short_openhab_offline)
        } else {
            try {
                getString(
                    resources.getIdentifier("error_short_http_code_$statusCode", "string", packageName),
                    statusCode
                )
            } catch (e: Resources.NotFoundException) {
                getString(R.string.error_short_http_connection_failed, statusCode)
            }
        }
    } else if (error is UnknownHostException) {
        Log.e(Util.TAG, "Unable to resolve hostname")
        return getString(R.string.error_short_unable_to_resolve_hostname)
    } else if (error is SSLException) {
        // If ssl exception, check for some common problems
        return if (error.hasCause(CertPathValidatorException::class.java)) {
            getString(R.string.error_short_certificate_not_trusted)
        } else if (error.hasCause(CertificateExpiredException::class.java)) {
            getString(R.string.error_short_certificate_expired)
        } else if (error.hasCause(CertificateNotYetValidException::class.java)) {
            getString(R.string.error_short_certificate_not_valid_yet)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            error.hasCause(CertificateRevokedException::class.java)
        ) {
            getString(R.string.error_short_certificate_revoked)
        } else if (error.hasCause(SSLPeerUnverifiedException::class.java)) {
            getString(R.string.error_short_certificate_wrong_host, url.toUri().host)
        } else {
            getString(R.string.error_short_connection_sslhandshake_failed)
        }
    } else if (error is ConnectException || error is SocketTimeoutException) {
        return getString(R.string.error_short_connection_failed)
    } else if (error is IOException && error.hasCause(EOFException::class.java)) {
        return getString(R.string.error_short_http_to_https_port)
    } else {
        Log.e(Util.TAG, "REST call to $url failed", error)
        return error?.localizedMessage ?: getString(R.string.error_short_unknown, error?.javaClass)
    }
}

fun Activity.finishAndRemoveTaskIfPossible() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        finishAndRemoveTask()
    } else {
        finish()
    }
}
