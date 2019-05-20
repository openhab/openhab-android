package org.openhab.habdroid.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.preference.PreferenceManager
import android.util.Log
import es.dmoral.toasty.Toasty
import org.json.JSONArray
import org.json.JSONObject
import org.openhab.habdroid.R
import org.w3c.dom.Node
import org.w3c.dom.NodeList
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
        return if (url.endsWith("/")) "$url/" else url
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