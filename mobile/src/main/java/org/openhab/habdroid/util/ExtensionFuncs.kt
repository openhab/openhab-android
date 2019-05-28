package org.openhab.habdroid.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.preference.PreferenceManager
import android.util.DisplayMetrics
import android.util.Log
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import es.dmoral.toasty.Toasty
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.openhab.habdroid.R
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL

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
    try {
        val url = URL(orEmpty())
                .toString()
                .replace("\n", "")
                .replace(" ", "")
        return if (url.endsWith("/")) url else "$url/"
    } catch (e: MalformedURLException) {
        Log.d(Util.TAG, "normalizeUrl(): invalid URL '$this'")
        return ""
    }
}

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
    val contentType = contentType()
    val isSvg = contentType != null
                && contentType.type() == "image"
                && contentType.subtype().contains("svg")
    if (!isSvg) {
        val bitmap = BitmapFactory.decodeStream(byteStream())
        if (bitmap == null) {
            throw IOException("Bitmap decoding failed")
        }
        if (!enforceSize) {
            return bitmap
        }
        return Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, false)
    }

    return try {
        val svg = SVG.getFromInputStream(byteStream())
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
            val scaleHeigth = targetSize.toFloat() / docHeight
            density = (scaleWidth + scaleHeigth) / 2
        }

        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (density != null) {
            canvas.scale(density, density)
        }
        svg.renderToCanvas(canvas)
        return bitmap
    } catch (e: SVGParseException) {
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

inline fun Context.getPrefs(): SharedPreferences {
    return PreferenceManager.getDefaultSharedPreferences(this)
}