/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.Interpolator;

import java.lang.reflect.Field;

public class OpenHABViewPager extends ViewPager {

    // Disable page swiping by default
    private boolean mSwipeEnabled = false;
    // A custom scroller to lower the scroll speed a little bit
    private ScrollerCustomDuration mScroller = null;

    public OpenHABViewPager(Context context) {
        super(context);
        postInitViewPager();
    }

    public OpenHABViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        postInitViewPager();
    }

    /**
     * Override the Scroller instance with our own class so we can change the
     * duration
     */

    private void postInitViewPager() {
        try {
            Class<?> viewpager = ViewPager.class;
            Field scroller = viewpager.getDeclaredField("mScroller");
            scroller.setAccessible(true);
            Field interpolator = viewpager.getDeclaredField("sInterpolator");
            interpolator.setAccessible(true);

            mScroller = new ScrollerCustomDuration(getContext(),
                    (Interpolator) interpolator.get(null));
            scroller.set(this, mScroller);
        } catch (Exception e) {
        }
    }

    /**
     * Set the factor by which the duration will change
     */
    public void setScrollDurationFactor(double scrollFactor) {
        mScroller.setScrollDurationFactor(scrollFactor);
    }

    /*
        onTouchEvent and onInterceptTouchEvent are needed to prevent swiping
        sitemap pages (OpenHABWidgetListFragments) to keep navigation as usual
        (press item to drill down, press 'back' to get to previous page)
     */

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (this.mSwipeEnabled) {
            return super.onTouchEvent(event);
        }

        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (this.mSwipeEnabled) {
            return super.onInterceptTouchEvent(event);
        }

        return false;
    }

    /*
        For flexibility or future use you can enable swiping pages (disabled by default)
     */

    public void setSwipeEnabled(boolean swipe) {
        mSwipeEnabled = swipe;
    }
}
