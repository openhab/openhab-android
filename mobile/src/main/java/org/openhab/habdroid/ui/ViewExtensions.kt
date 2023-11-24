/*
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
import android.webkit.WebView
import android.webkit.WebViewDatabase
import android.widget.EditText
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.LabeledValue
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ImageConversionPolicy
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

fun WebView.setUpForConnection(
    connection: Connection,
    url: HttpUrl,
    avoidAuthentication: Boolean = false
) {
    when {
        avoidAuthentication -> { /* Don't add authentication */ }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
            WebViewDatabase.getInstance(context)
                .setHttpAuthUsernamePassword(url.host, "", connection.username, connection.password)
        }
        else -> {
            @Suppress("DEPRECATION")
            setHttpAuthUsernamePassword(url.host, "", connection.username, connection.password)
        }
    }

    with(settings) {
        domStorageEnabled = true
        @SuppressLint("SetJavaScriptEnabled")
        javaScriptEnabled = true
        mixedContentMode = MIXED_CONTENT_COMPATIBILITY_MODE
    }

    webViewClient = ConnectionWebViewClient(connection)
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

fun RemoteViews.duplicate(): RemoteViews {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        RemoteViews(this)
    } else {
        @Suppress("DEPRECATION")
        clone()
    }
}

fun MaterialButton.setTextAndIcon(connection: Connection, mapping: LabeledValue) {
    val iconUrl = mapping.icon?.toUrl(context, true)
    if (iconUrl == null) {
        icon = null
        text = mapping.label
        return
    }
    val iconSize = context.resources.getDimensionPixelSize(R.dimen.section_switch_icon)
    CoroutineScope(Dispatchers.IO + Job()).launch {
        val drawable = try {
            connection.httpClient.get(iconUrl, caching = HttpClient.CachingMode.DEFAULT)
                .asBitmap(iconSize, 0, ImageConversionPolicy.ForceTargetSize).response
                .toDrawable(resources)
        } catch (e: HttpClient.HttpException) {
            Log.d(WidgetAdapter.TAG, "Error getting icon for button", e)
            null
        }
        withContext(Dispatchers.Main) {
            icon = drawable
            text = if (drawable == null) mapping.label else null
        }
    }
}
