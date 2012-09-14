package org.openhab.habdroid.util;

import android.content.Context;
import android.util.AttributeSet;

import com.loopj.android.image.SmartImageView;

public class MySmartImageView extends SmartImageView {


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
        setImage(new MyWebImage(url));
    }

    public void setImageUrl(String url, final Integer fallbackResource) {
        setImage(new MyWebImage(url), fallbackResource);
    }

    public void setImageUrl(String url, final Integer fallbackResource, final Integer loadingResource) {
        setImage(new MyWebImage(url), fallbackResource, loadingResource);
    }
    
}
