/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.util.Log;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.util.AsyncHttpClient;
import org.openhab.habdroid.util.CacheManager;
import org.openhab.habdroid.util.HttpClient;

import java.lang.ref.WeakReference;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Request;

public class WidgetImageView extends AppCompatImageView {
    public static final String TAG = WidgetImageView.class.getSimpleName();

    // Handler classes should be static or leaks might occur.
    private static class RefreshHandler extends Handler {
        private final WeakReference<WidgetImageView> mViewRef;

        RefreshHandler(WidgetImageView view) {
            mViewRef = new WeakReference<>(view);
        }

        @Override
        public void handleMessage(Message msg) {
            WidgetImageView imageView = mViewRef.get();
            if (imageView != null) {
                imageView.doRefresh();
            }
        }
    }

    private int mDefaultSvgSize;
    private Drawable mFallback;
    private Drawable mProgressDrawable;

    private ScaleType mOriginalScaleType;
    private boolean mOriginalAdjustViewBounds;
    private float mEmptyHeightToWidthRatio;
    private boolean mInternalLoad;
    private HttpImageRequest mLastRequest;

    private long mRefreshInterval;
    private long mLastRefreshTimestamp;
    private Handler mRefreshHandler;

    public WidgetImageView(Context context) {
        this(context, null);
    }

    public WidgetImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.WidgetImageView);
            mFallback = a.getDrawable(R.styleable.WidgetImageView_fallback);
            mProgressDrawable = a.getDrawable(R.styleable.WidgetImageView_progressIndicator);
            mEmptyHeightToWidthRatio = a.getFraction(
                    R.styleable.WidgetImageView_emptyHeightToWidthRatio, 1, 1, 0f);
            a.recycle();
        }

        mDefaultSvgSize = context.getResources().getDimensionPixelSize(R.dimen.svg_image_default_size);
        mRefreshHandler = new RefreshHandler(this);
    }

    public void setImageUrl(Connection connection, String url) {
        setImageUrl(connection, url, false);
    }

    public void setImageUrl(Connection connection, String url, long timeoutMillis) {
        setImageUrl(connection, url, timeoutMillis, false);
    }

    public void setImageUrl(Connection connection, String url, boolean forceLoad) {
        setImageUrl(connection, url, AsyncHttpClient.DEFAULT_TIMEOUT_MS, forceLoad);
    }

    public void setImageUrl(Connection connection, String url,
            long timeoutMillis, boolean forceLoad) {
        AsyncHttpClient client = connection.getAsyncHttpClient();
        HttpUrl actualUrl = client.buildUrl(url);

        if (mLastRequest != null && mLastRequest.isForUrl(actualUrl)) {
            // Nothing to do
            return;
        }

        cancelCurrentLoad();

        if (actualUrl == null) {
            applyFallbackDrawable();
            return;
        }
        Bitmap cached = CacheManager.getInstance(getContext()).getCachedBitmap(actualUrl);
        if (cached != null) {
            setBitmapInternal(cached);
        } else {
            applyProgressDrawable();
        }

        if (cached == null || forceLoad) {
            mLastRequest = new HttpImageRequest(client, actualUrl, timeoutMillis);
            mLastRequest.execute(forceLoad);
        }
    }

    @Override
    public void setImageResource(int resId) {
        cancelCurrentLoad();
        mLastRequest = null;
        removeProgressDrawable();
        super.setImageResource(resId);
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        if (!mInternalLoad) {
            cancelCurrentLoad();
            mLastRequest = null;
            removeProgressDrawable();
        }
        super.setImageDrawable(drawable);
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        cancelCurrentLoad();
        mLastRequest = null;
        removeProgressDrawable();
        super.setImageBitmap(bm);
    }

    @Override
    public void setAdjustViewBounds(boolean adjustViewBounds) {
        super.setAdjustViewBounds(adjustViewBounds);
        mOriginalAdjustViewBounds = adjustViewBounds;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        Drawable d = getDrawable();
        boolean isEmpty = d == null || d == mProgressDrawable;

        if (isEmpty && mEmptyHeightToWidthRatio > 0) {
            int specWidth = MeasureSpec.getSize(widthMeasureSpec);
            switch (MeasureSpec.getMode(widthMeasureSpec)) {
                case MeasureSpec.AT_MOST:
                case MeasureSpec.EXACTLY:
                    setMeasuredDimension(specWidth, (int) (mEmptyHeightToWidthRatio * specWidth));
                    break;
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mLastRequest != null && !mLastRequest.hasCompleted()) {
            mLastRequest.execute(false);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelCurrentLoad();
    }

    public void setRefreshRate(int msec) {
        cancelRefresh();
        mRefreshInterval = msec;
        mRefreshHandler.sendEmptyMessageDelayed(0, msec);
    }

    public void cancelRefresh() {
        mRefreshHandler.removeMessages(0);
        mRefreshInterval = 0;
    }

    private void setBitmapInternal(Bitmap bitmap) {
        removeProgressDrawable();
        // Mark this call as being triggered by ourselves, as setImageBitmap()
        // ultimately calls through to setImageDrawable().
        mInternalLoad = true;
        super.setImageBitmap(bitmap);
        mInternalLoad = false;
    }

    private void doRefresh() {
        mLastRefreshTimestamp = SystemClock.uptimeMillis();
        if (mLastRequest != null) {
            mLastRequest.execute(true);
        }
    }

    private void scheduleNextRefresh() {
        if (mRefreshInterval != 0) {
            mRefreshHandler.sendEmptyMessageAtTime(0, mLastRefreshTimestamp + mRefreshInterval);
        }
    }

    private void cancelCurrentLoad() {
        mRefreshHandler.removeMessages(0);
        if (mLastRequest != null) {
            mLastRequest.cancel();
        }
    }

    private void applyFallbackDrawable() {
        super.setImageDrawable(mFallback);
    }

    private void applyProgressDrawable() {
        if (mOriginalScaleType == null) {
            mOriginalScaleType = getScaleType();
            super.setScaleType(ScaleType.CENTER);
            super.setAdjustViewBounds(false);
        }
        super.setImageDrawable(mProgressDrawable);
    }

    private void removeProgressDrawable() {
        if (mOriginalScaleType != null) {
            super.setScaleType(mOriginalScaleType);
            super.setAdjustViewBounds(mOriginalAdjustViewBounds);
            mOriginalScaleType = null;
        }
    }

    private class HttpImageRequest extends AsyncHttpClient.BitmapResponseHandler {
        private final AsyncHttpClient mClient;
        private final HttpUrl mUrl;
        private final long mTimeoutMillis;
        private Call mCall;

        public HttpImageRequest(AsyncHttpClient client, HttpUrl url, long timeoutMillis) {
            super(mDefaultSvgSize);
            mClient = client;
            mUrl = url;
            mTimeoutMillis = timeoutMillis;
        }

        @Override
        public void onFailure(Request request, int statusCode, Throwable error) {
            removeProgressDrawable();
            applyFallbackDrawable();
            mCall = null;
        }

        @Override
        public void onSuccess(Bitmap body, Headers headers) {
            setBitmapInternal(body);
            CacheManager.getInstance(getContext()).cacheBitmap(mUrl, body);
            scheduleNextRefresh();
            mCall = null;
        }

        public boolean hasCompleted() {
            return mCall != null;
        }

        public void execute(boolean avoidCache) {
            Log.i(TAG, "Refreshing image at " + mUrl);
            HttpClient.CachingMode cachingMode = avoidCache
                    ? HttpClient.CachingMode.AVOID_CACHE
                    : HttpClient.CachingMode.FORCE_CACHE_IF_POSSIBLE;
            mCall = mClient.get(mUrl.toString(), mTimeoutMillis, cachingMode, this);
        }

        public void cancel() {
            if (mCall != null) {
                mCall.cancel();
                mCall = null;
            }
        }

        public boolean isForUrl(HttpUrl url) {
            return mCall != null && mCall.request().url().equals(url);
        }
    }
}
