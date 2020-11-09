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

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.caverock.androidsvg.SVG
import es.dmoral.toasty.Toasty
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.CertificateRevokedException
import javax.jmdns.ServiceInfo
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.math.max
import kotlin.math.round
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.openhab.habdroid.R
import org.openhab.habdroid.core.OpenHabApplication
import org.openhab.habdroid.model.ServerConfiguration
import org.openhab.habdroid.model.ServerPath
import org.w3c.dom.Node
import org.w3c.dom.NodeList

fun Throwable?.hasCause(cause: Class<out Throwable>): Boolean {
    var error = this
    while (error != null) {
        if (error::class.java == cause) {
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

fun String?.toNormalizedUrl(): String? {
    this ?: return null
    return try {
        val url = this
            .replace("\n", "")
            .replace(" ", "")
            .toHttpUrl()
            .toString()
        if (url.endsWith("/")) url else "$url/"
    } catch (e: IllegalArgumentException) {
        Log.d(Util.TAG, "normalizeUrl(): invalid URL '$this'")
        null
    }
}

fun String?.orDefaultIfEmpty(defaultValue: String) = if (isNullOrEmpty()) defaultValue else this

fun Uri?.openInBrowser(context: Context) {
    if (this == null) {
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, this)
    if (intent.isResolvable(context)) {
        context.startActivity(intent)
    } else {
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

enum class ImageConversionPolicy {
    PreferSourceSize,
    PreferTargetSize,
    ForceTargetSize
}

@Throws(IOException::class)
fun ResponseBody.toBitmap(targetSize: Int, conversionPolicy: ImageConversionPolicy): Bitmap {
    if (!contentType().isSvg()) {
        val bitmap = BitmapFactory.decodeStream(byteStream())
            ?: throw IOException("Bitmap decoding failed")
        // Avoid overly huge bitmaps, as we both do not want their memory consumption and drawing those bitmaps
        // to a canvas will fail later anyway. The actual limitation threshold is more or less arbitrary; as of
        // Android 10 the OS side limit is 100 MB.
        return if (bitmap.byteCount > 20000000 && bitmap.width > targetSize) {
            val scaler = bitmap.width.toFloat() / targetSize.toFloat()
            val scaledHeight = bitmap.height.toFloat() / scaler
            Bitmap.createScaledBitmap(bitmap, targetSize, scaledHeight.toInt(), true)
        } else if (conversionPolicy == ImageConversionPolicy.ForceTargetSize) {
            Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
        } else {
            bitmap
        }
    }

    return byteStream().svgToBitmap(targetSize, conversionPolicy)
}

fun MediaType?.isSvg(): Boolean {
    return this != null && this.type == "image" && this.subtype.contains("svg")
}

@Throws(IOException::class)
fun InputStream.svgToBitmap(targetSize: Int, conversionPolicy: ImageConversionPolicy): Bitmap {
    return try {
        val svg = SVG.getFromInputStream(this)
        val displayMetrics = Resources.getSystem().displayMetrics
        var density: Float? = displayMetrics.density
        val targetSizeFloat = targetSize.toFloat()

        if (svg.documentViewBox == null && svg.documentWidth > 0 && svg.documentHeight > 0) {
            svg.setDocumentViewBox(0F, 0F, svg.documentWidth, svg.documentHeight)
        }
        if (conversionPolicy == ImageConversionPolicy.ForceTargetSize ||
            (conversionPolicy == ImageConversionPolicy.PreferTargetSize && svg.documentViewBox != null)
        ) {
            svg.setDocumentWidth("100%")
            svg.setDocumentHeight("100%")
        }

        svg.renderDPI = DisplayMetrics.DENSITY_DEFAULT.toFloat()
        var docWidth = svg.documentWidth * displayMetrics.density
        var docHeight = svg.documentHeight * displayMetrics.density

        if (docWidth < 0 || docHeight < 0) {
            val aspectRatio = svg.documentAspectRatio
            if (aspectRatio > 0) {
                val heightForAspect = targetSizeFloat / aspectRatio
                val widthForAspect = targetSizeFloat * aspectRatio
                if (widthForAspect < heightForAspect) {
                    docWidth = widthForAspect
                    docHeight = targetSizeFloat
                } else {
                    docWidth = targetSizeFloat
                    docHeight = heightForAspect
                }
            } else {
                docWidth = targetSizeFloat
                docHeight = targetSizeFloat
            }

            // we didn't take density into account anymore when calculating docWidth
            // and docHeight, so don't scale with it and just let the renderer
            // figure out the scaling
            density = null
        }

        if (docWidth > targetSizeFloat || docHeight > targetSizeFloat) {
            val widthScaler = max(1F, docWidth / targetSizeFloat)
            val heightScaler = max(1F, docHeight / targetSizeFloat)
            val scaler = max(widthScaler, heightScaler)
            docWidth /= scaler
            docHeight /= scaler
            if (density != null) {
                density /= scaler
            }
        }

        val bitmap = Bitmap.createBitmap(round(docWidth).toInt(), round(docHeight).toInt(), Bitmap.Config.ARGB_8888)
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

inline fun <T> JSONArray.mapString(transform: (String) -> T): List<T> {
    return (0 until length()).map { index -> transform(getString(index)) }
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
    val length = if (type == ToastType.ERROR) Toast.LENGTH_LONG else Toast.LENGTH_SHORT

    GlobalScope.launch(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Toast.makeText(this@showToast, message, length).show()
        } else {
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
}

/**
 * Shows an Toast with the openHAB icon. Can be called from the background.
 */
fun Context.showToast(@StringRes message: Int, type: ToastType = ToastType.NORMAL) {
    showToast(getString(message), type)
}

fun Context.hasPermissions(permissions: Array<String>) = permissions.firstOrNull {
    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
} == null

fun Context.getHumanReadableErrorMessage(url: String, httpCode: Int, error: Throwable?, short: Boolean): CharSequence {
    return if (error.hasCause(UnknownHostException::class.java)) {
        getString(
            if (short) R.string.error_short_unable_to_resolve_hostname else R.string.error_unable_to_resolve_hostname)
    } else if (error.hasCause(CertificateExpiredException::class.java)) {
        getString(if (short) R.string.error_short_certificate_expired else R.string.error_certificate_expired)
    } else if (error.hasCause(CertificateNotYetValidException::class.java)) {
        getString(
            if (short) R.string.error_short_certificate_not_valid_yet else R.string.error_certificate_not_valid_yet)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
        error.hasCause(CertificateRevokedException::class.java)) {
        getString(if (short) R.string.error_short_certificate_revoked else R.string.error_certificate_revoked)
    } else if (error.hasCause(SSLPeerUnverifiedException::class.java)) {
        getString(
            if (short) R.string.error_short_certificate_wrong_host else R.string.error_certificate_wrong_host,
            url.toHttpUrlOrNull()?.host
        )
    } else if (error.hasCause(CertPathValidatorException::class.java)) {
        getString(if (short) R.string.error_short_certificate_not_trusted else R.string.error_certificate_not_trusted)
    } else if (error.hasCause(SSLException::class.java) || error.hasCause(SSLHandshakeException::class.java)) {
        getString(if (short) R.string.error_short_connection_sslhandshake_failed else
            R.string.error_connection_sslhandshake_failed)
    } else if (error.hasCause(ConnectException::class.java) || error.hasCause(SocketTimeoutException::class.java)) {
        getString(if (short) R.string.error_short_connection_failed else R.string.error_connection_failed)
    } else if (error.hasCause(IOException::class.java) && error.hasCause(EOFException::class.java)) {
        getString(if (short) R.string.error_short_http_to_https_port else R.string.error_http_to_https_port)
    } else if (httpCode >= 400) {
        if (error?.message == "openHAB is offline") {
            getString(if (short) R.string.error_short_openhab_offline else R.string.error_openhab_offline)
        } else {
            try {
                val resName = if (short) "error_short_http_code_$httpCode" else "error_http_code_$httpCode"
                getString(resources.getIdentifier(resName, "string", packageName), httpCode)
            } catch (e: Resources.NotFoundException) {
                getString(
                    if (short) R.string.error_short_http_connection_failed else R.string.error_http_connection_failed,
                    httpCode
                )
            }
        }
    } else {
        error.let { Log.e(Util.TAG, "REST call to $url failed", it) }
        getString(if (short) R.string.error_short_unknown else R.string.error_unknown, error?.localizedMessage)
    }
}

fun Context.openInAppStore(app: String) {
    val intent = Intent(Intent.ACTION_VIEW, "market://details?id=$app".toUri())
    if (intent.isResolvable(this)) {
        startActivity(intent)
    } else {
        "http://play.google.com/store/apps/details?id=$app".toUri().openInBrowser(this)
    }
}

data class DataUsagePolicy(
    val canDoLargeTransfers: Boolean,
    val loadIconsWithState: Boolean,
    val autoPlayVideos: Boolean,
    val canDoRefreshes: Boolean
)

fun Context.determineDataUsagePolicy(): DataUsagePolicy {
    val isBatterySaverActive = (applicationContext as OpenHabApplication).batterySaverActive
    fun getDataUsagePolicyForBatterySaver() = DataUsagePolicy(
        canDoLargeTransfers = true,
        loadIconsWithState = true,
        autoPlayVideos = false,
        canDoRefreshes = false
    )

    val dataSaverPref = getPrefs().getBoolean(PrefKeys.DATA_SAVER, false)
    if (dataSaverPref || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        return if (isBatterySaverActive) {
            getDataUsagePolicyForBatterySaver()
        } else {
            DataUsagePolicy(!dataSaverPref, !dataSaverPref, !dataSaverPref, !dataSaverPref)
        }
    }
    return when ((applicationContext as OpenHabApplication).systemDataSaverStatus) {
        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> DataUsagePolicy(
            canDoLargeTransfers = false,
            loadIconsWithState = false,
            autoPlayVideos = false,
            canDoRefreshes = false
        )
        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> {
            if (isBatterySaverActive) {
                getDataUsagePolicyForBatterySaver()
            } else {
                DataUsagePolicy(
                    canDoLargeTransfers = true,
                    loadIconsWithState = true,
                    autoPlayVideos = false,
                    canDoRefreshes = true
                )
            }
        }
        else -> {
            if (isBatterySaverActive) {
                getDataUsagePolicyForBatterySaver()
            } else {
                DataUsagePolicy(
                    canDoLargeTransfers = true,
                    loadIconsWithState = true,
                    autoPlayVideos = true,
                    canDoRefreshes = true
                )
            }
        }
    }
}

fun Context.resolveThemedColor(@AttrRes colorAttr: Int, @ColorInt fallbackColor: Int = 0): Int {
    val tv = TypedValue()
    theme.resolveAttribute(colorAttr, tv, true)
    return if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
        tv.data
    } else {
        fallbackColor
    }
}

fun Context.getCurrentWifiSsid() : String? {
    val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    return wifiManager.connectionInfo.let {info ->
        if (info.networkId == -1) null else info.ssid.removeSurrounding("\"")
    }
}

fun Socket.bindToNetworkIfPossible(network: Network?) {
    try {
        network?.bindSocket(this)
    } catch (e: IOException) {
        Log.d(Util.TAG, "Binding socket $this to network $network failed: $e")
    }
}

fun Uri.Builder.appendQueryParameter(key: String, value: Int): Uri.Builder {
    return appendQueryParameter(key, value.toString())
}

fun Uri.Builder.appendQueryParameter(key: String, value: Boolean): Uri.Builder {
    return appendQueryParameter(key, value.toString())
}

fun ServiceInfo.addToPrefs(context: Context) {
    val address = hostAddresses[0]
    val port = port.toString()
    Log.d(Util.TAG, "Service resolved: $address port: $port")

    val wifiSsid = context.getCurrentWifiSsid()

    val config = ServerConfiguration(
        context.getPrefs().getNextAvailableServerId(),
        context.getString(R.string.openhab),
        ServerPath("https://$address:$port", null, null),
        null,
        null,
        null,
        wifiSsid
    )
    config.saveToPrefs(context.getPrefs(), context.getSecretPrefs())
}

fun Intent.isResolvable(context: Context): Boolean {
    return context.packageManager.queryIntentActivities(this, 0).isNotEmpty()
}

/**
 * Removes trailing `.0` from float
 */
fun Float.beautify() = if (this == this.toInt().toFloat()) this.toInt().toString() else this.toString()

fun Menu.getGroupItems(groupId: Int): List<MenuItem> {
    return (0 until size())
        .map { index -> getItem(index) }
        .filter { item -> item.groupId == groupId }
}
