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
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.Log;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import com.loopj.android.image.SmartImage;
import com.loopj.android.image.WebImageCache;

import java.io.InputStream;

import okhttp3.MediaType;

public class MyWebImage implements SmartImage {
    private static final String TAG = "MyWebImage";
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 10000;

    private static WebImageCache sWebImageCache;

    private String url;
    private boolean useCache = true;
    
    private String authUsername;
    private String authPassword;
    private boolean shouldAuth = false;

    public MyWebImage(String url, boolean useCache, String username, String password) {
    	this.url = url;
    	this.useCache = useCache;
        this.setAuthentication(username, password);
    }

    public Bitmap getCachedBitmap() {
        WebImageCache cache = useCache ? getWebImageCache() : null;
        return cache != null ? cache.get(url) : null;
    }

    /**
     * Returns the already initialized WebImageCache, if there's any. This method may return
     * null if {@link MyWebImage#getWebImageCache(Context ctx)} was not called so far.
     *
     * @return WebImageCache|null
     */
    public static WebImageCache getWebImageCache() {
        return sWebImageCache;
    }

    /**
     * See {@link MyWebImage#getWebImageCache()}, with the difference, that this method does not
     * return null.
     *
     * @param ctx
     * @return WebImageCache
     */
    public static WebImageCache getWebImageCache(Context ctx) {
        if (sWebImageCache == null) {
            sWebImageCache = new WebImageCache(ctx);
        }

        return sWebImageCache;
    }

    public Bitmap getBitmap(Context context) {
                // Try getting bitmap from cache first
        Bitmap bitmap = null;
        if(url != null) {
            if (this.useCache)
            	bitmap = getWebImageCache(context).get(url);
            if(bitmap == null) {
            	Log.i("MyWebImage", "Cache for " + url + " is empty, getting image");
                bitmap = getBitmapFromUrl(context, url);
                if(bitmap != null && this.useCache) {
                    getWebImageCache(context).put(url, bitmap);
                }
            }
        }

        return bitmap;
    }

    private Bitmap getBitmapFromUrl(Context context, final String url) {
        MySyncHttpClient client = new MySyncHttpClient(context);
        client.setTimeout(READ_TIMEOUT);
        client.setBaseUrl(url);
        client.setTimeout(60000);
        if (shouldAuth) {
            client.setBasicAuth(authUsername, authPassword);
        }

        MySyncHttpClient.HttpResult result = client.get(url);
        if (result.error != null) {
            Log.e(TAG, "Failed to get " + url + " with code " + result.statusCode + ":" + result.error);
            return null;
        }

        MediaType contentType = result.response.contentType();
        boolean isSVG = contentType  != null
                && contentType.type().equals("image")
                && contentType.subtype().contains("svg");
        InputStream is = result.response.byteStream();

        return isSVG ? getBitmapFromSvgInputstream(is) : BitmapFactory.decodeStream(is);
    }

    private Bitmap getBitmapFromSvgInputstream(InputStream is) {
        Bitmap bitmap = null;
        try {
            SVG svg = SVG.getFromInputStream(is);
            double width = 16;
            double height = 16;
            if (svg.getDocumentViewBox() != null) {
                width = svg.getDocumentViewBox().width();
                height = svg.getDocumentViewBox().height();
            } else {
                Log.d(TAG, "DocumentViewBox is null. assuming width and heigh of 16px.");
            }

            bitmap = Bitmap.createBitmap((int) Math.ceil(width), (int) Math.ceil(height), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);//drawARGB(0,0,0,0);//drawRGB(255, 255, 255);
            svg.renderToCanvas(canvas);
        } catch (SVGParseException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        return bitmap;
    }

    public static void removeFromCache(String url) {
        WebImageCache cache = getWebImageCache();
        if (cache != null) {
            cache.remove(url);
        }
    }
    
    public void setAuthentication(String username, String password) {
    	this.authUsername = username;
    	this.authPassword = password;
    	if (this.authUsername != null && (this.authUsername.length() > 0 && this.authPassword.length() > 0))
    		this.shouldAuth = true;
    }
}
