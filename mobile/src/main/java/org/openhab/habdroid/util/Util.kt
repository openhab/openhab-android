/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.preference.PreferenceManager
import android.util.Log
import android.util.TypedValue
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.core.net.toUri
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import es.dmoral.toasty.Toasty

import okhttp3.Headers
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.openhab.habdroid.BuildConfig
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.ParsedState
import org.openhab.habdroid.model.Sitemap
import org.openhab.habdroid.model.toSitemap
import org.w3c.dom.Document

import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.CertificateRevokedException
import java.util.ArrayList
import java.util.Locale

import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException

object Util {
    private val TAG = Util::class.java.simpleName

    val isFlavorStable: Boolean
        get() = BuildConfig.FLAVOR.toLowerCase().contains("stable")

    val isFlavorBeta: Boolean
        get() = !isFlavorStable

    val isFlavorFull: Boolean
        get() = BuildConfig.FLAVOR.toLowerCase().contains("full")

    val isFlavorFoss: Boolean
        get() = !isFlavorFull

    fun normalizeUrl(sourceUrl: String): String {
        var normalizedUrl = ""
        try {
            val url = URL(sourceUrl)
            normalizedUrl = url.toString()
            normalizedUrl = normalizedUrl.replace("\n", "")
            normalizedUrl = normalizedUrl.replace(" ", "")
            if (!normalizedUrl.endsWith("/")) {
                normalizedUrl = "$normalizedUrl/"
            }
        } catch (e: MalformedURLException) {
            Log.d(TAG, "normalizeUrl(): invalid URL '$sourceUrl'")
        }

        return normalizedUrl
    }

    fun parseSitemapList(document: Document): List<Sitemap> {
        val sitemapList = ArrayList<Sitemap>()
        val sitemapNodes = document.getElementsByTagName("sitemap")
        if (sitemapNodes.length > 0) {
            for (i in 0 until sitemapNodes.length) {
                val sitemap = sitemapNodes.item(i).toSitemap()
                if (sitemap != null) {
                    sitemapList.add(sitemap)
                }
            }
        }
        return sitemapList
    }

    fun parseSitemapList(jsonArray: JSONArray): List<Sitemap> {
        val sitemapList = ArrayList<Sitemap>()
        for (i in 0 until jsonArray.length()) {
            try {
                val sitemap = jsonArray.getJSONObject(i).toSitemap()
                if (sitemap != null && (sitemap.name != "_default" || jsonArray.length() == 1)) {
                    sitemapList.add(sitemap)
                }
            } catch (e: JSONException) {
                Log.d(TAG, "Error while parsing sitemap", e)
            }

        }
        return sitemapList
    }

    fun sortedSitemapList(sitemapList: List<Sitemap>, defaultSitemapName: String): List<Sitemap> {
        // Sort by sitename label, the default sitemap should be the first one
        return sitemapList.sortedWith(object: Comparator<Sitemap> {
            override fun compare(lhs: Sitemap, rhs: Sitemap): Int = when {
                lhs.name == defaultSitemapName -> -1
                rhs.name == defaultSitemapName -> 1
                else -> lhs.label.compareTo(rhs.label, true)
            }
        })
    }

    fun sitemapExists(sitemapList: List<Sitemap>, sitemapName: String): Boolean {
        return sitemapList.any { sitemap -> sitemap.name == sitemapName }
    }

    fun getSitemapByName(sitemapList: List<Sitemap>, sitemapName: String): Sitemap? {
        return sitemapList.firstOrNull{ sitemap -> sitemap.name == sitemapName }
    }

    @StyleRes
    @JvmOverloads
    fun getActivityThemeId(activity: Activity, theme: String? = null): Int {
        var actualTheme = theme
        if (actualTheme == null) {
            actualTheme= PreferenceManager.getDefaultSharedPreferences(activity)
                    .getString(Constants.PREFERENCE_THEME, activity.getString(R.string.theme_value_light))
        }

        when (actualTheme) {
            activity.getString(R.string.theme_value_dark) -> return R.style.HABDroid_Dark
            activity.getString(R.string.theme_value_black) -> return R.style.HABDroid_Black
            activity.getString(R.string.theme_value_basic_ui) -> return R.style.HABDroid_Basic_ui
            activity.getString(R.string.theme_value_basic_ui_dark) -> return R.style.HABDroid_Basic_ui_dark
            else -> return R.style.HABDroid_Light
        }
    }

    fun exceptionHasCause(error: Throwable?, cause: Class<out Throwable>): Boolean {
        var actualError = error
        while (actualError != null) {
            if (actualError.javaClass == cause) {
                return true
            }
            actualError = actualError.cause
        }
        return false
    }

    fun sendItemCommand(client: AsyncHttpClient, item: Item?,
                        state: ParsedState.NumberState?) {
        if (item == null || state == null) {
            return
        }
        if (item.isOfTypeOrGroupType(Item.Type.NumberWithDimension)) {
            // For number items, include unit (if present) in command
            sendItemCommand(client, item, state.toString(Locale.US))
        } else {
            // For all other items, send the plain value
            sendItemCommand(client, item, state.formatValue())
        }
    }

    fun sendItemCommand(client: AsyncHttpClient, item: Item?, command: String) {
        if (item == null) {
            return
        }
        sendItemCommand(client, item.link, command)
    }

    fun sendItemCommand(client: AsyncHttpClient, itemUrl: String?, command: String?) {
        if (itemUrl == null || command == null) {
            return
        }
        client.post(itemUrl, command, "text/plain;charset=UTF-8",
                object : AsyncHttpClient.StringResponseHandler() {
                    override fun onFailure(request: Request, statusCode: Int, error: Throwable) {
                        Log.e(TAG, "Sending command $command to $itemUrl failed: status $statusCode", error);
                    }

                    override fun onSuccess(response: String, headers: Headers) {
                        Log.d(TAG, "Command '$command' was sent successfully to $itemUrl")
                    }
                })
    }

    /**
     * Replaces everything after the first clearTextCharCount chars with asterisks
     * @param string to obfuscate
     * @param clearTextCharCount leave the first clearTextCharCount in clear text
     * @return obfuscated string
     */
    @JvmOverloads
    fun obfuscateString(string: String, clearTextCharCount: Int = 3): String {
        var clearTextCharCount = Math.min(string.length, clearTextCharCount)
        return string.substring(0, clearTextCharCount) + string.substring(clearTextCharCount).replace(".".toRegex(), "*")
    }

    fun openInBrowser(context: Context, url: String?) {
        if (url.isNullOrEmpty()) {
            Log.e(TAG, "Got empty url")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            context.startActivity(intent);
        } catch (e: ActivityNotFoundException) {
            Toasty.error(context, R.string.error_no_browser_found, Toasty.LENGTH_LONG).show()
        }
    }

    /**
     * Returns vibration pattern for notifications that can be passed to
     * [}][androidx.core.app.NotificationCompat.Builder.setVibrate]
     */
    fun getNotificationVibrationPattern(context: Context): LongArray {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val vibration = prefs.getString(Constants.PREFERENCE_NOTIFICATION_VIBRATION, "")
        return if (context.getString(R.string.settings_notification_vibration_value_short) == vibration) {
            longArrayOf(0, 500, 500)
        } else if (context.getString(R.string.settings_notification_vibration_value_long) == vibration) {
            longArrayOf(0, 1000, 1000)
        } else if (context.getString(R.string.settings_notification_vibration_value_twice) == vibration) {
            longArrayOf(0, 1000, 1000, 1000, 1000)
        } else {
            longArrayOf(0)
        }
    }

    fun getHumanReadableErrorMessage(context: Context, url: String,
                                     statusCode: Int, error: Throwable): CharSequence {
        if (statusCode >= 400) {
            if (error.message == "openHAB is offline") {
                return context.getString(R.string.error_openhab_offline)
            } else {
                val resourceId: Int
                try {
                    resourceId = context.resources.getIdentifier(
                            "error_http_code_$statusCode",
                            "string", context.packageName)
                    return context.getString(resourceId)
                } catch (e: Resources.NotFoundException) {
                    return context.getString(R.string.error_http_connection_failed, statusCode)
                }

            }
        } else if (error is UnknownHostException) {
            Log.e(TAG, "Unable to resolve hostname")
            return context.getString(R.string.error_unable_to_resolve_hostname)
        } else if (error is SSLException) {
            // if ssl exception, check for some common problems
            return if (Util.exceptionHasCause(error, CertPathValidatorException::class.java)) {
                context.getString(R.string.error_certificate_not_trusted)
            } else if (Util.exceptionHasCause(error, CertificateExpiredException::class.java)) {
                context.getString(R.string.error_certificate_expired)
            } else if (Util.exceptionHasCause(error, CertificateNotYetValidException::class.java)) {
                context.getString(R.string.error_certificate_not_valid_yet)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Util.exceptionHasCause(error, CertificateRevokedException::class.java)) {
                context.getString(R.string.error_certificate_revoked)
            } else if (Util.exceptionHasCause(error, SSLPeerUnverifiedException::class.java)) {
                String.format(context.getString(R.string.error_certificate_wrong_host),
                        url.toUri().host)
            } else {
                context.getString(R.string.error_connection_sslhandshake_failed)
            }
        } else if (error is ConnectException || error is SocketTimeoutException) {
            return context.getString(R.string.error_connection_failed)
        } else if (error is IOException && Util.exceptionHasCause(error, EOFException::class.java)) {
            return context.getString(R.string.error_http_to_https_port)
        } else {
            Log.e(TAG, "REST call to $url failed", error)
            return error.message ?: ""
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
}
