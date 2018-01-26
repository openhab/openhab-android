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
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;

import com.loopj.android.image.SmartImageView;
import com.loopj.android.image.SmartImageTask;
import com.loopj.android.image.SmartImage;

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
            if (imageView != null && !imageView.refreshDisabled && imageView.myImageUrl != null) {
                Log.i(TAG, "Refreshing image at " + imageView.myImageUrl);
                imageView.refreshDisabled = true;
                imageView.setImage(new MyWebImage(imageView.myImageUrl, false, imageView.username, imageView.password), imageView.imageCompletionListener);
            }
        }
    }

    private static class OnCompleteListener extends SmartImageTask.OnCompleteListener {
        private final WeakReference<MySmartImageView> viewWeakReference;

        OnCompleteListener(MySmartImageView smartImageView) {
            viewWeakReference = new WeakReference<>(smartImageView);
        }

        public void onComplete(){
            MySmartImageView imageView = viewWeakReference.get();
            if (imageView != null) {
                imageView.refreshDisabled = false;
            }
        }
    }

    private String myImageUrl;
    private String username;
    private String password;
    private int maxWidth;
    private int maxHeight;
    private boolean refreshDisabled;

    private Timer imageRefreshTimer;
    private OnCompleteListener imageCompletionListener;

    public MySmartImageView(Context context) {
        super(context);
        this.maxWidth = -1;
        this.maxHeight = -1;
        this.imageCompletionListener = new OnCompleteListener(this);
    }

    public MySmartImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.maxWidth = -1;
        this.maxHeight = -1;
        this.imageCompletionListener = new OnCompleteListener(this);
    }

    public MySmartImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.maxWidth = -1;
        this.maxHeight = -1;
        this.imageCompletionListener = new OnCompleteListener(this);
    }

    public void setImageUrl(String url, String username, String password) {
        setImageUrl(url, username, password, true);
    }

    public void setImageUrl(String url, String username, String password, boolean useImageCache) {
        setImageUrl(url, username, password, useImageCache, null);
    }

    public void setImageUrl(String url, String username, String password, Integer fallbackResource) {
        setImageUrl(url, username, password, true, fallbackResource);
    }

    public void setImageUrl(String url, String username, String password,
            boolean useImageCache, Integer fallbackResource) {
        setImageUrl(url, username, password, useImageCache, fallbackResource, fallbackResource);
    }

    public void setImageUrl(String url, String username, String password,
            Integer fallbackResource, Integer loadingResource) {
        setImageUrl(url, username, password, true, fallbackResource, loadingResource);
    }

    private void setImageUrl(String url, String username, String password, boolean useImageCache,
            Integer fallbackResource, Integer loadingResource) {
        if (TextUtils.equals(myImageUrl, url)
                && TextUtils.equals(this.username, username)
                && TextUtils.equals(this.password, password)) {
            // nothing changed -> nothing to do
            return;
        }
        this.myImageUrl = url;
        this.username = username;
        this.password = password;
        this.refreshDisabled = true;

        MyWebImage image = new MyWebImage(url, useImageCache, username, password);
        Bitmap cachedBitmap = image.getCachedBitmap();
        if (cachedBitmap != null) {
            setImageBitmap(cachedBitmap);
        } else {
            setImage(image, fallbackResource, loadingResource, imageCompletionListener);
        }
    }

    public void setImageWithData(SmartImage image) {
        this.myImageUrl = null;
        this.username = null;
        this.password = null;
        this.refreshDisabled = true;
        setImage(image, imageCompletionListener);
    }

    public void setMaxSize(int maxWidth, int maxHeight) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
    }

    public void setRefreshRate(int msec) {
        Log.i(TAG, "Setting image refresh rate to " + msec + " msec for " + myImageUrl);

        cancelRefresh();

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
            this.refreshDisabled = false;
        }
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        if (maxWidth > 0 && maxHeight > 0) {
            float imageRatio = bm.getWidth()/bm.getHeight();
            if ((int) (maxWidth / imageRatio) > maxHeight) {
                ViewGroup.LayoutParams layoutParams = getLayoutParams();
                layoutParams.height = maxHeight;
                setLayoutParams(layoutParams);
            }
        }
        super.setImageBitmap(bm);
    }
}
