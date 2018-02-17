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
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

public class MjpegStreamer {

    private static final String TAG = MjpegStreamer.class.getSimpleName();
    private String mSourceUrl;
    private String mUsername;
    private String mPassword;
    private MjpegInputStream mInputStream;
    private boolean mRunning = false;
    private Handler mHandler;
    private Context mCtx;
    private int mId;
    private ImageView mTargetImageView;
    private DownloadImageTask mDownloadImageTask;

    public MjpegStreamer(String sourceUrl, String username, String password, Context ctx){
        mSourceUrl = sourceUrl;
        mUsername = username;
        mPassword = password;
        mCtx = ctx;
        mHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                Bitmap bmp = (Bitmap) msg.obj;
                if (mTargetImageView != null)
                    mTargetImageView.setImageBitmap(bmp);
                return false;
            }
        });
    }

    public void start() {
        mDownloadImageTask = startTask(this, 1, false, mHandler);
    }

    public void stop() {
        if (mDownloadImageTask != null)
            mDownloadImageTask.cancel(true);
    }

    public void setTargetImageView(ImageView targetImageView) {
        mTargetImageView = targetImageView;
    }

    public void startStream(Handler handler, int id){
        mHandler = handler;
        mId = id;
        mInputStream = new MjpegInputStream(httpRequest(mSourceUrl, mUsername, mPassword));
        mRunning = true;
    }

    public void getFrame(){
        Bitmap mBitmap;
        try {
            mBitmap = mInputStream.readMjpegFrame();
            Message m = mHandler.obtainMessage(mId, mBitmap);
            m.sendToTarget();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public InputStream httpRequest(String url, final String usr, final String pwd){
        Request request = new Request.Builder()
                .url(url)
                .build();
        OkHttpClient client = new OkHttpClient.Builder()
                .authenticator(new Authenticator() {
                    @Override
                    public Request authenticate(Route route, Response response) throws IOException {
                        Log.d(TAG, "Authenticating for response: " + response);
                        Log.d(TAG, "Challenges: " + response.challenges());
                        // Get username/password from preferences
                        String credential = Credentials.basic(usr, pwd);
                        return response.request().newBuilder()
                                .header("Authorization", credential)
                                .build();
                    }
                })
                .build();

        try {
            Log.d(TAG, "1. Sending http request");
            Response response = client.newCall(request).execute();
            Log.d(TAG, "2. Request finished, status = " + response.code());
            if (response.code()==401){
                //You must turn off camera User Access Control before this will work
                return null;
            }
            return response.body().byteStream();
        } catch (IOException e) {
            Log.e(TAG, "Request failed-IOException", e);
            //Error connecting to camera
        }

        return null;

    }

    private DownloadImageTask startTask(MjpegStreamer cam, int id, boolean useParallelExecution, Handler h) {
        DownloadImageTask task = new DownloadImageTask(cam, id);
        if (useParallelExecution) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            task.execute(h);
        }
        return task;
    }

    private class DownloadImageTask extends AsyncTask<Handler, Void, Void> {
        MjpegStreamer cam;
        int id;
        DownloadImageTask(MjpegStreamer cam, int id){
            this.cam = cam;
            this.id = id;
        }

        protected Void doInBackground(Handler... h) {
            cam.startStream(h[0], id);
            while (!isCancelled()) {
                cam.getFrame();
            }
            return null;
        }
    }
}