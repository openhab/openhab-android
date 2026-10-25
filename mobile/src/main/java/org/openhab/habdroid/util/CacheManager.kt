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

package org.openhab.habdroid.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.ColorInt
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.openhab.habdroid.model.IconFormat
import org.openhab.habdroid.model.IconResource

class CacheManager private constructor(appContext: Context) {
    val httpCache: Cache = Cache(File(appContext.cacheDir, "http"), (10 * 1024 * 1024).toLong())
    private val iconBitmapCache: BitmapCache
    private val temporaryBitmapCache: BitmapCache
    private val notificationBitmapCache: NotificationBitmapCache
    private val widgetIconDirectory = appContext.getDir("widgeticons", Context.MODE_PRIVATE)
    private val notificationImageDirectory = File(appContext.filesDir, "notification_images")

    init {
        // Get max available VM memory, exceeding this amount will throw an
        // OutOfMemory exception. Stored in kilobytes as LruCache takes an
        // int in its constructor.
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        // Use up to 2/8 of the available VM memory for the bitmap cache
        iconBitmapCache = BitmapCache(maxMemory / 8)
        temporaryBitmapCache = BitmapCache(maxMemory / 8)
        notificationBitmapCache = NotificationBitmapCache(maxMemory / 16)
    }

    fun getCachedNotificationImage(persistedId: String, createdTimestamp: Long): Bitmap? {
        if (persistedId.isEmpty()) {
            return null
        }
        val now = System.currentTimeMillis()
        if (createdTimestamp > 0L && now - createdTimestamp > NOTIFICATION_IMAGE_TTL_MS) {
            notificationBitmapCache.remove(persistedId)
            getNotificationImageFile(persistedId).delete()
            return null
        }
        notificationBitmapCache.get(persistedId)?.let { return it }

        val file = getNotificationImageFile(persistedId)
        if (!file.exists()) {
            return null
        }
        val effectiveTimestamp = if (createdTimestamp > 0L) createdTimestamp else file.lastModified()
        if (now - effectiveTimestamp > NOTIFICATION_IMAGE_TTL_MS) {
            file.delete()
            return null
        }
        return try {
            BitmapFactory.decodeFile(file.absolutePath)?.also { bitmap ->
                notificationBitmapCache.put(persistedId, bitmap)
            }
        } catch (ignored: OutOfMemoryError) {
            null
        }
    }

    fun saveNotificationImage(persistedId: String, bitmap: Bitmap, createdTimestamp: Long) {
        if (persistedId.isEmpty()) {
            return
        }
        val now = System.currentTimeMillis()
        notificationBitmapCache.put(persistedId, bitmap)
        if (createdTimestamp > 0L && now - createdTimestamp > NOTIFICATION_IMAGE_TTL_MS) {
            return
        }
        try {
            if (!notificationImageDirectory.exists()) {
                notificationImageDirectory.mkdirs()
            }
            val targetFile = getNotificationImageFile(persistedId)
            FileOutputStream(targetFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            val effectiveTimestamp = if (createdTimestamp > 0L) createdTimestamp else now
            targetFile.setLastModified(effectiveTimestamp)
            pruneExpiredNotificationImages(now)
        } catch (ignored: IOException) {
            // ignored
        }
    }

    private fun pruneExpiredNotificationImages(now: Long) {
        notificationImageDirectory.listFiles()?.forEach { file ->
            if (now - file.lastModified() > NOTIFICATION_IMAGE_TTL_MS) {
                file.delete()
            }
        }
    }

    private fun getNotificationImageFile(persistedId: String): File {
        val safeName = persistedId.replace(SAFE_FILENAME_REGEX, "_")
        return File(notificationImageDirectory, "$safeName.jpg")
    }

    fun getCachedBitmap(url: HttpUrl, @ColorInt fallbackColor: Int): Bitmap? {
        val key = CacheKey(url, fallbackColor)
        return targetCache(url).get(key)
    }

    fun cacheBitmap(url: HttpUrl, bitmap: Bitmap, @ColorInt fallbackColor: Int) {
        val key = CacheKey(url, fallbackColor)
        targetCache(url).put(key, bitmap)
    }

    private fun targetCache(url: HttpUrl): BitmapCache = if (url.isIconUrl()) {
        iconBitmapCache
    } else {
        temporaryBitmapCache
    }

    private fun HttpUrl.isIconUrl() = host == IconResource.ICONIFY_API_URL ||
        (pathSegments.firstOrNull() == "icon" && pathSegments[1].isNotEmpty())

    fun isBitmapCached(url: HttpUrl, @ColorInt fallbackColor: Int): Boolean =
        getCachedBitmap(url, fallbackColor) != null

    fun saveWidgetIcon(widgetId: Int, iconData: InputStream, format: IconFormat) {
        FileOutputStream(getWidgetIconFile(widgetId, format)).use {
            iconData.copyTo(it)
        }
    }

    fun removeWidgetIcon(widgetId: Int) {
        IconFormat.entries.forEach { format -> getWidgetIconFile(widgetId, format).delete() }
    }

    fun getWidgetIconFormat(widgetId: Int): IconFormat? = IconFormat.entries
        .firstOrNull { format -> getWidgetIconFile(widgetId, format).exists() }

    fun getWidgetIconStream(widgetId: Int): InputStream? {
        return getWidgetIconFormat(widgetId)?.let { format ->
            return FileInputStream(getWidgetIconFile(widgetId, format))
        }
    }

    fun clearCache(alsoClearIcons: Boolean) {
        temporaryBitmapCache.evictAll()
        if (alsoClearIcons) {
            try {
                httpCache.evictAll()
            } catch (ignored: IOException) {
                // ignored
            }
            widgetIconDirectory?.listFiles()?.forEach { f -> f.delete() }
            iconBitmapCache.evictAll()
        } else {
            // Don't evict icons from httpCache
            try {
                val urlIterator = httpCache.urls()
                while (urlIterator.hasNext()) {
                    if (urlIterator.next().toHttpUrlOrNull()?.isIconUrl() == false) {
                        urlIterator.remove()
                    }
                }
            } catch (ignored: IOException) {
                // ignored
            }
        }
    }

    private fun getWidgetIconFile(widgetId: Int, format: IconFormat): File {
        val suffix = when (format) {
            IconFormat.Svg -> ".svg"
            IconFormat.Png -> ".png"
        }
        return File(widgetIconDirectory, widgetId.toString() + suffix)
    }

    class BitmapCache(maxSize: Int) : LruCache<CacheKey, Bitmap>(maxSize) {
        override fun sizeOf(key: CacheKey, value: Bitmap): Int = value.byteCount / 1024
    }

    class NotificationBitmapCache(maxSize: Int) : LruCache<String, Bitmap>(maxSize) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    data class CacheKey(val url: HttpUrl, @param:ColorInt val fallbackColor: Int)

    companion object {
        private val TAG = CacheManager::class.java.simpleName
        private const val NOTIFICATION_IMAGE_TTL_MS = 30L * 24L * 60L * 60L * 1000L
        private val SAFE_FILENAME_REGEX = "[^a-zA-Z0-9._-]".toRegex()
        private var instance: CacheManager? = null

        fun getInstance(context: Context): CacheManager {
            val inst = instance ?: CacheManager(context.applicationContext)
            instance = inst
            return inst
        }
    }
}
