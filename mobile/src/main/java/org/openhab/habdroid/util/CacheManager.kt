/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.util

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache

import okhttp3.Cache
import okhttp3.HttpUrl

import java.io.File
import java.io.IOException

class CacheManager private constructor(appContext: Context) {
    val httpCache: Cache = Cache(File(appContext.cacheDir, "http"), (2 * 1024 * 1024).toLong())
    private val bitmapCache: LruCache<HttpUrl, Bitmap>

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

    fun clearCache() {
        bitmapCache.evictAll()
        try {
            httpCache.evictAll()
        } catch (ignored: IOException) {
            // ignored
        }

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
