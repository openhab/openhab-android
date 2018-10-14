/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.util;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;
import androidx.annotation.NonNull;

import org.openhab.habdroid.core.connection.Connection;

import java.io.IOException;

public class MjpegStreamer {
    private static final String TAG = MjpegStreamer.class.getSimpleName();

    private SyncHttpClient mHttpClient;
    private String mUrl;
    private Handler mHandler;
    private DownloadImageTask mDownloadImageTask;

    public MjpegStreamer(ImageView view, Connection connection, String url) {
        mHttpClient = connection.getSyncHttpClient();
        mUrl = url;
        mHandler = new Handler(msg -> {
            if (mDownloadImageTask != null) {
                Bitmap bmp = (Bitmap) msg.obj;
                view.setImageBitmap(bmp);
            }
            return false;
        });
    }

    public void start() {
        mDownloadImageTask = new DownloadImageTask();
        mDownloadImageTask.execute();
    }

    public void stop() {
        if (mDownloadImageTask != null) {
            mDownloadImageTask.cancel(true);
            mDownloadImageTask = null;
        }
    }

    @NonNull
    private MjpegInputStream startStream() throws IOException {
        SyncHttpClient.HttpResult result = mHttpClient.get(mUrl);
        Log.d(TAG, "MJPEG request finished, status = " + result.statusCode);
        if (result.error != null) {
            throw new HttpException(result.statusCode, result.error);
        }
        return new MjpegInputStream(result.response.byteStream());
    }

    private static class HttpException extends IOException {
        public HttpException(int code, Throwable cause) {
            super("HTTP failure code " + code);
            initCause(cause);
        }
    }

    private class DownloadImageTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            while (!isCancelled()) {
                try {
                    doStreamOnce();
                } catch (IOException e) {
                    Log.e(TAG, "MJPEG streaming from " + mUrl + " failed", e);
                    if (e instanceof HttpException) {
                        // no point in continuing if the server returned failure
                        break;
                    }
                }
            }
            return null;
        }

        private void doStreamOnce() throws IOException {
            try (MjpegInputStream stream = startStream()) {
                while (!isCancelled()) {
                    Bitmap bitmap = stream.readMjpegFrame();
                    mHandler.obtainMessage(0, bitmap).sendToTarget();
                }
            }
        }

        @Override
        protected void onPostExecute(Void result) {
            mDownloadImageTask = null;
        }
    }
}