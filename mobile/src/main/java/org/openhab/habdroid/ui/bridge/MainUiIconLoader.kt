/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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

package org.openhab.habdroid.ui.bridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.toDrawable
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openhab.habdroid.model.toOH2IconResource
import org.openhab.habdroid.util.HttpClient
import org.openhab.habdroid.util.ImageConversionPolicy
import org.openhab.habdroid.util.getConnectionFactory
import org.openhab.habdroid.util.svgToBitmap

/**
 * Loads the icons of Main UI's navbar and sidebar. Icons in Main UI's format are loaded the same way as sitemap
 * icons: openHAB icons from the server, Framework7, Material and Iconify icons from Iconify.
 */
class MainUiIconLoader(private val context: Context, private val scope: CoroutineScope) {
    private val cache = mutableMapOf<String, Drawable>()
    private val loading = mutableSetOf<String>()

    /**
     * @param color The color of monochrome icons.
     * @param onLoaded Called once an icon that wasn't loaded yet is available.
     * @return The icon if it's loaded already, null otherwise.
     */
    fun get(icon: BridgeIcon, @ColorInt color: Int, sizePx: Int, onLoaded: () -> Unit): Drawable? {
        val key = "${icon.androidName ?: icon.svg}|$color|$sizePx"
        cache[key]?.let { return it }
        if (loading.add(key)) {
            scope.launch {
                val bitmap = load(icon, color, sizePx)
                loading.remove(key)
                if (bitmap != null) {
                    cache[key] = bitmap.toDrawable(context.resources)
                    onLoaded()
                }
            }
        }
        return null
    }

    private suspend fun load(icon: BridgeIcon, @ColorInt color: Int, sizePx: Int): Bitmap? {
        val name = icon.androidName
        if (name != null) {
            val resource = name.toOH2IconResource() ?: return null
            val connection = context.getConnectionFactory().currentActive?.usableConnection ?: return null
            return try {
                connection.httpClient
                    .get(resource.toUrl(context, false), caching = HttpClient.CachingMode.DEFAULT)
                    .asBitmap(sizePx, color, ImageConversionPolicy.ForceTargetSize)
                    .response
            } catch (e: HttpClient.HttpException) {
                Log.w(TAG, "Could not load icon $name", e)
                null
            }
        }
        val svg = icon.svg ?: return null
        return withContext(Dispatchers.IO) {
            try {
                svg.byteInputStream().svgToBitmap(sizePx, color, ImageConversionPolicy.ForceTargetSize)
            } catch (e: IOException) {
                Log.w(TAG, "Could not draw SVG icon", e)
                null
            }
        }
    }

    companion object {
        private val TAG = MainUiIconLoader::class.java.simpleName
    }
}
