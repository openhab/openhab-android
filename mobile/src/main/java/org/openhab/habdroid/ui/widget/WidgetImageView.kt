/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Message
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView

import okhttp3.Call
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.util.AsyncHttpClient
import org.openhab.habdroid.util.CacheManager
import org.openhab.habdroid.util.HttpClient

import java.lang.ref.WeakReference

class WidgetImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
        AppCompatImageView(context, attrs, defStyle) {

    private val defaultSvgSize: Int
    private val fallback: Drawable?
    private val progressDrawable: Drawable?

    private var originalScaleType: ScaleType? = null
    private var originalAdjustViewBounds: Boolean = false
    private val emptyHeightToWidthRatio: Float
    private var internalLoad: Boolean = false
    private var lastRequest: HttpImageRequest? = null

    private var refreshInterval: Long = 0
    private var lastRefreshTimestamp: Long = 0
    private val refreshHandler: Handler

    // Handler classes should be static or leaks might occur.
    private class RefreshHandler internal constructor(view: WidgetImageView) : Handler() {
        private val viewRef: WeakReference<WidgetImageView>

        init {
            viewRef = WeakReference(view)
        }

        override fun handleMessage(msg: Message) {
            val imageView = viewRef.get()
            imageView?.doRefresh()
        }
    }

    init {
        val a = if (attrs != null) context.obtainStyledAttributes(attrs, R.styleable.WidgetImageView) else null
        fallback = a?.getDrawable(R.styleable.WidgetImageView_fallback)
        progressDrawable = a?.getDrawable(R.styleable.WidgetImageView_progressIndicator)
        emptyHeightToWidthRatio = a?.getFraction(R.styleable.WidgetImageView_emptyHeightToWidthRatio, 1, 1, 0f) ?: 0F
        a?.recycle()

        defaultSvgSize = context.resources.getDimensionPixelSize(R.dimen.svg_image_default_size)
        refreshHandler = RefreshHandler(this)
    }

    fun setImageUrl(connection: Connection, url: String, size: Int?) {
        setImageUrl(connection, url, size, AsyncHttpClient.DEFAULT_TIMEOUT_MS, false)
    }

    fun setImageUrl(connection: Connection, url: String, size: Int?, forceLoad: Boolean) {
        setImageUrl(connection, url, size, AsyncHttpClient.DEFAULT_TIMEOUT_MS, forceLoad)
    }

    fun setImageUrl(connection: Connection, url: String, size: Int?, timeoutMillis: Long) {
        setImageUrl(connection, url, size, timeoutMillis, false)
    }

    fun setImageUrl(connection: Connection, url: String, size: Int?,
                    timeoutMillis: Long, forceLoad: Boolean) {
        val actualSize = size ?: defaultSvgSize
        val client = connection.asyncHttpClient
        val actualUrl = client.buildUrl(url)

        if (lastRequest?.isActiveForUrl(actualUrl) ?: false) {
            // We're already in the process of loading this image, thus there's nothing to do
            return
        }

        cancelCurrentLoad()

        val cached = CacheManager.getInstance(context).getCachedBitmap(actualUrl)
        val request = HttpImageRequest(actualSize, client, actualUrl, timeoutMillis)

        if (cached != null) {
            setBitmapInternal(cached)
        } else {
            applyProgressDrawable()
        }

        if (cached == null || forceLoad) {
            request.execute(forceLoad)
        }
        lastRequest = request
    }

    override fun setImageResource(resId: Int) {
        cancelCurrentLoad()
        lastRequest = null
        removeProgressDrawable()
        super.setImageResource(resId)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        if (!internalLoad) {
            cancelCurrentLoad()
            lastRequest = null
            removeProgressDrawable()
        }
        super.setImageDrawable(drawable)
    }

    override fun setImageBitmap(bm: Bitmap) {
        cancelCurrentLoad()
        lastRequest = null
        removeProgressDrawable()
        super.setImageBitmap(bm)
    }

    override fun setAdjustViewBounds(adjustViewBounds: Boolean) {
        super.setAdjustViewBounds(adjustViewBounds)
        originalAdjustViewBounds = adjustViewBounds
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val d = drawable
        val isEmpty = d == null || d === progressDrawable

        if (isEmpty && emptyHeightToWidthRatio > 0) {
            val specWidth = View.MeasureSpec.getSize(widthMeasureSpec)
            val specMode = View.MeasureSpec.getMode(widthMeasureSpec)
            if (specMode == View.MeasureSpec.AT_MOST || specMode == View.MeasureSpec.EXACTLY) {
                setMeasuredDimension(specWidth, (emptyHeightToWidthRatio * specWidth).toInt())
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val request = lastRequest
        if (request != null) {
            if (!request.hasCompleted()) {
                request.execute(false)
            } else {
                scheduleNextRefresh()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelCurrentLoad()
    }

    fun setRefreshRate(msec: Int) {
        cancelRefresh()
        refreshInterval = msec.toLong()
        refreshHandler.sendEmptyMessageDelayed(0, refreshInterval)
    }

    fun cancelRefresh() {
        refreshHandler.removeMessages(0)
        refreshInterval = 0
    }

    private fun setBitmapInternal(bitmap: Bitmap) {
        removeProgressDrawable()
        // Mark this call as being triggered by ourselves, as setImageBitmap()
        // ultimately calls through to setImageDrawable().
        internalLoad = true
        super.setImageBitmap(bitmap)
        internalLoad = false
    }

    private fun doRefresh() {
        lastRefreshTimestamp = SystemClock.uptimeMillis()
        lastRequest?.execute(true)
    }

    private fun scheduleNextRefresh() {
        if (refreshInterval != 0L) {
            refreshHandler.sendEmptyMessageAtTime(0, lastRefreshTimestamp + refreshInterval)
        }
    }

    private fun cancelCurrentLoad() {
        refreshHandler.removeMessages(0)
        lastRequest?.cancel()
    }

    private fun applyFallbackDrawable() {
        super.setImageDrawable(fallback)
    }

    private fun applyProgressDrawable() {
        if (originalScaleType == null) {
            originalScaleType = scaleType
            super.setScaleType(ImageView.ScaleType.CENTER)
            super.setAdjustViewBounds(false)
        }
        super.setImageDrawable(progressDrawable)
    }

    private fun removeProgressDrawable() {
        if (originalScaleType != null) {
            super.setScaleType(originalScaleType)
            super.setAdjustViewBounds(originalAdjustViewBounds)
            originalScaleType = null
        }
    }

    private inner class HttpImageRequest(size: Int, private val client: AsyncHttpClient,
                                         private val url: HttpUrl, private val timeoutMillis: Long) :
            AsyncHttpClient.BitmapResponseHandler(size) {
        private var call: Call? = null

        override fun onFailure(request: Request, statusCode: Int, error: Throwable) {
            removeProgressDrawable()
            applyFallbackDrawable()
            call = null
        }

        override fun onSuccess(response: Bitmap, headers: Headers) {
            setBitmapInternal(response)
            CacheManager.getInstance(context).cacheBitmap(url, response)
            scheduleNextRefresh()
            call = null
        }

        fun execute(avoidCache: Boolean) {
            Log.i(TAG, "Refreshing image at $url, avoidCache $avoidCache")
            val cachingMode = if (avoidCache)
                HttpClient.CachingMode.AVOID_CACHE
            else
                HttpClient.CachingMode.FORCE_CACHE_IF_POSSIBLE
            call = client.get(url.toString(), timeoutMillis, cachingMode, this)
        }

        fun cancel() {
            call?.cancel()
        }

        fun hasCompleted(): Boolean {
            return call == null
        }

        fun isActiveForUrl(url: HttpUrl): Boolean {
            val c = call
            return c != null && c.request().url() == url && !c.isCanceled
        }
    }

    companion object {
        val TAG = WidgetImageView::class.java.simpleName
    }
}
