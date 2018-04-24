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
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.ImageView;

import java.io.IOException;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MjpegStreamer {
    private static final String TAG = MjpegStreamer.class.getSimpleName();

    private String mSourceUrl;
    private String mUsername;
    private String mPassword;
    private Handler mHandler;
    private DownloadImageTask mDownloadImageTask;

    public MjpegStreamer(ImageView view, String sourceUrl, String username, String password) {
        mSourceUrl = sourceUrl;
        mUsername = username;
        mPassword = password;
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
        Request request = new Request.Builder()
                .url(mSourceUrl)
                .build();
        OkHttpClient client = new OkHttpClient.Builder()
                .authenticator((route, response) -> {
                    Log.d(TAG, "Authenticating for response: " + response);
                    Log.d(TAG, "Challenges: " + response.challenges());
                    String credential = Credentials.basic(mUsername, mPassword);
                    return response.request().newBuilder()
                            .header("Authorization", credential)
                            .build();
                })
                .build();

        Response response = client.newCall(request).execute();
        Log.d(TAG, "MJPEG request finished, status = " + response.code());
        if (!response.isSuccessful()) {
            throw new HttpException(response.code());
        }
        return new MjpegInputStream(response.body().byteStream());
    }

    private static class HttpException extends IOException {
        public HttpException(int code) {
            super("HTTP failure code " + code);
        }
    }

    private class DownloadImageTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            while (!isCancelled()) {
                try {
                    doStreamOnce();
                } catch (IOException e) {
                    Log.e(TAG, "MJPEG streaming from " + mSourceUrl + " failed", e);
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