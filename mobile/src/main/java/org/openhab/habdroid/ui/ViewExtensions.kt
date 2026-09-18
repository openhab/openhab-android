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

package org.openhab.habdroid.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
import android.webkit.WebView
import android.webkit.WebViewDatabase
import android.widget.EditText
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.net.toUri
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.openInBrowser
import org.openhab.habdroid.util.resolveThemedColor

/**
 * Sets [SwipeRefreshLayout] color scheme according to colorPrimary and colorAccent
 */
fun SwipeRefreshLayout.applyColors() {
    val colors = listOf(R.attr.colorPrimary, R.attr.colorAccent)
        .map { attr -> context.resolveThemedColor(attr) }
        .toIntArray()
    setColorSchemeColors(*colors)
}

fun WebView.setUpForConnection(connection: Connection) {
    with(settings) {
        domStorageEnabled = true
        @SuppressLint("SetJavaScriptEnabled")
        javaScriptEnabled = true
        mixedContentMode = MIXED_CONTENT_COMPATIBILITY_MODE
    }

    webViewClient = ConnectionWebViewClient(connection)
}

private fun buildWebViewProfileName(serverId: Int) = "server_$serverId"

/**
 * Servers may share an origin (e.g. all myopenhab.org users, or the same local IP at different sites),
 * so HTTP cache, HTTP auth cache, cookies and DOM storage must not be shared between them.
 *
 * Must be called before the WebView is used for anything else.
 */
fun WebView.isolateForServer(serverId: Int) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
        WebViewCompat.setProfile(this, buildWebViewProfileName(serverId))
        return
    }

    // No profile support: At least make sure not to serve cached data or credentials of the previous server
    val prefs = context.getPrefs()
    if (prefs.getInt(PrefKeys.WEBVIEW_LAST_SERVER_ID, serverId) != serverId) {
        clearCache(true)
        WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
    }
    prefs.edit { putInt(PrefKeys.WEBVIEW_LAST_SERVER_ID, serverId) }
}

fun Context.clearWebViewCaches() {
    WebView(this).clearCache(true)
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
        return
    }
    ProfileStore.getInstance().allProfileNames
        .filter { it != Profile.DEFAULT_PROFILE_NAME }
        .forEach { name -> clearWebViewCache(name) }
}

private fun Context.clearWebViewCache(profileName: String) {
    val webView = WebView(this)
    WebViewCompat.setProfile(webView, profileName)
    webView.clearCache(true)
    webView.destroy()
}

fun Context.deleteWebViewDataForServer(serverId: Int) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
        return
    }
    val name = buildWebViewProfileName(serverId)
    val store = ProfileStore.getInstance()
    try {
        store.deleteProfile(name)
    } catch (e: IllegalStateException) {
        // Profile is in use by a WebView. Server IDs are reused, so don't leave the data for the next server.
        store.getProfile(name)?.let { profile ->
            profile.cookieManager.removeAllCookies(null)
            profile.webStorage.deleteAllData()
            clearWebViewCache(name)
        }
    }
}

fun ImageView.setupHelpIcon(url: String, contentDescriptionRes: Int) {
    val contentDescription = context.getString(contentDescriptionRes)
    this.contentDescription = contentDescription
    TooltipCompat.setTooltipText(this, contentDescription)

    setOnClickListener {
        url.toUri().openInBrowser(context)
    }
}

fun EditText.setKeyboardVisible(visible: Boolean) {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    if (visible) {
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    } else {
        imm.hideSoftInputFromWindow(windowToken, 0)
    }
}

fun View.playPressAnimationAndCallBack(postAnimationCallback: () -> Unit) {
    post {
        if (background != null) {
            val centerX = width / 2
            val centerY = height / 2
            DrawableCompat.setHotspot(background, centerX.toFloat(), centerY.toFloat())
        }
        isPressed = true
        isPressed = false
        postAnimationCallback()
    }
}

fun RemoteViews.duplicate(): RemoteViews = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    RemoteViews(this)
} else {
    @Suppress("DEPRECATION")
    clone()
}
