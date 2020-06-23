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

package org.openhab.habdroid.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import androidx.appcompat.widget.AppCompatImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.util.CacheManager
import org.openhab.habdroid.util.HttpClient
import kotlin.random.Random

class WidgetImageView constructor(context: Context, attrs: AttributeSet?) : AppCompatImageView(context, attrs) {
    private var scope: CoroutineScope? = null
    private val defaultSvgSize: Int
    private val fallback: Drawable?
    private val progressDrawable: Drawable?

    private var originalScaleType: ScaleType? = null
    private var originalAdjustViewBounds: Boolean = false
    private val emptyHeightToWidthRatio: Float
    private val addRandomnessToUrl: Boolean
    private var internalLoad: Boolean = false
    private var lastRequest: HttpImageRequest? = null

    private var refreshInterval: Long = 0
    private var lastRefreshTimestamp: Long = 0
    private var refreshJob: Job? = null

    init {
        context.obtainStyledAttributes(attrs, R.styleable.WidgetImageView).apply {
            fallback = getDrawable(R.styleable.WidgetImageView_fallback)
            progressDrawable = getDrawable(R.styleable.WidgetImageView_progressIndicator)
            emptyHeightToWidthRatio = getFraction(R.styleable.WidgetImageView_emptyHeightToWidthRatio, 1, 1, 0f)
            addRandomnessToUrl = getBoolean(R.styleable.WidgetImageView_addRandomnessToUrl, false)
            recycle()
        }

        defaultSvgSize = context.resources.getDimensionPixelSize(R.dimen.svg_image_default_size)
    }

    fun setImageUrl(
        connection: Connection,
        url: String,
        size: Int?,
        timeoutMillis: Long = HttpClient.DEFAULT_TIMEOUT_MS,
        forceLoad: Boolean = false
    ) {
        val actualSize = size ?: defaultSvgSize
        val client = connection.httpClient
        val actualUrl = client.buildUrl(url)

        if (actualUrl == lastRequest?.url) {
            if (lastRequest?.isActive() == true) {
                // We're already in the process of loading this image, thus there's nothing to do
                return
            }
        } else {
            lastRefreshTimestamp = 0
        }

        cancelCurrentLoad()

        val cached = CacheManager.getInstance(context).getCachedBitmap(actualUrl)
        val request = HttpImageRequest(client, actualUrl, actualSize, timeoutMillis)

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

    override fun setImageBitmap(bm: Bitmap?) {
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
            val specWidth = MeasureSpec.getSize(widthMeasureSpec)
            val specMode = MeasureSpec.getMode(widthMeasureSpec)
            if (specMode == MeasureSpec.AT_MOST || specMode == MeasureSpec.EXACTLY) {
                setMeasuredDimension(specWidth, (emptyHeightToWidthRatio * specWidth).toInt())
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(Dispatchers.Main + Job())
        lastRequest?.let { request ->
            if (!request.hasCompleted()) {
                request.execute(false)
            } else {
                scheduleNextRefresh()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope?.cancel()
        scope = null
    }

    fun startRefreshing(refreshDelayInMs: Int) {
        refreshJob?.cancel()
        refreshJob = null
        refreshInterval = refreshDelayInMs.toLong()
        if (lastRequest?.isActive() != true) {
            scheduleNextRefresh()
        }
    }

    fun cancelRefresh() {
        refreshJob?.cancel()
        refreshJob = null
        refreshInterval = 0
        lastRefreshTimestamp = 0
    }

    private fun setBitmapInternal(bitmap: Bitmap) {
        removeProgressDrawable()
        // Mark this call as being triggered by ourselves, as setImageBitmap()
        // ultimately calls through to setImageDrawable().
        internalLoad = true
        super.setImageBitmap(bitmap)
        internalLoad = false
    }

    private fun scheduleNextRefresh() {
        if (refreshInterval == 0L) {
            return
        }
        val timeToNextRefresh = refreshInterval + lastRefreshTimestamp - SystemClock.uptimeMillis()
        Log.d(TAG, "Scheduling next refresh for ${lastRequest?.url} in $timeToNextRefresh ms")
        refreshJob = scope?.launch {
            delay(timeToNextRefresh)
            lastRequest?.execute(true)
        }
    }

    private fun cancelCurrentLoad() {
        refreshJob?.cancel()
        refreshJob = null
        lastRequest?.cancel()
    }

    private fun applyFallbackDrawable() {
        super.setImageDrawable(fallback)
    }

    private fun applyProgressDrawable() {
        if (originalScaleType == null) {
            originalScaleType = scaleType
            super.setScaleType(ScaleType.CENTER)
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

    private inner class HttpImageRequest(
        private val client: HttpClient,
        val url: HttpUrl,
        private val size: Int,
        private val timeoutMillis: Long
    ) {
        private var job: Job? = null
        private var lastRandomness = Random.Default.nextInt()

        fun execute(avoidCache: Boolean) {
            if (job?.isActive == true) {
                // Nothing to do, we're still in the process of downloading
                return
            }

            Log.i(TAG, "Refreshing image at $url, avoidCache $avoidCache")
            val cachingMode = if (avoidCache)
                HttpClient.CachingMode.AVOID_CACHE
            else
                HttpClient.CachingMode.FORCE_CACHE_IF_POSSIBLE

            val actualUrl = if (addRandomnessToUrl) {
                if (avoidCache) {
                    lastRandomness = Random.Default.nextInt()
                }
                url.newBuilder().setQueryParameter("random", lastRandomness.toString()).build()
            } else {
                url
            }

            job = scope?.launch(Dispatchers.Main) {
                try {
                    val bitmap = client.get(actualUrl.toString(),
                        timeoutMillis = timeoutMillis, caching = cachingMode)
                        .asBitmap(size)
                        .response
                    setBitmapInternal(bitmap)
                    CacheManager.getInstance(context).cacheBitmap(url, bitmap)
                    lastRefreshTimestamp = SystemClock.uptimeMillis()
                    scheduleNextRefresh()
                } catch (e: HttpClient.HttpException) {
                    removeProgressDrawable()
                    applyFallbackDrawable()
                }
            }
        }

        fun cancel() {
            job?.cancel()
        }

        fun hasCompleted(): Boolean {
            return job?.isCompleted == true
        }

        fun isActive(): Boolean {
            return job?.isActive == true
        }
    }

    companion object {
        private val TAG = WidgetImageView::class.java.simpleName
    }
}
