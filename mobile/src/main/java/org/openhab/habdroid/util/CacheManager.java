/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;

import okhttp3.Cache;
import okhttp3.HttpUrl;

import java.io.File;
import java.io.IOException;

public class CacheManager {
    private static CacheManager sInstance;
    private Cache mHttpCache;
    private LruCache<HttpUrl, Bitmap> mBitmapCache;

    public static CacheManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new CacheManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private CacheManager(Context appContext) {
        mHttpCache = new Cache(new File(appContext.getCacheDir(), "http"), 2 * 1024 * 1024);

        // Get max available VM memory, exceeding this amount will throw an
        // OutOfMemory exception. Stored in kilobytes as LruCache takes an
        // int in its constructor.
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        // Use up to 1/8 of the available VM memory for the bitmap cache
        mBitmapCache = new LruCache<HttpUrl, Bitmap>(maxMemory / 8) {
            @Override
            protected int sizeOf(HttpUrl key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    public Bitmap getCachedBitmap(HttpUrl url) {
        return mBitmapCache.get(url);
    }

    public void cacheBitmap(HttpUrl url, Bitmap bitmap) {
        mBitmapCache.put(url, bitmap);
    }

    public void clearCache() {
        mBitmapCache.evictAll();
        try {
            mHttpCache.evictAll();
        } catch (IOException ignored) {
            // ignored
        }
    }

    public Cache getHttpCache() {
        return mHttpCache;
    }
}
