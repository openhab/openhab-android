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
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import androidx.annotation.NonNull;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class AsyncHttpClient extends HttpClient {
    public interface ResponseHandler<T> {
        // called in background thread
        T convertBodyInBackground(ResponseBody body) throws IOException;
        void onFailure(Request request, int statusCode, Throwable error);
        void onSuccess(T body, Headers headers);
    }

    public abstract static class StringResponseHandler implements ResponseHandler<String> {
        @Override
        public String convertBodyInBackground(ResponseBody body) throws IOException {
            return body.string();
        }
    }

    public abstract static class BitmapResponseHandler implements ResponseHandler<Bitmap> {
        private final int mSize;

        public BitmapResponseHandler(int sizePx) {
            mSize = sizePx;
        }

        @Override
        public Bitmap convertBodyInBackground(ResponseBody body) throws IOException {
            MediaType contentType = body.contentType();
            boolean isSvg = contentType != null
                    && contentType.type().equals("image")
                    && contentType.subtype().contains("svg");
            InputStream is = body.byteStream();
            if (isSvg) {
                try {
                    return getBitmapFromSvgInputstream(Resources.getSystem(), is, mSize);
                } catch (SVGParseException e) {
                    throw new IOException("SVG decoding failed", e);
                }
            } else {
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                if (bitmap != null) {
                    return bitmap;
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

    public static final long DEFAULT_TIMEOUT_MS = 30000;

    private Handler mHandler = new Handler(Looper.getMainLooper());

    public AsyncHttpClient(OkHttpClient client, String baseUrl, String username, String password) {
        super(client, baseUrl, username, password);
    }

    public <T> Call get(String url, ResponseHandler<T> responseHandler) {
        return method(url, "GET", null, null, null, DEFAULT_TIMEOUT_MS,
                CachingMode.AVOID_CACHE, responseHandler);
    }

    public <T> Call get(String url, Map<String, String> headers,
            ResponseHandler<T> responseHandler) {
        return method(url, "GET", headers, null, null, DEFAULT_TIMEOUT_MS,
                CachingMode.AVOID_CACHE, responseHandler);
    }

    public <T> Call get(String url, Map<String, String> headers,
            long timeoutMillis, ResponseHandler<T> responseHandler) {
        return method(url, "GET", headers, null, null, timeoutMillis,
                CachingMode.AVOID_CACHE, responseHandler);
    }

    public <T> Call get(String url, long timeoutMillis, CachingMode caching,
            ResponseHandler<T> responseHandler) {
        return method(url, "GET", null, null, null, timeoutMillis, caching, responseHandler);
    }

    public Call post(String url, String requestBody, String mediaType,
            StringResponseHandler responseHandler) {
        return method(url, "POST", null, requestBody,
                mediaType, DEFAULT_TIMEOUT_MS, CachingMode.AVOID_CACHE, responseHandler);
    }

    private <T> Call method(String url, String method, Map<String, String> headers,
            String requestBody, String mediaType, long timeoutMillis, CachingMode caching,
            final ResponseHandler<T> responseHandler) {
        Call call = prepareCall(url, method, headers,
                requestBody, mediaType, timeoutMillis, caching);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull final Call call, final IOException e) {
                mHandler.post(() -> {
                    if (!call.isCanceled()) {
                        responseHandler.onFailure(call.request(), 0, e);
                    }
                });
            }

            @Override
            public void onResponse(final Call call, Response response) {
                final ResponseBody body = response.body();
                final int code = response.code();
                T converted = null;
                Throwable conversionError = null;
                if (body != null) {
                    try {
                        converted = responseHandler.convertBodyInBackground(body);
                    } catch (IOException e) {
                        conversionError = e;
                    } finally {
                        body.close();
                    }
                }
                final T result = converted;
                final String message = response.message();
                final Throwable error = response.isSuccessful()
                        ? conversionError : new IOException(message);
                final Headers headers = response.headers();
                mHandler.post(() -> {
                    if (!call.isCanceled()) {
                        if (error != null) {
                            responseHandler.onFailure(call.request(), code, error);
                        } else {
                            responseHandler.onSuccess(result, headers);
                        }
                    }
                });
            }
        });
        return call;
    }
}
