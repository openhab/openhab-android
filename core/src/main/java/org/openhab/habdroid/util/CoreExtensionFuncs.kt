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

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Network
import android.net.Uri
import android.util.DisplayMetrics
import android.util.Log
import androidx.annotation.ColorInt
import androidx.preference.PreferenceManager
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import kotlin.math.max
import kotlin.math.round
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Node
import org.w3c.dom.NodeList

private const val TAG = "CoreExtensionFuncs"

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

fun Float.asColorTemperatureToKelvin(): Float = if (this < 1000) {
    // likely Mirek
    1000000F / this
} else {
    this
}

fun NodeList.forEach(action: (Node) -> Unit) = (0 until length).forEach { index -> action(item(index)) }

fun JSONArray.forEach(action: (JSONObject) -> Unit) =
    (0 until length()).forEach { index -> action(getJSONObject(index)) }

inline fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> = (0 until length()).map { index ->
    transform(getJSONObject(index))
}

inline fun <T> JSONArray.mapString(transform: (String) -> T): List<T> = (0 until length()).map { index ->
    transform(getString(index))
}

fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key)) getDouble(key) else null

fun JSONObject.optFloatOrNull(key: String): Float? = if (has(key)) getDouble(key).toFloat() else null

fun JSONObject.optIntOrNull(key: String): Int? = if (has(key)) getInt(key) else null

fun JSONObject.optBooleanOrNull(key: String): Boolean? = if (has(key)) getBoolean(key) else null

fun JSONObject.optStringOrNull(key: String): String? = optStringOrFallback(key, null)

fun JSONObject.optStringOrFallback(key: String, fallback: String?): String? = if (has(key)) getString(key) else fallback

fun String.toJsonArrayOrNull() = try {
    JSONArray(this)
} catch (e: Exception) {
    null
}

fun Context.getPrefs(): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

fun Socket.bindToNetworkIfPossible(network: Network?) {
    try {
        network?.bindSocket(this)
    } catch (e: IOException) {
        Log.d(TAG, "Binding socket $this to network $network failed: $e")
    }
}

fun Uri.Builder.appendQueryParameter(key: String, value: Int): Uri.Builder = appendQueryParameter(key, value.toString())

fun Uri.Builder.appendQueryParameter(key: String, value: Boolean): Uri.Builder =
    appendQueryParameter(key, value.toString())

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

fun MediaType?.isSvg(): Boolean = this != null && this.type == "image" && this.subtype.contains("svg")

@Throws(IOException::class)
fun InputStream.svgToBitmap(
    targetSize: Int,
    @ColorInt fallbackColor: Int,
    conversionPolicy: ImageConversionPolicy
): Bitmap = try {
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
