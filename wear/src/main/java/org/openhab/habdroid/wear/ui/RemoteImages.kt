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

package org.openhab.habdroid.wear.ui

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.LocalContentColor
import kotlinx.coroutines.delay
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.IconResource
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ImageConversionPolicy
import org.openhab.habdroid.wear.util.toUrl

private val bitmapCache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

/**
 * Loads a bitmap from the server. Icons are cached in memory, so they don't flicker when a page is updated.
 */
suspend fun Connection.loadBitmap(
    url: String,
    sizePixels: Int,
    fallbackColor: Int,
    policy: ImageConversionPolicy = ImageConversionPolicy.PreferTargetSize,
    cachingMode: HttpClient.CachingMode = HttpClient.CachingMode.DEFAULT
): Bitmap? {
    val cacheKey = "$url@$sizePixels"
    if (cachingMode != HttpClient.CachingMode.AVOID_CACHE) {
        bitmapCache.get(cacheKey)?.let { return it }
    }
    return try {
        httpClient.get(url, caching = cachingMode)
            .asBitmap(sizePixels, fallbackColor, policy)
            .response
            .also { bitmap -> bitmapCache.put(cacheKey, bitmap) }
    } catch (e: HttpClient.HttpException) {
        Log.d("RemoteImages", "Loading $url failed", e)
        null
    }
}

@Composable
fun WidgetIcon(
    icon: IconResource?,
    connection: Connection,
    serverFlags: Int,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    icon ?: return
    val sizePixels = with(LocalDensity.current) { size.roundToPx() }
    val fallbackColor = LocalContentColor.current.toArgb()
    val url = icon.toUrl(serverFlags, sizePixels)
    val bitmap by produceState<ImageBitmap?>(bitmapCache.get("$url@$sizePixels")?.asImageBitmap(), url, connection) {
        value = connection.loadBitmap(url, sizePixels, fallbackColor)?.asImageBitmap()
    }
    bitmap?.let {
        Image(bitmap = it, contentDescription = null, modifier = modifier.size(size), contentScale = ContentScale.Fit)
    }
}

/**
 * Shows an image from the server, e.g. of an image or chart widget.
 *
 * @param refreshMillis Interval to reload the image, 0 to load it once
 */
@Composable
fun RemoteImage(
    url: String,
    connection: Connection,
    widthPixels: Int,
    refreshMillis: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val fallbackColor = LocalContentColor.current.toArgb()
    val bitmap by produceState<ImageBitmap?>(bitmapCache.get("$url@$widthPixels")?.asImageBitmap(), url, connection) {
        var cachingMode = HttpClient.CachingMode.DEFAULT
        while (true) {
            connection.loadBitmap(url, widthPixels, fallbackColor, ImageConversionPolicy.PreferSourceSize, cachingMode)
                ?.let { value = it.asImageBitmap() }
            if (refreshMillis <= 0) {
                break
            }
            delay(refreshMillis.toLong())
            cachingMode = HttpClient.CachingMode.AVOID_CACHE
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.FillWidth
        )
    }
}
