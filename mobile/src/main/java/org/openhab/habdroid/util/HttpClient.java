/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.util;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.util.DisplayMetrics;
import androidx.annotation.VisibleForTesting;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import com.here.oksse.OkSse;
import com.here.oksse.ServerSentEvent;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class HttpClient {
    public enum CachingMode {
        DEFAULT,
        AVOID_CACHE,
        FORCE_CACHE_IF_POSSIBLE
    }

    private final HttpUrl mBaseUrl;
    private final OkHttpClient mClient;
    @VisibleForTesting public final String mAuthHeader;

    protected HttpClient(OkHttpClient client, String baseUrl, String username, String password) {
        mBaseUrl = baseUrl != null ? HttpUrl.parse(baseUrl) : null;
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            mAuthHeader = Credentials.basic(username, password);
        } else {
            mAuthHeader = null;
        }
        mClient = client;
    }

    public ServerSentEvent makeSse(HttpUrl url, ServerSentEvent.Listener listener) {
        Request request = makeAuthenticatedRequestBuilder()
                .url(url)
                .build();
        OkHttpClient client = mClient.newBuilder()
                .readTimeout(0, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        return new OkSse(client).newServerSentEvent(request, listener);
    }

    public HttpUrl buildUrl(String url) {
        HttpUrl absoluteUrl = HttpUrl.parse(url);
        if (absoluteUrl == null && mBaseUrl != null) {
            if (url.startsWith("/")) {
                url = url.substring(1);
            }
            absoluteUrl = HttpUrl.parse(mBaseUrl.toString() + url);
        }
        if (absoluteUrl == null) {
            throw new IllegalArgumentException("URL '" + url + "' is invalid");
        }
        return absoluteUrl;
    }

    protected Call prepareCall(String url, String method, Map<String, String> additionalHeaders,
            String requestBody, String mediaType,
            long timeoutMillis, CachingMode caching) {
        Request.Builder requestBuilder = makeAuthenticatedRequestBuilder()
                .url(buildUrl(url));
        if (additionalHeaders != null) {
            for (Map.Entry<String, String> entry : additionalHeaders.entrySet()) {
                requestBuilder.addHeader(entry.getKey(), entry.getValue());
            }
        }
        if (requestBody != null) {
            requestBuilder.method(method, RequestBody.create(MediaType.parse(mediaType), requestBody));
        }
        switch (caching) {
            case AVOID_CACHE:
                requestBuilder.cacheControl(CacheControl.FORCE_NETWORK);
                break;
            case FORCE_CACHE_IF_POSSIBLE:
                requestBuilder.cacheControl(new CacheControl.Builder()
                        .maxStale(Integer.MAX_VALUE, TimeUnit.SECONDS)
                        .build());
                break;
            default:
                break;
        }
        Request request = requestBuilder.build();
        final OkHttpClient client = timeoutMillis > 0
                ? mClient.newBuilder().readTimeout(timeoutMillis, TimeUnit.MILLISECONDS).build()
                : mClient;
        return client.newCall(request);
    }

    private Request.Builder makeAuthenticatedRequestBuilder() {
        Request.Builder builder = new Request.Builder()
                .addHeader("User-Agent", "openHAB client for Android");
        if (mAuthHeader != null) {
            builder.addHeader("Authorization", mAuthHeader);
        }
        return builder;
    }

    protected static Bitmap getBitmapFromResponseBody(ResponseBody body,
            int size, boolean enforceSize) throws IOException {
        MediaType contentType = body.contentType();
        boolean isSvg = contentType != null
                && contentType.type().equals("image")
                && contentType.subtype().contains("svg");
        InputStream is = body.byteStream();
        if (isSvg) {
            try {
                return getBitmapFromSvgInputstream(Resources.getSystem(), is, size);
            } catch (SVGParseException e) {
                throw new IOException("SVG decoding failed", e);
            }
        } else {
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (bitmap != null) {
                return enforceSize
                        ? Bitmap.createScaledBitmap(bitmap, size, size, false)
                        : bitmap;
            }
            throw new IOException("Bitmap decoding failed");
        }
    }

    private static Bitmap getBitmapFromSvgInputstream(Resources res, InputStream is, int size)
            throws SVGParseException {
        SVG svg = SVG.getFromInputStream(is);
        svg.setRenderDPI(DisplayMetrics.DENSITY_DEFAULT);
        Float density = res.getDisplayMetrics().density;
        svg.setDocumentHeight("100%");
        svg.setDocumentWidth("100%");
        int docWidth = (int) (svg.getDocumentWidth() * density);
        int docHeight = (int) (svg.getDocumentHeight() * density);
        if (docWidth < 0 || docHeight < 0) {
            float aspectRatio = svg.getDocumentAspectRatio();
            if (aspectRatio > 0) {
                float heightForAspect = (float) size / aspectRatio;
                float widthForAspect = (float) size * aspectRatio;
                if (widthForAspect < heightForAspect) {
                    docWidth = Math.round(widthForAspect);
                    docHeight = size;
                } else {
                    docWidth = size;
                    docHeight = Math.round(heightForAspect);
                }
            } else {
                docWidth = size;
                docHeight = size;
            }

            // we didn't take density into account anymore when calculating docWidth
            // and docHeight, so don't scale with it and just let the renderer
            // figure out the scaling
            density = null;
        }

        if (docWidth != size || docHeight != size) {
            float scaleWidth = (float) size / docWidth;
            float scaleHeigth = (float) size / docHeight;
            density = (scaleWidth + scaleHeigth) / 2;

            docWidth = size;
            docHeight = size;
        }

        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        if (density != null) {
            canvas.scale(density, density);
        }
        svg.renderToCanvas(canvas);
        return bitmap;
    }
}
