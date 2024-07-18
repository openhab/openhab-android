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

package org.openhab.habdroid.util

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import com.google.android.material.color.DynamicColors
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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.openhab.habdroid.R
import org.openhab.habdroid.core.OpenHabApplication
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.core.connection.DefaultConnection
import org.openhab.habdroid.model.ServerConfiguration
import org.openhab.habdroid.model.ServerPath
import org.openhab.habdroid.model.ServerProperties
import org.openhab.habdroid.util.Util.TAG
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
    if (isNullOrEmpty()) {
        return null
    }
    return try {
        val url = this
            .replace("\n", "")
            .replace(" ", "")
            .toHttpUrl()
            .toString()
        if (url.endsWith("/")) url else "$url/"
    } catch (e: IllegalArgumentException) {
        Log.d(TAG, "toNormalizedUrl(): Invalid URL '$this'")
        null
    }
}

fun String?.orDefaultIfEmpty(defaultValue: String) = if (isNullOrEmpty()) defaultValue else this

fun Uri?.openInBrowser(context: Context, intentFlags: Int = 0) {
    if (this == null) {
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, this)
    if (intentFlags != 0) {
        intent.addFlags(intentFlags)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.d(TAG, "Unable to open url in browser: $intent")
        context.showToast(R.string.error_no_browser_found, Toast.LENGTH_LONG)
    }
}

fun HttpUrl.toRelativeUrl(): String {
    val base = resolve("/")
    return this.toString().substring(base.toString().length - 1)
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
fun ResponseBody.toBitmap(
    targetSize: Int,
    @ColorInt fallbackColor: Int,
    conversionPolicy: ImageConversionPolicy
): Bitmap {
    if (!contentType().isSvg()) {
        val bitmap = BitmapFactory.decodeStream(byteStream())
            ?: throw IOException(
                "Bitmap with decoding failed: content type: ${contentType()}, length: ${contentLength()}"
            )
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

    return byteStream().svgToBitmap(targetSize, fallbackColor, conversionPolicy)
}

fun MediaType?.isSvg(): Boolean {
    return this != null && this.type == "image" && this.subtype.contains("svg")
}

@Throws(IOException::class)
fun InputStream.svgToBitmap(
    targetSize: Int,
    @ColorInt fallbackColor: Int,
    conversionPolicy: ImageConversionPolicy
): Bitmap {
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

        val options = RenderOptions()
        options.css(" * { color: ${String.format("#%06X", 0xFFFFFF and fallbackColor)}; }")
        svg.renderToCanvas(canvas, options)
        bitmap
    } catch (e: Exception) {
        throw IOException("SVG decoding failed", e)
    }
}

fun NodeList.forEach(action: (Node) -> Unit) = (0 until length).forEach { index -> action(item(index)) }

fun JSONArray.forEach(action: (JSONObject) -> Unit) =
    (0 until length()).forEach { index -> action(getJSONObject(index)) }

inline fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> {
    return (0 until length()).map { index -> transform(getJSONObject(index)) }
}

inline fun <T> JSONArray.mapString(transform: (String) -> T): List<T> {
    return (0 until length()).map { index -> transform(getString(index)) }
}

fun JSONObject.optDoubleOrNull(key: String): Double? {
    return if (has(key)) getDouble(key) else null
}

fun JSONObject.optFloatOrNull(key: String): Float? {
    return if (has(key)) getDouble(key).toFloat() else null
}

fun JSONObject.optIntOrNull(key: String): Int? {
    return if (has(key)) getInt(key) else null
}

fun JSONObject.optBooleanOrNull(key: String): Boolean? {
    return if (has(key)) getBoolean(key) else null
}

fun JSONObject.optStringOrNull(key: String): String? {
    return optStringOrFallback(key, null)
}

fun JSONObject.optStringOrFallback(key: String, fallback: String?): String? {
    return if (has(key)) getString(key) else fallback
}

fun String.toJsonArrayOrNull() = try {
    JSONArray(this)
} catch (e: Exception) {
    null
}

fun Context.getPrefs(): SharedPreferences {
    return PreferenceManager.getDefaultSharedPreferences(this)
}

fun Context.getSecretPrefs(): SharedPreferences {
    return (applicationContext as OpenHabApplication).secretPrefs
}

/**
 * Shows an Toast and can be called from the background.
 */
fun Context.showToast(message: CharSequence, length: Int = Toast.LENGTH_SHORT) {
    GlobalScope.launch(Dispatchers.Main) {
        Toast.makeText(this@showToast, message, length).show()
    }
}

/**
 * Shows an Toast and can be called from the background.
 */
fun Context.showToast(@StringRes message: Int, length: Int = Toast.LENGTH_SHORT) {
    showToast(getString(message), length)
}

fun Context.hasPermissions(permissions: Array<String>) = permissions.firstOrNull {
    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
} == null

fun Context.getHumanReadableErrorMessage(url: String, httpCode: Int, error: Throwable?, short: Boolean): CharSequence {
    return if (error.hasCause(UnknownHostException::class.java)) {
        getString(
            if (short) R.string.error_short_unable_to_resolve_hostname else R.string.error_unable_to_resolve_hostname
        )
    } else if (error.hasCause(CertificateExpiredException::class.java)) {
        getString(if (short) R.string.error_short_certificate_expired else R.string.error_certificate_expired)
    } else if (error.hasCause(CertificateNotYetValidException::class.java)) {
        getString(
            if (short) R.string.error_short_certificate_not_valid_yet else R.string.error_certificate_not_valid_yet
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
        error.hasCause(CertificateRevokedException::class.java)
    ) {
        getString(if (short) R.string.error_short_certificate_revoked else R.string.error_certificate_revoked)
    } else if (error.hasCause(SSLPeerUnverifiedException::class.java)) {
        getString(
            if (short) R.string.error_short_certificate_wrong_host else R.string.error_certificate_wrong_host,
            url.toHttpUrlOrNull()?.host
        )
    } else if (error.hasCause(CertPathValidatorException::class.java)) {
        getString(if (short) R.string.error_short_certificate_not_trusted else R.string.error_certificate_not_trusted)
    } else if (error.hasCause(SSLException::class.java) || error.hasCause(SSLHandshakeException::class.java)) {
        getString(
            if (short) {
                R.string.error_short_connection_sslhandshake_failed
            } else {
                R.string.error_connection_sslhandshake_failed
            }
        )
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
        error.let { Log.e(TAG, "REST call to $url failed", it) }
        getString(if (short) R.string.error_short_unknown else R.string.error_unknown, error?.localizedMessage)
    }
}

fun Context.openInAppStore(app: String) {
    val intent = Intent(Intent.ACTION_VIEW, "market://details?id=$app".toUri())
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        "http://play.google.com/store/apps/details?id=$app".toUri().openInBrowser(this)
    }
}

data class DataUsagePolicy(
    val canDoLargeTransfers: Boolean,
    val loadIconsWithState: Boolean,
    val autoPlayVideos: Boolean,
    val canDoRefreshes: Boolean
)

fun Context.determineDataUsagePolicy(conn: Connection? = null): DataUsagePolicy {
    val appContext = applicationContext as OpenHabApplication
    var canDoLargeTransfers = true
    var loadIconsWithState = true
    var autoPlayVideos = true
    var canDoRefreshes = true

    if (appContext.batterySaverActive) {
        autoPlayVideos = false
        canDoRefreshes = false
    }

    val dataSaverPref = getPrefs().getBoolean(PrefKeys.DATA_SAVER, false)
    if (dataSaverPref || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        canDoLargeTransfers = canDoLargeTransfers && !dataSaverPref
        loadIconsWithState = loadIconsWithState && !dataSaverPref
        autoPlayVideos = autoPlayVideos && !dataSaverPref
        canDoRefreshes = canDoRefreshes && !dataSaverPref
    } else {
        val isMetered = conn is DefaultConnection && conn.isMetered
        val dataSaverState = appContext.systemDataSaverStatus

        when {
            dataSaverState == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED && isMetered -> {
                canDoLargeTransfers = false
                loadIconsWithState = false
                autoPlayVideos = false
                canDoRefreshes = false
            }
            dataSaverState == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED && isMetered -> {
                autoPlayVideos = false
            }
        }
    }
    return DataUsagePolicy(canDoLargeTransfers, loadIconsWithState, autoPlayVideos, canDoRefreshes)
}

@ColorInt
fun Context.resolveThemedColor(@AttrRes colorAttr: Int, @ColorInt fallbackColor: Int = 0): Int {
    val tv = TypedValue()
    theme.resolveAttribute(colorAttr, tv, true)
    return if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
        tv.data
    } else {
        fallbackColor
    }
}

@ColorRes
fun Context.resolveThemedColorToResource(@AttrRes colorAttr: Int, @ColorRes fallbackColorRes: Int = 0): Int {
    val ta = obtainStyledAttributes(intArrayOf(colorAttr))
    return ta.getResourceId(0, fallbackColorRes).also { ta.recycle() }
}

fun Context.getChartTheme(serverFlags: Int): CharSequence {
    val tv = TypedValue()
    if (serverFlags and ServerProperties.SERVER_FLAG_TRANSPARENT_CHARTS == 0) {
        theme.resolveAttribute(R.attr.chartTheme, tv, true)
    } else {
        theme.resolveAttribute(R.attr.transparentChartTheme, tv, true)
    }

    return tv.string
}

fun Context.isDarkModeActive(): Boolean {
    return when (getPrefs().getDayNightMode(this)) {
        AppCompatDelegate.MODE_NIGHT_NO -> false
        AppCompatDelegate.MODE_NIGHT_YES -> true
        else -> {
            val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            currentNightMode != Configuration.UI_MODE_NIGHT_NO
        }
    }
}

enum class IconBackground {
    APP_THEME,
    OS_THEME,
    LIGHT,
    DARK
}

@ColorInt
fun Context.getIconFallbackColor(iconBackground: IconBackground) = when (iconBackground) {
    IconBackground.APP_THEME -> resolveThemedColor(R.attr.colorOnBackground)
    IconBackground.OS_THEME -> {
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = currentNightMode != Configuration.UI_MODE_NIGHT_NO
        val colorRes = if (isDark) {
            R.color.on_background_default_theme_dark
        } else {
            R.color.on_background_default_theme_light
        }
        ContextCompat.getColor(this, colorRes)
    }
    IconBackground.LIGHT -> ContextCompat.getColor(this, R.color.on_background_default_theme_light)
    IconBackground.DARK -> ContextCompat.getColor(this, R.color.on_background_default_theme_dark)
}

fun Context.loadActiveServerConfig(): ServerConfiguration? {
    val activeServerId = getPrefs().getActiveServerId()
    return ServerConfiguration.load(getPrefs(), getSecretPrefs(), activeServerId)
}

fun Activity.shouldUseDynamicColors(): Boolean {
    val colorScheme = getPrefs().getStringOrEmpty(PrefKeys.COLOR_SCHEME)
    return DynamicColors.isDynamicColorAvailable() && colorScheme == getString(R.string.color_scheme_value_dynamic)
}

fun Activity.applyUserSelectedTheme() {
    setTheme(getActivityThemeId())
    if (shouldUseDynamicColors()) {
        DynamicColors.applyToActivityIfAvailable(this)
    }
}

@StyleRes fun Context.getActivityThemeId(): Int {
    val isBlackTheme = getPrefs().getStringOrNull(PrefKeys.THEME) == getString(R.string.theme_value_black)
    val colorScheme = getPrefs().getStringOrEmpty(PrefKeys.COLOR_SCHEME)
    val basicUiScheme = getString(R.string.color_scheme_value_basicui)
    return when {
        colorScheme == basicUiScheme && isBlackTheme -> R.style.openHAB_DayNight_Black_basicui
        colorScheme == basicUiScheme -> R.style.openHAB_DayNight_basicui
        isBlackTheme -> R.style.openHAB_DayNight_Black_orange
        else -> R.style.openHAB_DayNight_orange
    }
}

fun Context.getCurrentWifiSsid(attributionTag: String): String? {
    val wifiManager = getWifiManager(attributionTag)
    // TODO: Replace deprecated function
    @Suppress("DEPRECATION")
    return wifiManager.connectionInfo.let { info ->
        if (info.networkId == -1) null else info.ssid.removeSurrounding("\"")
    }
}

fun Context.withAttribution(tag: String): Context {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        createAttributionContext(tag)
    } else {
        this
    }
}

fun Context.getWifiManager(attributionTag: String): WifiManager {
    // Android < N requires applicationContext for getting WifiManager, otherwise leaks may occur
    val context = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        applicationContext
    } else {
        withAttribution(attributionTag)
    }

    return context.getSystemService(Context.WIFI_SERVICE) as WifiManager
}

fun Socket.bindToNetworkIfPossible(network: Network?) {
    try {
        network?.bindSocket(this)
    } catch (e: IOException) {
        Log.d(TAG, "Binding socket $this to network $network failed: $e")
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
    Log.d(TAG, "Service resolved: $address port: $port")

    val config = ServerConfiguration(
        context.getPrefs().getNextAvailableServerId(),
        context.getString(R.string.openhab),
        ServerPath("https://$address:$port", null, null),
        null,
        null,
        null,
        null,
        false,
        null,
        null
    )
    config.saveToPrefs(context.getPrefs(), context.getSecretPrefs())
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

fun PackageManager.isInstalled(app: String): Boolean {
    return try {
        // Some devices return `null` for getApplicationInfo()
        @Suppress("UNNECESSARY_SAFE_CALL")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplicationInfo(app, PackageManager.ApplicationInfoFlags.of(0))?.enabled == true
        } else {
            getApplicationInfo(app, 0)?.enabled == true
        }
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

val PendingIntent_Immutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    PendingIntent.FLAG_IMMUTABLE
} else {
    0
}

val PendingIntent_Mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    PendingIntent.FLAG_MUTABLE
} else {
    0
}

inline fun <reified T> Intent.parcelable(key: String): T? {
    setExtrasClassLoader(T::class.java.classLoader)
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelableExtra(key, T::class.java)
        else ->
            @Suppress("DEPRECATION")
            getParcelableExtra(key) as? T
    }
}

inline fun <reified T> Intent.parcelableArrayList(key: String): List<T>? {
    setExtrasClassLoader(T::class.java.classLoader)
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelableArrayListExtra(key, T::class.java)
        else ->
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra(key)
    }
}

inline fun <reified T> Bundle.parcelable(key: String): T? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelable(key, T::class.java)
    else ->
        @Suppress("DEPRECATION")
        getParcelable(key) as? T
}

inline fun <reified T> Bundle.parcelableArrayList(key: String): List<T>? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelableArrayList(key, T::class.java)
    else ->
        @Suppress("DEPRECATION")
        getParcelableArrayList(key)
}
