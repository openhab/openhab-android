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

package org.openhab.habdroid.util

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache

import okhttp3.Cache
import okhttp3.HttpUrl
import org.openhab.habdroid.model.IconFormat

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class CacheManager private constructor(appContext: Context) {
    val httpCache: Cache = Cache(File(appContext.cacheDir, "http"), (2 * 1024 * 1024).toLong())
    private val bitmapCache: LruCache<HttpUrl, Bitmap>
    private val widgetIconDirectory = appContext.getDir("widgeticons", Context.MODE_PRIVATE)

    init {
        // Get max available VM memory, exceeding this amount will throw an
        // OutOfMemory exception. Stored in kilobytes as LruCache takes an
        // int in its constructor.
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        // Use up to 1/8 of the available VM memory for the bitmap cache
        bitmapCache = object : LruCache<HttpUrl, Bitmap>(maxMemory / 8) {
            override fun sizeOf(key: HttpUrl, value: Bitmap): Int {
                return value.byteCount / 1024
            }
        }
    }

    fun getCachedBitmap(url: HttpUrl): Bitmap? {
        return bitmapCache.get(url)
    }

    fun cacheBitmap(url: HttpUrl, bitmap: Bitmap) {
        bitmapCache.put(url, bitmap)
    }

    fun isBitmapCached(url: HttpUrl): Boolean {
        return getCachedBitmap(url) != null
    }

    fun saveWidgetIcon(widgetId: Int, iconData: InputStream, format: IconFormat) {
        FileOutputStream(getWidgetIconFile(widgetId, format)).use {
            iconData.copyTo(it)
        }
    }

    fun removeWidgetIcon(widgetId: Int) {
        IconFormat.values().forEach { format -> getWidgetIconFile(widgetId, format).delete() }
    }

    fun getWidgetIconFormat(widgetId: Int): IconFormat? {
        return IconFormat.values()
            .filter { format -> getWidgetIconFile(widgetId, format).exists() }
            .firstOrNull()
    }

    fun getWidgetIconStream(widgetId: Int): InputStream? {
        return getWidgetIconFormat(widgetId)?.let { format ->
            return FileInputStream(getWidgetIconFile(widgetId, format))
        }
    }

    fun clearCache() {
        bitmapCache.evictAll()
        try {
            httpCache.evictAll()
        } catch (ignored: IOException) {
            // ignored
        }
        widgetIconDirectory?.listFiles()?.forEach { f -> f.delete() }
    }

    private fun getWidgetIconFile(widgetId: Int, format: IconFormat): File {
        val suffix = when (format) {
            IconFormat.Svg -> ".svg"
            IconFormat.Png -> ".png"
        }
        return File(widgetIconDirectory, widgetId.toString() + suffix)
    }

    companion object {
        private var instance: CacheManager? = null

        fun getInstance(context: Context): CacheManager {
            val inst = instance ?: CacheManager(context.applicationContext)
            instance = inst
            return inst
        }
    }
}
