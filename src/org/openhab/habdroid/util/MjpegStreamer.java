/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *  @author Victor Belov
 *  @since 1.4.0
 *
 */

package org.openhab.habdroid.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class MjpegStreamer {

    private static final String TAG = "MjpegStreamer";
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
            e.printStackTrace();
        }
    }

    public InputStream httpRequest(String url, String usr, String pwd){
        HttpResponse res = null;
        DefaultHttpClient httpclient = new DefaultHttpClient();
        CredentialsProvider credProvider = new BasicCredentialsProvider();
        credProvider.setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
                new UsernamePasswordCredentials(usr, pwd));
        httpclient.setCredentialsProvider(credProvider);
        Log.d(TAG, "1. Sending http request");
        try {
            res = httpclient.execute(new HttpGet(URI.create(url)));
            Log.d(TAG, "2. Request finished, status = " + res.getStatusLine().getStatusCode());
            if(res.getStatusLine().getStatusCode()==401){
                //You must turn off camera User Access Control before this will work
                return null;
            }
            Log.d(TAG, "content-type = " + res.getEntity().getContentType());
            Log.d(TAG, "content-encoding = " + res.getEntity().getContentEncoding());
            return res.getEntity().getContent();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
            Log.d(TAG, "Request failed-ClientProtocolException", e);
            //Error connecting to camera
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "Request failed-IOException", e);
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