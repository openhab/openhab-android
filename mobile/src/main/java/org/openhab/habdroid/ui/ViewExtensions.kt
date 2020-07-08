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

package org.openhab.habdroid.ui

import android.content.Intent
import android.os.Build
import android.os.Message
import android.util.TypedValue
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewDatabase
import android.widget.ImageView
import androidx.annotation.AttrRes
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import okhttp3.HttpUrl
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.util.isResolvable
import org.openhab.habdroid.util.openInBrowser
import org.openhab.habdroid.util.resolveThemedColor

/**
 * Sets [SwipeRefreshLayout] color scheme from
 * a list of attributes pointing to color resources
 *
 * @param colorAttrIds color attributes to create color scheme from
 */
fun SwipeRefreshLayout.applyColors(@AttrRes vararg colorAttrIds: Int) {
    val colors = colorAttrIds.map { attr -> context.resolveThemedColor(attr) }.toIntArray()
    setColorSchemeColors(*colors)
}

fun WebView.setUpForConnection(connection: Connection, url: HttpUrl) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val webViewDatabase = WebViewDatabase.getInstance(context)
        webViewDatabase.setHttpAuthUsernamePassword(url.host, "", connection.username, connection.password)
    } else {
        @Suppress("DEPRECATION")
        setHttpAuthUsernamePassword(url.host, "", connection.username, connection.password)
    }

    with(settings) {
        domStorageEnabled = true
        javaScriptEnabled = true
        setSupportMultipleWindows(true)
    }

    webViewClient = ConnectionWebViewClient(connection)
    webChromeClient = object : WebChromeClient() {
        override fun onCreateWindow(view: WebView, dialog: Boolean, userGesture: Boolean, resultMsg: Message): Boolean {
            val href = view.handler.obtainMessage()
            view.requestFocusNodeHref(href)
            href.data.getString("url")?.toUri().openInBrowser(view.context)
            return false
        }
    }
}

fun ImageView.setupHelpIcon(url: String, contentDescriptionRes: Int) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
    val contentDescription = context.getString(contentDescriptionRes)
    if (intent.isResolvable(context)) {
        setOnClickListener { context.startActivity(intent) }
        this.contentDescription = contentDescription
        TooltipCompat.setTooltipText(this, contentDescription)
    } else {
        isVisible = false
    }
}

fun ImageView.updateHelpIconAlpha(isEnabled: Boolean) {
    alpha = if (isEnabled) 1.0f else 0.5f
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
