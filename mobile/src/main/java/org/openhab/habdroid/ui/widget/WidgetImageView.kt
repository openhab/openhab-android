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
import kotlin.random.Random
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
import org.openhab.habdroid.util.ImageConversionPolicy

class WidgetImageView constructor(context: Context, attrs: AttributeSet?) : AppCompatImageView(context, attrs) {
    private var scope: CoroutineScope? = null
    private val fallback: Drawable?
    private val progressDrawable: Drawable?

    private var originalScaleType: ScaleType? = null
    private var originalAdjustViewBounds: Boolean = false
    private val emptyHeightToWidthRatio: Float
    private val addRandomnessToUrl: Boolean
    private var imageScalingType = ImageScalingType.NoScaling
    private var internalLoad: Boolean = false
    private var lastRequest: HttpImageRequest? = null

    private var refreshInterval: Long = 0
    private var lastRefreshTimestamp: Long = 0
    private var refreshJob: Job? = null
    private var refreshActive = false
    private var pendingRequest: PendingRequest? = null
    private var pendingLoadJob: Job? = null
    private var targetImageSize: Int = 0

    init {
        context.obtainStyledAttributes(attrs, R.styleable.WidgetImageView).apply {
            fallback = getDrawable(R.styleable.WidgetImageView_fallback)
            progressDrawable = getDrawable(R.styleable.WidgetImageView_progressIndicator)
            emptyHeightToWidthRatio = getFraction(R.styleable.WidgetImageView_emptyHeightToWidthRatio, 1, 1, 0f)
            addRandomnessToUrl = getBoolean(R.styleable.WidgetImageView_addRandomnessToUrl, false)
            val imageScalingType = getInt(R.styleable.WidgetImageView_imageScalingType, 0)
            if (imageScalingType < ImageScalingType.values().size) {
                setImageScalingType(ImageScalingType.values()[imageScalingType])
            }
            recycle()
        }
    }

    fun setImageUrl(
        connection: Connection,
        url: String,
        refreshDelayInMs: Int = 0,
        timeoutMillis: Long = HttpClient.DEFAULT_TIMEOUT_MS,
        forceLoad: Boolean = false
    ) {
        val client = connection.httpClient
        val actualUrl = client.buildUrl(url)

        pendingLoadJob?.cancel()
        refreshInterval = refreshDelayInMs.toLong()

        if (actualUrl == lastRequest?.url) {
            if (lastRequest?.isActive() == true) {
                // We're already in the process of loading this image, thus there's nothing to do
                return
            }
        } else if (pendingRequest == null) {
            lastRefreshTimestamp = 0
        }

        if (targetImageSize == 0) {
            pendingRequest = PendingRequest(client, actualUrl, timeoutMillis, forceLoad)
        } else {
            doLoad(client, actualUrl, timeoutMillis, forceLoad)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        targetImageSize = right - left - paddingLeft - paddingRight
        pendingRequest?.let { r ->
            pendingLoadJob = scope?.launch {
                doLoad(r.client, r.url, r.timeoutMillis, r.forceLoad)
            }
        }
        pendingRequest = null
    }

    override fun setImageResource(resId: Int) {
        prepareForNonHttpImage()
        super.setImageResource(resId)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        if (!internalLoad) {
            prepareForNonHttpImage()
        }
        super.setImageDrawable(drawable)
    }

    override fun setImageBitmap(bm: Bitmap?) {
        prepareForNonHttpImage()
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
                // Make sure to have an up-to-date image if refresh is enabled by avoiding cache in that case
                // (when not doing so, we'd always load a stale image from cache until first refresh)
                request.execute(refreshInterval != 0L)
            } else {
                scheduleNextRefreshIfNeeded()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope?.cancel()
        scope = null
    }

    fun setImageScalingType(type: ImageScalingType) {
        if (imageScalingType == type) {
            return
        }
        imageScalingType = type
        when (type) {
            ImageScalingType.NoScaling -> {
                adjustViewBounds = false
                scaleType = ScaleType.CENTER_INSIDE
            }
            ImageScalingType.ScaleToFit -> {
                adjustViewBounds = false
                scaleType = ScaleType.FIT_CENTER
            }
            ImageScalingType.ScaleToFitWithViewAdjustment,
            ImageScalingType.ScaleToFitWithViewAdjustmentDownscaleOnly -> {
                adjustViewBounds = true
                scaleType = ScaleType.FIT_CENTER
            }
        }
    }

    fun startRefreshingIfNeeded() {
        refreshJob?.cancel()
        refreshJob = null
        refreshActive = true
        if (lastRequest?.isActive() != true) {
            scheduleNextRefreshIfNeeded()
        }
    }

    fun cancelRefresh() {
        refreshJob?.cancel()
        refreshJob = null
        lastRefreshTimestamp = 0
        refreshActive = false
    }

    private fun prepareForNonHttpImage() {
        cancelCurrentLoad()
        cancelRefresh()
        lastRequest = null
        refreshInterval = 0
        removeProgressDrawable()
    }

    private fun doLoad(client: HttpClient, url: HttpUrl, timeoutMillis: Long, forceLoad: Boolean) {
        cancelCurrentLoad()

        val cached = CacheManager.getInstance(context).getCachedBitmap(url)
        val request = HttpImageRequest(client, url, targetImageSize, timeoutMillis)

        if (cached != null) {
            applyLoadedBitmap(cached)
        } else if (progressDrawable != null || lastRequest?.statelessUrlEquals(url) != true) {
            applyProgressDrawable()
        }

        if (cached == null || forceLoad) {
            request.execute(forceLoad)
        } else {
            scheduleNextRefreshIfNeeded()
        }
        lastRequest = request
    }

    private fun scheduleNextRefreshIfNeeded() {
        if (refreshInterval == 0L || !refreshActive) {
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
        pendingLoadJob?.cancel()
        pendingLoadJob = null
    }

    private fun applyLoadedBitmap(bitmap: Bitmap) {
        removeProgressDrawable()
        if (imageScalingType == ImageScalingType.ScaleToFitWithViewAdjustmentDownscaleOnly) {
            // Make sure that view only shrinks to accommodate bitmap size, but doesn't enlarge ... that is,
            // adjust view bounds only if width is larger than target size or height is larger than the maximum height
            adjustViewBounds = bitmap.width > targetImageSize || maxHeight < bitmap.height
        }
        // Mark this call as being triggered by ourselves, as setImageBitmap()
        // ultimately calls through to setImageDrawable().
        internalLoad = true
        super.setImageBitmap(bitmap)
        internalLoad = false
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
            super.setAdjustViewBounds(originalAdjustViewBounds)
            super.setScaleType(originalScaleType)
            originalScaleType = null
        }
        super.setImageDrawable(null)
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
                    val conversionPolicy = when (originalScaleType ?: scaleType) {
                        ScaleType.FIT_CENTER, ScaleType.FIT_START,
                        ScaleType.FIT_END, ScaleType.FIT_XY -> ImageConversionPolicy.PreferTargetSize
                        else -> ImageConversionPolicy.PreferSourceSize
                    }
                    val bitmap = client.get(actualUrl.toString(),
                        timeoutMillis = timeoutMillis, caching = cachingMode)
                        .asBitmap(size, conversionPolicy)
                        .response
                    CacheManager.getInstance(context).cacheBitmap(url, bitmap)
                    applyLoadedBitmap(bitmap)
                    lastRefreshTimestamp = SystemClock.uptimeMillis()
                    scheduleNextRefreshIfNeeded()
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

        fun statelessUrlEquals(url: HttpUrl): Boolean {
            return this.url.newBuilder().removeAllQueryParameters("state").build() ==
                url.newBuilder().removeAllQueryParameters("state").build()
        }
    }

    data class PendingRequest(val client: HttpClient, val url: HttpUrl, val timeoutMillis: Long, val forceLoad: Boolean)

    enum class ImageScalingType {
        NoScaling,
        ScaleToFit,
        ScaleToFitWithViewAdjustment,
        ScaleToFitWithViewAdjustmentDownscaleOnly
    }

    companion object {
        private val TAG = WidgetImageView::class.java.simpleName
    }
}
