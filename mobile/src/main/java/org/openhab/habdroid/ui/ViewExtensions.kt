/*
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
import android.webkit.WebView
import android.webkit.WebViewDatabase
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.net.toUri
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.slider.Slider
import kotlin.math.abs
import okhttp3.HttpUrl
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
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
    avoidAuthentication: Boolean = false,
    progressCallback: (progress: Int) -> Unit
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
    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            progressCallback(newProgress)
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

fun RemoteViews.duplicate(): RemoteViews {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        RemoteViews(this)
    } else {
        @Suppress("DEPRECATION")
        clone()
    }
}

fun Slider.setup(from: Float, to: Float, step: Float, widgetValue: Float) {
    // Fix "The stepSize must be 0, or a factor of the valueFrom-valueTo range" exception
    valueTo = to - (to - from).rem(step)
    valueFrom = from
    stepSize = step

    // Fix "Value must be equal to valueFrom plus a multiple of stepSize when using stepSize"
    val stepCount = (abs(valueTo - valueFrom) / stepSize).toInt()
    var closetValue = valueFrom
    var closestDelta = Float.MAX_VALUE
    (0..stepCount).map { index ->
        val stepValue = valueFrom + index * stepSize
        if (abs(widgetValue - stepValue) < closestDelta) {
            closetValue = stepValue
            closestDelta = abs(widgetValue - stepValue)
        }
    }

    isTickVisible = stepCount <= 12

    Log.d(
        WidgetAdapter.TAG,
        "Slider: valueFrom = $valueFrom, valueTo = $valueTo, " +
            "stepSize = $stepSize, stepCount = $stepCount, widgetValue = $widgetValue, " +
            "closetValue = $closetValue, closestDelta = $closestDelta"
    )

    value = closetValue
}
