/*
 * Copyright (c) 2010-2018, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available
 * at https://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.WebView;

public class MaxHeightWebView extends WebView {
    static final int NO_MAX_HEIGHT = -1;
    int mMaxHeight = NO_MAX_HEIGHT;

    public MaxHeightWebView(Context context) {
        super(context);
    }

    public MaxHeightWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MaxHeightWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MaxHeightWebView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setMaxHeight(int maxHeight) {
        mMaxHeight = maxHeight;
    }

    /**
     * Make sure the webview isn't higher than mMaxHeight.
     *
     * @author https://stackoverflow.com/a/29178364
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mMaxHeight > 0) {
            int heightSize = MeasureSpec.getSize(heightMeasureSpec);
            int heightMode = MeasureSpec.getMode(heightMeasureSpec);

            if (heightMode == MeasureSpec.UNSPECIFIED) {
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(
                        mMaxHeight, MeasureSpec.AT_MOST);
            } else {
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(Math.min(heightSize, mMaxHeight),
                        heightMode);
            }
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
