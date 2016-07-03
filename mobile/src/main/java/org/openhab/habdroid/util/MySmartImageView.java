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
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;

import com.loopj.android.image.SmartImageView;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

public class MySmartImageView extends SmartImageView {

    public static final String TAG = MySmartImageView.class.getSimpleName();

    // Handler classes should be static or leaks might occur.
    private static class RefreshHandler extends Handler {

        private final WeakReference<MySmartImageView> viewWeakReference;

        RefreshHandler(MySmartImageView smartImageView) {
            viewWeakReference = new WeakReference<>(smartImageView);
        }

        public void handleMessage(Message msg) {
            MySmartImageView imageView = viewWeakReference.get();
            if (imageView != null) {
                Log.i(TAG, "Refreshing image at " + imageView.myImageUrl);
                imageView.setImage(new MyWebImage(imageView.myImageUrl, false, imageView.username, imageView.password));
            }
        }
    }

    private String myImageUrl;
    private String username;
    private String password;

    private Timer imageRefreshTimer;

    public MySmartImageView(Context context) {
        super(context);
    }

    public MySmartImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MySmartImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setImageUrl(String url, String username, String password) {
        this.myImageUrl = url;
        this.username = username;
        this.password = password;
        setImage(new MyWebImage(url, username, password));
    }

    public void setImageUrl(String url, final Integer fallbackResource, String username, String password) {
        this.myImageUrl = url;
        this.username = username;
        this.password = password;
        setImage(new MyWebImage(url, username, password), fallbackResource, null);
    }

    public void setImageUrl(String url, final Integer fallbackResource, final Integer loadingResource, String username, String password) {
        this.myImageUrl = url;
        this.username = username;
        this.password = password;
        setImage(new MyWebImage(url, username, password), fallbackResource, loadingResource);
    }

    public void setImageUrl(String url, boolean useImageCache, String username, String password) {
        this.myImageUrl = url;
        this.username = username;
        this.password = password;
        setImage(new MyWebImage(url, useImageCache, username, password));
    }

    public void setRefreshRate(int msec) {
        Log.i(TAG, "Setting image refresh rate to " + msec + " msec for " + myImageUrl);
        if (this.imageRefreshTimer != null) {
            this.imageRefreshTimer.cancel();
        }

        this.imageRefreshTimer = new Timer();
        final Handler timerHandler = new RefreshHandler(this);

        imageRefreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                timerHandler.sendEmptyMessage(0);
            }
        }, msec, msec);
    }

    public void cancelRefresh() {
        Log.i(TAG, "Cancel image Refresh for " + myImageUrl);
        if (this.imageRefreshTimer != null) {
            this.imageRefreshTimer.cancel();
        }
    }
}
