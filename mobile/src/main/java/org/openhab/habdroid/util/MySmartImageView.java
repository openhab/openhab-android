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
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;

import com.loopj.android.image.SmartImageView;

import java.util.Timer;
import java.util.TimerTask;

public class MySmartImageView extends SmartImageView {
	private String myImageUrl;
	private Timer imageRefreshTimer;
	
	boolean useImageCache = true;

	public MySmartImageView(Context context) {
		super(context);
	}
	
    public MySmartImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MySmartImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setImageUrl(String url) {
    	this.myImageUrl = url;
        setImage(new MyWebImage(url));
    }

    public void setImageUrl(String url, String username, String password) {
    	this.myImageUrl = url;
        setImage(new MyWebImage(url, username, password));
    }
    
    public void setImageUrl(String url, final Integer fallbackResource) {
    	this.myImageUrl = url;
        setImage(new MyWebImage(url), fallbackResource, null);
    }

    public void setImageUrl(String url, final Integer fallbackResource, String username, String password) {
    	this.myImageUrl = url;
        setImage(new MyWebImage(url, username, password), fallbackResource, null);
    }
    
    public void setImageUrl(String url, final Integer fallbackResource, final Integer loadingResource) {
    	this.myImageUrl = url;
        setImage(new MyWebImage(url), fallbackResource, loadingResource);
    }

    public void setImageUrl(String url, final Integer fallbackResource, final Integer loadingResource, String username, String password) {
    	this.myImageUrl = url;
        setImage(new MyWebImage(url, username, password), fallbackResource, loadingResource);
    }
    
    public void setImageUrl(String url, boolean useImageCache) {
    	this.myImageUrl = url;
    	this.useImageCache = useImageCache;
        setImage(new MyWebImage(url, useImageCache));
    }

    public void setImageUrl(String url, boolean useImageCache, String username, String password) {
    	this.myImageUrl = url;
    	this.useImageCache = useImageCache;
        setImage(new MyWebImage(url, useImageCache, username, password));
    }
    
    public void setRefreshRate(int msec) {
    	Log.i("MySmartImageView", "Setting image refresh rate to " + msec + " msec");
    	if (this.imageRefreshTimer != null)
    		this.imageRefreshTimer.cancel();
    	this.imageRefreshTimer = new Timer();
    	final Handler timerHandler = new Handler() {
    		public void handleMessage(Message msg) {
				Log.i("MySmartImageView", "Refreshing image at " + MySmartImageView.this.myImageUrl);
				MySmartImageView.this.setImage(new MyWebImage(MySmartImageView.this.myImageUrl, false));
    		}
    	};
    	imageRefreshTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				timerHandler.sendEmptyMessage(0);
			}
    	}, msec, msec);
    }
    
    public void cancelRefresh() {
    	if (this.imageRefreshTimer != null)
    		this.imageRefreshTimer.cancel();
    }

}
