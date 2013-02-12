/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2012, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */
package org.openhab.habdroid.util;

import java.util.Timer;
import java.util.TimerTask;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;

import com.loopj.android.image.SmartImageView;

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
        setImage(new MyWebImage(url), fallbackResource);
    }

    public void setImageUrl(String url, final Integer fallbackResource, String username, String password) {
    	this.myImageUrl = url;
        setImage(new MyWebImage(url, username, password), fallbackResource);
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
